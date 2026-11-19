package pl.adrian.ptaki

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

const val USER_AGENT = "Ptaki/0.1 (prywatna aplikacja Android do rozpoznawania ptakow)"

/** Wspólny klient HTTP — Wikimedia wymaga sensownego User-Agenta. */
val http: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()
}

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Wiki.init(this)
        Journal.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { http }
            .crossfade(true)
            .build()
}

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("ptaki", Context.MODE_PRIVATE)
    }

    /** Minimalna pewność (0..1), od której ptak trafia na listę. */
    var threshold: Float
        get() = sp.getFloat("threshold", 0.5f)
        set(v) = sp.edit().putFloat("threshold", v).apply()

    /** Czy odrzucać gatunki niewystępujące w okolicy o tej porze roku. */
    var locationFilter: Boolean
        get() = sp.getBoolean("locFilter", true)
        set(v) = sp.edit().putBoolean("locFilter", v).apply()

    /** Czy zapisywać 3-sekundowe nagranie każdej obserwacji. */
    var saveClips: Boolean
        get() = sp.getBoolean("saveClips", true)
        set(v) = sp.edit().putBoolean("saveClips", v).apply()
}
