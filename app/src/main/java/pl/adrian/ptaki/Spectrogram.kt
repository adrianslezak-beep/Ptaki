package pl.adrian.ptaki

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/** Kołowy bufor kolumn spektrogramu (kolory ARGB). Kolumna 0 = najniższa częstotliwość. */
class SpectrogramBuffer(val cols: Int, val rows: Int) {
    private val data = IntArray(cols * rows) { BG }

    /** Liczba wszystkich dopisanych kolumn (rośnie bez końca). */
    @Volatile var total = 0L
        private set

    @Synchronized
    fun push(column: IntArray) {
        val c = (total % cols).toInt()
        System.arraycopy(column, 0, data, c * rows, rows)
        total++
    }

    @Synchronized
    fun clear() {
        data.fill(BG)
        total = 0
    }

    /** Rozkłada bufor do obrazka wiersz po wierszu (góra = wysokie tony), najnowsze z prawej. */
    @Synchronized
    fun render(out: IntArray): Long {
        val t = total
        for (x in 0 until cols) {
            val src = t - cols + x
            if (src < 0) {
                for (y in 0 until rows) out[y * cols + x] = BG
            } else {
                val base = (src % cols).toInt() * rows
                for (y in 0 until rows) out[y * cols + x] = data[base + rows - 1 - y]
            }
        }
        return t
    }

    companion object {
        const val BG = 0xFF0B110D.toInt()
    }
}

/** Liczy kolumny spektrogramu z kolejnych porcji dźwięku. */
class SpectroMaker(private val sampleRate: Int, private val rows: Int, private val maxHz: Float) {
    private val n = 2048
    private val fft = Fft(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private val history = FloatArray(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val column = IntArray(rows)
    private val db = FloatArray(rows)
    private var floor = Float.NaN

    fun process(samples: FloatArray, count: Int, sink: (IntArray) -> Unit) {
        // przesuwamy historię i dopisujemy nowe próbki
        if (count >= n) {
            System.arraycopy(samples, count - n, history, 0, n)
        } else {
            System.arraycopy(history, count, history, 0, n - count)
            System.arraycopy(samples, 0, history, n - count, count)
        }
        for (i in 0 until n) {
            re[i] = history[i] * window[i]
            im[i] = 0f
        }
        fft.transform(re, im)

        val maxBin = ((maxHz / sampleRate) * n).toInt().coerceAtMost(n / 2 - 1)
        var sum = 0f
        for (r in 0 until rows) {
            val b0 = 1 + r * maxBin / rows
            val b1 = maxOf(b0 + 1, 1 + (r + 1) * maxBin / rows)
            var p = 0f
            for (b in b0 until b1) {
                val v = re[b] * re[b] + im[b] * im[b]
                if (v > p) p = v
            }
            val d = 10f * log10(p + 1e-9f)
            db[r] = d
            sum += d
        }
        val mean = sum / rows
        floor = if (floor.isNaN()) mean else floor * 0.97f + mean * 0.03f
        for (r in 0 until rows) {
            val v = ((db[r] - floor - 4f) / 38f).coerceIn(0f, 1f)
            column[r] = LUT[(v * 255).toInt()]
        }
        sink(column)
    }

    companion object {
        /** Mech → zieleń → mosiądz → kremowy. */
        val LUT: IntArray = run {
            val stops = listOf(
                0.00f to 0x0B110D, 0.25f to 0x173A30, 0.50f to 0x3E7A57,
                0.75f to 0xB9A85E, 1.00f to 0xF4E6BC
            )
            IntArray(256) { i ->
                val t = i / 255f
                val k = stops.indexOfLast { it.first <= t }.coerceAtMost(stops.size - 2)
                val (t0, c0) = stops[k]
                val (t1, c1) = stops[k + 1]
                val f = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
                fun ch(c: Int, s: Int) = (c shr s) and 0xFF
                fun mix(s: Int) = (ch(c0, s) + (ch(c1, s) - ch(c0, s)) * f).toInt()
                (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            }
        }
    }
}

/** Prosta FFT radix-2 (n = potęga dwójki). */
class Fft(private val n: Int) {
    private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { (-sin(2 * PI * it / n)).toFloat() }
    private val rev = IntArray(n)

    init {
        var bits = 0
        while ((1 shl bits) < n) bits++
        for (i in 0 until n) {
            var x = i
            var r = 0
            for (b in 0 until bits) {
                r = (r shl 1) or (x and 1)
                x = x shr 1
            }
            rev[i] = r
        }
    }

    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val l = j + half
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }
}
