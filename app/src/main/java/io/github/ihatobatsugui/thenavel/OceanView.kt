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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 海面背景：深蓝水面 + 稀疏的白色碎沫团。
 * 泡沫用两套不同尺度/流速的值噪声 fBm 合成——没有正弦叠加的周期性，
 * 再按密度用混淆哈希撒点，边缘才是碎的而不是整块白。
 * 整片按 2dp 一格渲染到位图后最近邻放大，像素颗粒清晰；8fps 足够表现海浪。
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cols = max(1, w / cellPx)
        rows = max(1, h / cellPx)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        pixels = IntArray(cols * rows)
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
        val stamp = (t * 5).toInt()
        var index = 0
        for (j in 0 until rows) {
            val depth = j.toFloat() / max(rows - 1, 1)
            val base = lerpColor(SHALLOW, DEEP, depth.pow(0.7f))
            val py = j * 0.045f + t * 0.05f
            val ty = j * 0.120f + t * 0.18f
            for (i in 0 until cols) {
                val patch = fbm(i * 0.055f - t * 0.22f, py, 1)
                val tex = fbm(i * 0.130f - t * 0.5f, ty, 72)

                val shade = 0.84f + 0.30f * patch
                val water = shaded(base, shade)

                val mask = smoothStep(0.48f, 0.68f, patch)
                val f = 0.55f * patch + 0.45f * tex
                val density = max(0f, (f - 0.54f) / 0.20f) * mask

                val r = hash01(i, j, stamp)
                pixels[index++] = when {
                    r < density * 0.42f -> FOAM
                    r < density * 0.80f -> blend(water, FOAM_DIM, 0.6f)
                    else -> water
                }
            }
        }
    }

    private fun hash01(ix: Int, iy: Int, s: Int): Float {
        var v = (ix * 73856093) xor (iy * 19349663) xor (s * 83492791)
        v = v xor (v ushr 16)
        v *= 0x7FEB352D
        v = v xor (v ushr 15)
        v *= 0x846CA68B.toInt()
        v = v xor (v ushr 16)
        return (v and 0xFFFF) / 65535f
    }

    private fun noise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val u = fx * fx * (3f - 2f * fx)
        val v = fy * fy * (3f - 2f * fy)
        val n00 = hash01(x0, y0, seed)
        val n10 = hash01(x0 + 1, y0, seed)
        val n01 = hash01(x0, y0 + 1, seed)
        val n11 = hash01(x0 + 1, y0 + 1, seed)
        val a = n00 + (n10 - n00) * u
        val b = n01 + (n11 - n01) * u
        return a + (b - a) * v
    }

    private fun fbm(x: Float, y: Float, seed: Int): Float {
        var total = 0f
        var amp = 1f
        var freq = 1f
        var norm = 0f
        for (o in 0 until OCTAVES) {
            total += amp * noise(x * freq, y * freq, seed + o * 31)
            norm += amp
            amp *= 0.5f
            freq *= 2.03f
        }
        return total / norm
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
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

    companion object {
        private const val FRAME_MS = 125L
        private const val OCTAVES = 2
        private val DEEP = Color.parseColor("#081A29")
        private val SHALLOW = Color.parseColor("#17435C")
        private val FOAM = Color.parseColor("#D2E2EA")
        private val FOAM_DIM = Color.parseColor("#86A4B4")
    }
}
