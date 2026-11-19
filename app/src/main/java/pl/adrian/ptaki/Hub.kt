package pl.adrian.ptaki

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class Detection(
    val sci: String,
    val name: String,
    val first: Long,
    val last: Long,
    val count: Int,
    val best: Float,
    val lastConf: Float,
)

data class Marker(val col: Long, val label: String)

sealed interface Status {
    data object Idle : Status
    data object Loading : Status
    data object Listening : Status
    data class Error(val msg: String) : Status
}

/** Wspólny stan między usługą nasłuchu a ekranem. */
object Hub {
    const val COLS_PER_SEC = 40
    const val MAX_HZ = 12_000f

    val status = MutableStateFlow<Status>(Status.Idle)
    val detections = MutableStateFlow<List<Detection>>(emptyList())
    val markers = MutableStateFlow<List<Marker>>(emptyList())
    val filterInfo = MutableStateFlow<String?>(null)
    val spectrogram = SpectrogramBuffer(cols = COLS_PER_SEC * 12, rows = 256)

    fun start(ctx: Context) {
        status.value = Status.Loading
        ContextCompat.startForegroundService(ctx, Intent(ctx, ListenService::class.java))
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, ListenService::class.java))
        status.value = Status.Idle
    }

    fun clear() {
        detections.value = emptyList()
        markers.value = emptyList()
    }

    /** Wywoływane z wątku modelu po każdym 3-sekundowym oknie. */
    fun report(hits: List<Pair<Label, Float>>) {
        val now = System.currentTimeMillis()
        val col = spectrogram.total - COLS_PER_SEC * 3 / 2   // środek analizowanego okna
        val map = detections.value.associateBy { it.sci }.toMutableMap()
        val newMarkers = ArrayList<Marker>()
        for ((lab, conf) in hits) {
            val pct = "${(conf * 100).toInt()}%"
            val old = map[lab.sci]
            if (old == null) {
                map[lab.sci] = Detection(lab.sci, lab.name, now, now, 1, conf, conf)
                newMarkers += Marker(col, "${lab.name} $pct")
            } else {
                val newOccurrence = now - old.last > 8_000
                map[lab.sci] = old.copy(
                    last = now,
                    count = old.count + if (newOccurrence) 1 else 0,
                    best = maxOf(old.best, conf),
                    lastConf = conf,
                )
                if (newOccurrence) newMarkers += Marker(col, "${lab.name} $pct")
            }
        }
        detections.value = map.values.sortedByDescending { it.last }
        if (newMarkers.isNotEmpty()) {
            val t = spectrogram.total
            markers.value = (markers.value + newMarkers).filter { t - it.col < spectrogram.cols }
        }
    }
}
