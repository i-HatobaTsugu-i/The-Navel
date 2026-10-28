package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 海面背景：蓝底 + 白色碎沫。整片按 2dp 一格渲染到位图再最近邻放大，因此像素颗粒清晰。
 * 三个方向的涌浪叠加出等高线状的泡沫骨架，再按密度用哈希撒点，得到碎沫而不是整块白。
 */
class OceanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cellPx = (2f * resources.displayMetrics.density).roundToInt().coerceAtLeast(2)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val dst = Rect()

    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var cols = 0
    private var rows = 0
    private var startAt = 0L
    private var lastFrameAt = 0L

    private var colA = FloatArray(0)
    private var colB = FloatArray(0)
    private var colC = FloatArray(0)
    private var colM = FloatArray(0)
    private var rowA = FloatArray(0)
    private var rowB = FloatArray(0)
    private var rowC = FloatArray(0)
    private var rowM = FloatArray(0)
    private var caA = FloatArray(0)
    private var caB = FloatArray(0)
    private var caC = FloatArray(0)
    private var caM = FloatArray(0)
    private var raA = FloatArray(0)
    private var raB = FloatArray(0)
    private var raC = FloatArray(0)
    private var raM = FloatArray(0)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cols = max(1, w / cellPx)
        rows = max(1, h / cellPx)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        pixels = IntArray(cols * rows)
        colA = FloatArray(cols); caA = FloatArray(cols)
        colB = FloatArray(cols); caB = FloatArray(cols)
        colC = FloatArray(cols); caC = FloatArray(cols)
        colM = FloatArray(cols); caM = FloatArray(cols)
        rowA = FloatArray(rows); raA = FloatArray(rows)
        rowB = FloatArray(rows); raB = FloatArray(rows)
        rowC = FloatArray(rows); raC = FloatArray(rows)
        rowM = FloatArray(rows); raM = FloatArray(rows)
        startAt = SystemClock.uptimeMillis()
        lastFrameAt = 0L
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (lastFrameAt == 0L || now - lastFrameAt >= FRAME_MS) {
            lastFrameAt = now
            render((now - startAt) / 1000f)
            bitmap?.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        }
        bitmap?.let {
            dst.set(0, 0, width, height)
            canvas.drawBitmap(it, null, dst, paint)
        }
        postInvalidateOnAnimation()
    }

    private fun render(t: Float) {
        val stamp = (t * 6).toInt()
        // 预计算每一列/每一行的 sin、cos，逐格只做乘加
        prepare(colA, caA, 0.080f, 0.90f * t)
        prepare(rowA, raA, 0.030f, 0f)
        prepare(colB, caB, -0.050f, -0.70f * t + 1.7f)
        prepare(rowB, raB, 0.110f, 0f)
        prepare(colC, caC, 0.170f, 1.70f * t + 3.1f)
        prepare(rowC, raC, -0.090f, 0f)
        prepare(colM, caM, 0.021f, 0.25f * t + 1.0f)
        prepare(rowM, raM, 0.028f, 0f)

        var index = 0
        for (j in 0 until rows) {
            val depth = j.toFloat() / max(rows - 1, 1)
            val base = lerpColor(SHALLOW, DEEP, depth.pow(0.75f))
            val rA = rowA[j]; val rB = rowB[j]; val rC = rowC[j]; val rM = rowM[j]
            val rAa = raA[j]; val rBa = raB[j]; val rCa = raC[j]; val rMa = raM[j]
            for (i in 0 until cols) {
                val a = colA[i] * rA + caA[i] * rAa
                val b = colB[i] * rB + caB[i] * rBa
                val c = colC[i] * rC + caC[i] * rCa
                val m = colM[i] * rM + caM[i] * rMa

                val s = a + 0.55f * b + 0.30f * c
                val g = abs(s)
                val shine = 1f + 0.13f * s
                val density = max(0f, (0.38f - g) / 0.38f) * (0.15f + 0.85f * (0.5f + 0.5f * m))

                val r = hash01(i, j, stamp)
                pixels[index++] = when {
                    r < density * 0.55f -> FOAM
                    r < density * 1.15f -> blend(shaded(base, shine), FOAM_DIM, 0.7f)
                    else -> shaded(base, shine)
                }
            }
        }
    }

    private fun prepare(sinArr: FloatArray, cosArr: FloatArray, k: Float, phase: Float) {
        for (i in sinArr.indices) {
            val v = k * i + phase
            sinArr[i] = sin(v)
            cosArr[i] = kotlin.math.cos(v)
        }
    }

    private fun shaded(base: Int, factor: Float): Int = Color.rgb(
        (Color.red(base) * factor).roundToInt().coerceIn(0, 255),
        (Color.green(base) * factor).roundToInt().coerceIn(0, 255),
        (Color.blue(base) * factor).roundToInt().coerceIn(0, 255)
    )

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt().coerceIn(0, 255),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt().coerceIn(0, 255),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt().coerceIn(0, 255)
    )

    private fun lerpColor(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt().coerceIn(0, 255),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt().coerceIn(0, 255),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt().coerceIn(0, 255)
    )

    private fun Float.pow(e: Float): Float = Math.pow(toDouble(), e.toDouble()).toFloat()

    private fun hash01(i: Int, j: Int, s: Int): Float {
        var v = (i * 73856093) xor (j * 19349663) xor (s * 83492791)
        v = (v xor (v ushr 13)) * 1274126177
        return (v and 0xFFFF) / 65535f
    }

    companion object {
        private const val FRAME_MS = 100L
        private val DEEP = Color.parseColor("#071E30")
        private val SHALLOW = Color.parseColor("#1A5778")
        private val FOAM = Color.parseColor("#ECF5F8")
        private val FOAM_DIM = Color.parseColor("#9FBFCF")
    }
}
