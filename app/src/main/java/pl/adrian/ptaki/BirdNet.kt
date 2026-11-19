package pl.adrian.ptaki

import android.content.Context
import kotlinx.coroutines.asCoroutineDispatcher
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.exp

data class Label(val sci: String, val name: String)

/**
 * Model BirdNET V2.4 (Cornell Lab of Ornithology & TU Chemnitz, CC BY-NC-SA 4.0).
 * Wejście: 3 s dźwięku mono 48 kHz (144 000 próbek, -1..1). Wyjście: 6522 klasy.
 * Model "meta" podpowiada, które gatunki występują w danym miejscu i tygodniu roku.
 */
class BirdNet private constructor(ctx: Context) {

    val labels: List<Label>
    val excluded: BooleanArray
    val inputLen: Int
    private val classes: Int
    private val interp: Interpreter
    private val meta: Interpreter

    /** Interpreter TFLite nie jest wielowątkowy — wszystko liczymy na jednym wątku. */
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    init {
        labels = ctx.assets.open("labels_pl.txt").bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val i = line.indexOf('_')
                if (i < 0) Label(line.trim(), line.trim())
                else Label(line.substring(0, i).trim(), line.substring(i + 1).trim().replaceFirstChar { it.uppercase() })
            }
        excluded = BooleanArray(labels.size) { labels[it].sci in NOT_BIRDS }

        interp = Interpreter(loadAsset(ctx, "birdnet.tflite"), Interpreter.Options().setNumThreads(4))
        meta = Interpreter(loadAsset(ctx, "meta.tflite"), Interpreter.Options().setNumThreads(2))
        val inShape = interp.getInputTensor(0).shape()
        inputLen = inShape.last()
        classes = minOf(interp.getOutputTensor(0).shape().last(), labels.size)
    }

    /** Zwraca pewność 0..1 dla każdej klasy. */
    fun predict(samples: FloatArray): FloatArray {
        val input = if (samples.size == inputLen) samples else samples.copyOf(inputLen)
        val out = arrayOf(FloatArray(interp.getOutputTensor(0).shape().last()))
        interp.run(arrayOf(input), out)
        val logits = out[0]
        return FloatArray(classes) { i ->
            val x = logits[i].coerceIn(-15f, 15f)
            (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
        }
    }

    /** Gatunki prawdopodobne w danym miejscu i tygodniu (null = bez filtra). */
    fun allowedAt(lat: Double, lon: Double, cal: Calendar = Calendar.getInstance()): BooleanArray {
        val month = cal.get(Calendar.MONTH)            // 0..11
        val day = cal.get(Calendar.DAY_OF_MONTH)       // 1..31
        val week = (month * 4 + minOf(4, (day - 1) / 7 + 1)).toFloat()  // 1..48 jak w BirdNET
        val n = meta.getOutputTensor(0).shape().last()
        val out = arrayOf(FloatArray(n))
        meta.run(arrayOf(floatArrayOf(lat.toFloat(), lon.toFloat(), week)), out)
        return BooleanArray(classes) { i -> i < n && out[0][i] >= META_THRESHOLD }
    }

    companion object {
        private const val META_THRESHOLD = 0.03f
        private val NOT_BIRDS = setOf(
            "Dog", "Engine", "Environmental", "Fireworks", "Gun", "Noise", "Siren",
            "Human non-vocal", "Human vocal", "Human whistle", "Power tools"
        )

        @Volatile private var instance: BirdNet? = null

        fun get(ctx: Context): BirdNet =
            instance ?: synchronized(this) {
                instance ?: BirdNet(ctx.applicationContext).also { instance = it }
            }

        private fun loadAsset(ctx: Context, name: String): ByteBuffer {
            val bytes = ctx.assets.open(name).use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
    }
}
