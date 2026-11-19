package pl.adrian.ptaki

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class WikiInfo(
    val title: String,
    val extract: String,
    val thumb: String?,
    val url: String,
    val lang: String,
) {
    /** Większa wersja miniatury do ekranu szczegółów. */
    val bigImage: String? get() = thumb?.replace(Regex("/\\d+px-"), "/800px-")

    fun toJson(): String = JSONObject()
        .put("title", title).put("extract", extract).put("thumb", thumb ?: "")
        .put("url", url).put("lang", lang).toString()

    companion object {
        fun fromJson(s: String): WikiInfo = JSONObject(s).let {
            WikiInfo(
                it.getString("title"), it.getString("extract"),
                it.optString("thumb").ifBlank { null }, it.getString("url"), it.getString("lang")
            )
        }
    }
}

/** Krótkie opisy gatunków z Wikipedii (najpierw polska, potem angielska), z pamięcią podręczną. */
object Wiki {
    private lateinit var dir: File
    private val mem = ConcurrentHashMap<String, WikiInfo>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "wiki").apply { mkdirs() }
    }

    fun cached(sci: String): WikiInfo? = mem[sci]

    fun pageUrl(sci: String): String =
        "https://pl.wikipedia.org/w/index.php?search=" + URLEncoder.encode(sci, "UTF-8")

    suspend fun get(sci: String, name: String): WikiInfo? = withContext(Dispatchers.IO) {
        mem[sci]?.let { return@withContext it }
        val f = File(dir, sci.replace(' ', '_').replace('/', '_') + ".json")
        if (f.exists()) {
            runCatching { WikiInfo.fromJson(f.readText()) }.getOrNull()?.let {
                mem[sci] = it
                return@withContext it
            }
        }
        if (sci in missing) return@withContext null
        try {
            val info = fetch("pl", sci)
                ?: fetch("pl", name)?.takeIf { looksLikeSpecies(it, sci) }
                ?: fetch("en", sci)
            if (info != null) {
                mem[sci] = info
                runCatching { f.writeText(info.toJson()) }
            } else {
                missing += sci
            }
            info
        } catch (e: IOException) {
            null   // brak internetu — spróbujemy następnym razem
        }
    }

    /** Przy szukaniu po polskiej nazwie upewniamy się, że to artykuł o zwierzęciu, a nie np. o wyspie Kos. */
    private fun looksLikeSpecies(info: WikiInfo, sci: String): Boolean {
        val t = info.extract.lowercase()
        return sci.lowercase() in t || listOf("gatunek", "ptak", "rodzin", "rzęd").any { it in t }
    }

    private fun fetch(lang: String, title: String): WikiInfo? {
        val enc = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        val req = Request.Builder()
            .url("https://$lang.wikipedia.org/api/rest_v1/page/summary/$enc")
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val j = JSONObject(resp.body?.string() ?: return null)
            if (j.optString("type") == "disambiguation") return null
            val extract = j.optString("extract")
            if (extract.isBlank()) return null
            val url = j.optJSONObject("content_urls")?.let {
                it.optJSONObject("mobile")?.optString("page")
                    ?: it.optJSONObject("desktop")?.optString("page")
            } ?: "https://$lang.wikipedia.org/wiki/$enc"
            return WikiInfo(
                title = j.optString("title", title),
                extract = extract,
                thumb = j.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null },
                url = url,
                lang = lang,
            )
        }
    }
}
