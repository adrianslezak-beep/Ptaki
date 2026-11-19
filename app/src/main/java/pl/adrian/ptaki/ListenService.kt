package pl.adrian.ptaki

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** Usługa pierwszoplanowa: słucha mikrofonu, rysuje spektrogram i rozpoznaje ptaki. */
class ListenService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotified = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Hub.status.value = Status.Idle
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground("Przygotowuję nasłuch…")
        if (job == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ptaki:nasluch")
                .apply { acquire(6 * 60 * 60 * 1000L) }
            job = scope.launch {
                try {
                    listen()
                } catch (e: Throwable) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Hub.status.value = Status.Error("Błąd: ${e.message ?: e.javaClass.simpleName}")
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        if (Hub.status.value !is Status.Error) Hub.status.value = Status.Idle
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun listen() {
        Hub.status.value = Status.Loading
        val model = BirdNet.get(this)

        // Filtr gatunków wg miejsca i pory roku
        val allowed: BooleanArray? = if (Prefs.locationFilter) {
            val loc = lastLocation()
            Journal.location = loc?.let { it.latitude to it.longitude }
            val (lat, lon, approx) = if (loc != null) Triple(loc.latitude, loc.longitude, false)
            else Triple(52.1, 19.4, true)
            Hub.filterInfo.value = if (approx) "Filtr gatunków: Polska (brak lokalizacji)"
            else String.format(Locale.US, "Filtr gatunków: okolice %.1f°N %.1f°E", lat, lon)
            model.allowedAt(lat, lon)
        } else {
            Journal.location = lastLocation()?.let { it.latitude to it.longitude }
            Hub.filterInfo.value = "Filtr gatunków wyłączony — cały świat"
            null
        }

        val rec = openRecorder() ?: run {
            Hub.status.value = Status.Error("Nie mogę otworzyć mikrofonu")
            stopSelf()
            return
        }
        val sr = rec.sampleRate
        try {
            rec.startRecording()
            Hub.status.value = Status.Listening
            updateNotification("Nasłuchuję…")

            val windowLen = sr * 3
            val ring = FloatArray(windowLen)
            var ringPos = 0
            var filled = 0
            var sinceInference = 0
            val hop = sr / Hub.COLS_PER_SEC
            val shorts = ShortArray(hop)
            val floats = FloatArray(hop)
            val spectro = SpectroMaker(sr, Hub.spectrogram.rows, Hub.MAX_HZ)

            val queue = Channel<FloatArray>(Channel.CONFLATED)
            scope.launch(model.dispatcher) {
                for (buf in queue) {
                    val input = if (sr == 48_000) buf else resample(buf, model.inputLen)
                    val scores = model.predict(input)
                    handle(scores, model, allowed, input)
                }
            }

            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < hop) {
                    val r = rec.read(shorts, read, hop - read)
                    if (r <= 0) throw IllegalStateException("mikrofon przestał odpowiadać ($r)")
                    read += r
                }
                for (i in 0 until hop) {
                    val v = shorts[i] / 32768f
                    floats[i] = v
                    ring[ringPos] = v
                    ringPos = (ringPos + 1) % windowLen
                }
                filled = minOf(windowLen, filled + hop)
                spectro.process(floats, hop) { Hub.spectrogram.push(it) }

                sinceInference += hop
                if (filled >= windowLen && sinceInference >= sr * 3 / 2) {
                    sinceInference = 0
                    val snap = FloatArray(windowLen)
                    val tail = windowLen - ringPos
                    System.arraycopy(ring, ringPos, snap, 0, tail)
                    System.arraycopy(ring, 0, snap, tail, ringPos)
                    queue.trySend(snap)
                }
            }
            queue.close()
        } finally {
            try { rec.stop() } catch (_: Throwable) {}
            rec.release()
        }
    }

    private fun handle(scores: FloatArray, model: BirdNet, allowed: BooleanArray?, audio: FloatArray) {
        val thr = Prefs.threshold
        val hits = ArrayList<Pair<Int, Float>>()
        for (i in scores.indices) {
            if (scores[i] >= thr && !model.excluded[i] && (allowed == null || allowed[i])) {
                hits += i to scores[i]
            }
        }
        if (hits.isEmpty()) return
        hits.sortByDescending { it.second }
        val top = hits.take(3).map { model.labels[it.first] to it.second }
        Hub.report(top, audio)
        val text = "Słychać: " + top.joinToString { it.first.name }
        if (text != lastNotified) {
            lastNotified = text
            updateNotification(text)
        }
    }

    private fun resample(src: FloatArray, outLen: Int): FloatArray {
        val out = FloatArray(outLen)
        val ratio = (src.size - 1).toDouble() / (outLen - 1)
        for (i in 0 until outLen) {
            val p = i * ratio
            val a = p.toInt()
            val b = minOf(a + 1, src.size - 1)
            val f = (p - a).toFloat()
            out[i] = src[a] * (1 - f) + src[b] * f
        }
        return out
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        // Surowy mikrofon (bez odszumiania) jest lepszy dla ptaków
        val sources = if (unprocessed) listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
        else listOf(MediaRecorder.AudioSource.MIC)
        for (rate in listOf(48_000, 44_100)) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            for (src in sources) {
                try {
                    val r = AudioRecord(src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
                    if (r.state == AudioRecord.STATE_INITIALIZED) return r
                    r.release()
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Location? {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.getProviders(true)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        } catch (_: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------

    private fun notification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Nasłuch ptaków", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bird)
            .setContentTitle("Ptaki — nasłuch")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Zatrzymaj", stop)
            .build()
    }

    private fun goForeground(text: String) {
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, notification(text))
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val ACTION_STOP = "pl.adrian.ptaki.STOP"
        private const val CHANNEL = "nasluch"
        private const val NOTIF_ID = 7
    }
}
