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
 * 海面背景：蓝底 + 白色碎沫。
 * 泡沫有生命周期：开局只播一次种子，之后靠"聚集场"生长、每帧衰减、随流漂移（双线性采样，运动平滑）。
 * 整片按 2dp 一格渲染到位图后最近邻放大，像素颗粒清晰；15fps。
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
    private var foamA = FloatArray(0)
    private var foamB = FloatArray(0)
    private var convBuf = FloatArray(0)
    private var detailBuf = FloatArray(0)
    private var frameIndex = 0
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
        foamA = FloatArray(cols * rows)
        foamB = FloatArray(cols * rows)
        convBuf = FloatArray(cols * rows)
        detailBuf = FloatArray(cols * rows)
        frameIndex = 0
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
        if (frameIndex % 3 == 0) updateFields(t)

        if (frameIndex == 0) {
            // 开局只在这里撒一次种子，之后泡沫全部由聚集生长产生
            var idx = 0
            for (j in 0 until rows) {
                for (i in 0 until cols) {
                    foamA[idx++] = if (hash01(i, j, 4242) < SEED_RATE) 0.9f else 0f
                }
            }
        } else {
            var idx = 0
            for (j in 0 until rows) {
                for (i in 0 until cols) {
                    val prev = sampleBilinear(foamA, i + DRIFT_X, j + DRIFT_Y)
                    val grow = max(0f, (convBuf[idx] - 0.60f) / 0.40f) *
                        (0.25f + 1.5f * detailBuf[idx]) * GROW
                    foamB[idx] = (prev * DECAY + grow).coerceIn(0f, 1f)
                    idx++
                }
            }
            val swap = foamA
            foamA = foamB
            foamB = swap
        }

        var index = 0
        for (j in 0 until rows) {
            val depth = j.toFloat() / max(rows - 1, 1)
            val water = lerpColor(SHALLOW, DEEP, depth.pow(0.8f))
            for (i in 0 until cols) {
                val value = foamA[index]
                val r = hash01(i, j, stamp)
                pixels[index++] = when {
                    r < value * 0.75f -> FOAM
                    r < value * 1.20f -> blend(water, FOAM_DIM, 0.65f)
                    else -> water
                }
            }
        }
        frameIndex++
    }

    // 聚集场变化慢，隔 3 帧重算一次即可
    private fun updateFields(t: Float) {
        var index = 0
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                convBuf[index] = fbm(i * 0.035f - t * 0.25f, j * 0.035f + t * 0.05f, 9)
                detailBuf[index] = fbm(i * 0.14f + t * 0.03f, j * 0.14f, 21)
                index++
            }
        }
    }

    private fun sampleBilinear(source: FloatArray, fx: Float, fy: Float): Float {
        var x = fx % cols
        if (x < 0f) x += cols
        val y = fy.coerceIn(0f, (rows - 1).toFloat())
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = if (x0 + 1 >= cols) 0 else x0 + 1
        val y1 = if (y0 + 1 >= rows) rows - 1 else y0 + 1
        val tx = x - x0
        val ty = y - y0
        val top = source[y0 * cols + x0] + (source[y0 * cols + x1] - source[y0 * cols + x0]) * tx
        val bottom = source[y1 * cols + x0] + (source[y1 * cols + x1] - source[y1 * cols + x0]) * tx
        return top + (bottom - top) * ty
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
        private const val FRAME_MS = 66L
        private const val OCTAVES = 2
        private const val DECAY = 0.90f
        private const val GROW = 0.035f
        private const val SEED_RATE = 0.005f
        private const val DRIFT_X = 0.35f
        private const val DRIFT_Y = 0.12f
        private val SHALLOW = Color.parseColor("#2F7396")
        private val DEEP = Color.parseColor("#0F2C40")
        private val FOAM = Color.parseColor("#E6F1F6")
        private val FOAM_DIM = Color.parseColor("#9DBECE")
    }
}
