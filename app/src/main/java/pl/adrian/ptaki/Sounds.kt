package pl.adrian.ptaki

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class RefSound(val file: File, val title: String, val page: String)

/** Wzorcowe nagrania głosów z Wikimedia Commons (głównie z xeno-canto), zapisywane w telefonie. */
object Sounds {
    private val mem = ConcurrentHashMap<String, RefSound>()
    private val none = ConcurrentHashMap.newKeySet<String>()
    private val okMime = setOf(
        "audio/ogg", "application/ogg", "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
        "audio/flac", "audio/x-flac"
    )

    suspend fun get(ctx: Context, sci: String): RefSound? = withContext(Dispatchers.IO) {
        mem[sci]?.let { return@withContext it }
        if (sci in none) return@withContext null
        val dir = File(ctx.filesDir, "wzorce").apply { mkdirs() }
        val safe = sci.replace(Regex("[^A-Za-z0-9]"), "_")
        val meta = File(dir, "$safe.json")
        if (meta.exists()) {
            runCatching {
                val j = JSONObject(meta.readText())
                val f = File(dir, j.getString("file"))
                if (f.exists()) RefSound(f, j.getString("title"), j.getString("page")) else null
            }.getOrNull()?.let { mem[sci] = it; return@withContext it }
        }
        try {
            val q = URLEncoder.encode("filetype:audio \"$sci\"", "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrnamespace=6&gsrlimit=15&gsrsearch=$q" +
                "&prop=imageinfo&iiprop=url%7Cmime%7Csize"
            val body = http.newCall(Request.Builder().url(url).build()).execute().use {
                if (!it.isSuccessful) return@withContext null
                it.body?.string()
            } ?: return@withContext null
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
            val needle = sci.lowercase()
            data class Cand(val index: Int, val title: String, val url: String, val page: String, val ext: String)
            val cands = ArrayList<Cand>()
            pages?.keys()?.forEach { k ->
                val p = pages.getJSONObject(k)
                val title = p.optString("title")
                val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@forEach
                val mime = ii.optString("mime")
                val size = ii.optLong("size")
                if (mime !in okMime || size > 15_000_000) return@forEach
                if (!title.lowercase().replace('_', ' ').contains(needle)) return@forEach
                val fileUrl = ii.optString("url")
                val ext = fileUrl.substringAfterLast('.', "ogg").lowercase().take(5)
                cands += Cand(p.optInt("index", 999), title, fileUrl, ii.optString("descriptionurl", fileUrl), ext)
            }
            val best = cands.minByOrNull { it.index }
            if (best == null) {
                none += sci
                return@withContext null
            }
            val f = File(dir, "$safe.${best.ext}")
            http.newCall(Request.Builder().url(best.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.byteStream()?.use { input -> f.outputStream().use { input.copyTo(it) } }
                    ?: return@withContext null
            }
            val r = RefSound(f, best.title.removePrefix("File:"), best.page)
            meta.writeText(JSONObject().put("file", f.name).put("title", r.title).put("page", r.page).toString())
            mem[sci] = r
            r
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}

/** Prosty odtwarzacz: jedno nagranie naraz. [playing] = klucz aktualnie granego. */
object Player {
    val playing = MutableStateFlow<String?>(null)
    private var mp: MediaPlayer? = null

    fun toggle(key: String, file: File) {
        if (playing.value == key) {
            stop()
            return
        }
        stop()
        try {
            mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); true }
                prepare()
                start()
            }
            playing.value = key
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        mp?.let {
            runCatching { it.stop() }
            it.release()
        }
        mp = null
        playing.value = null
    }
}
