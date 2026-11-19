package pl.adrian.ptaki

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs

/** Jedna obserwacja = ptak słyszany bez przerwy dłuższej niż 8 s. */
data class Obs(
    val id: Long,
    val sci: String,
    val name: String,
    val start: Long,
    val end: Long,
    val best: Float,
    val lat: Double?,
    val lon: Double?,
    val clip: String?,     // nazwa pliku WAV w katalogu nagrań (najpewniejszy fragment)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("sci", sci).put("name", name).put("start", start).put("end", end)
        .put("best", best.toDouble())
        .apply {
            if (lat != null && lon != null) { put("lat", lat); put("lon", lon) }
            if (clip != null) put("clip", clip)
        }

    companion object {
        fun fromJson(j: JSONObject) = Obs(
            id = j.getLong("id"),
            sci = j.getString("sci"),
            name = j.getString("name"),
            start = j.getLong("start"),
            end = j.getLong("end"),
            best = j.getDouble("best").toFloat(),
            lat = if (j.has("lat")) j.getDouble("lat") else null,
            lon = if (j.has("lon")) j.getDouble("lon") else null,
            clip = j.optString("clip").ifBlank { null },
        )
    }
}

/** Trwały dziennik obserwacji (plik JSON) + krótkie nagrania WAV. */
object Journal {
    const val GAP_MS = 8_000L

    private lateinit var file: File
    lateinit var clipDir: File
        private set
    private val io = Executors.newSingleThreadExecutor()

    /** Wszystkie obserwacje, najnowsze pierwsze. */
    val items = MutableStateFlow<List<Obs>>(emptyList())

    /** Ustawiane przez usługę, jeśli znamy prawdziwą lokalizację. */
    @Volatile var location: Pair<Double, Double>? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "dziennik.json")
        clipDir = File(ctx.filesDir, "nagrania").apply { mkdirs() }
        items.value = runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { Obs.fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.start }
        }.getOrDefault(emptyList())
    }

    fun clipFile(o: Obs): File? = o.clip?.let { File(clipDir, it) }?.takeIf { it.exists() }

    /** Wołane z wątku modelu. [audio] = okno 3 s, 48 kHz. */
    @Synchronized
    fun record(lab: Label, conf: Float, now: Long, audio: FloatArray?) {
        val list = items.value
        val open = list.firstOrNull { it.sci == lab.sci && now - it.end <= GAP_MS }
        val withClip = Prefs.saveClips && audio != null
        if (open == null) {
            var id = now
            while (list.any { it.id == id }) id++
            val clip = if (withClip) saveClip(id, audio!!) else null
            val loc = location
            items.value = listOf(Obs(id, lab.sci, lab.name, now, now, conf, loc?.first, loc?.second, clip)) + list
        } else {
            var o = open.copy(end = now, best = maxOf(open.best, conf))
            if (withClip && (conf > open.best || open.clip == null)) {
                o = o.copy(clip = saveClip(open.id, audio!!))
            }
            items.value = list.map { if (it.id == open.id) o else it }
        }
        persist()
    }

    @Synchronized
    fun delete(id: Long) {
        val o = items.value.firstOrNull { it.id == id } ?: return
        clipFile(o)?.delete()
        items.value = items.value.filter { it.id != id }
        persist()
    }

    @Synchronized
    fun deleteAllClips() {
        clipDir.listFiles()?.forEach { it.delete() }
        items.value = items.value.map { it.copy(clip = null) }
        persist()
    }

    fun clipsBytes(): Long = clipDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun persist() {
        val snapshot = items.value
        io.execute {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { arr.put(it.toJson()) }
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(file)
            }
        }
    }

    /** Zapis 3 s jako WAV 24 kHz mono 16 bit, z wyrównaniem głośności. */
    private fun saveClip(id: Long, audio48k: FloatArray): String? = runCatching {
        val n = audio48k.size / 2
        val out = FloatArray(n) { (audio48k[2 * it] + audio48k[2 * it + 1]) * 0.5f }
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        val gain = if (peak > 1e-5f) minOf(0.9f / peak, 20f) else 1f
        val name = "$id.wav"
        writeWav(File(clipDir, name), out, gain, 24_000)
        name
    }.getOrNull()

    private fun writeWav(f: File, data: FloatArray, gain: Float, rate: Int) {
        val dataBytes = data.size * 2
        DataOutputStream(FileOutputStream(f).buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF); o.write((v shr 16) and 0xFF); o.write((v shr 24) and 0xFF) }
            fun le16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
            o.writeBytes("RIFF"); le32(36 + dataBytes); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(dataBytes)
            for (v in data) le16((v * gain * 32767f).toInt().coerceIn(-32768, 32767))
        }
    }
}
