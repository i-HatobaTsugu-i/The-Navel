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
import kotlin.math.roundToInt

/**
 * 海面背景：单色水面 + 成团的白色泡沫。
 * 泡沫是一张浓度场：每帧沿风向平流（双线性采样）、带扩散、按时间常数衰减，
 * 浓度由缓慢变化的噪声区域持续补充。因此泡沫团会边移动边被拉散、形状每刻都不同。
 * 抖动阈值固定在每个格子上、不随时间重掷，所以高刷新率下也不会闪。
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
    private var dither = FloatArray(0)
    private var curlX = FloatArray(0)
    private var curlY = FloatArray(0)
    private var convGrid = FloatArray(0)
    private var sourceBuf = FloatArray(0)
    private var gridCols = 0
    private var gridRows = 0
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
        dither = FloatArray(cols * rows) { hash01(it % cols, it / cols, 7) }
        curlX = FloatArray(cols * rows) { (hash01(it % cols, it / cols, 21) - 0.5f) * 2f }
        curlY = FloatArray(cols * rows) { (hash01(it % cols, it / cols, 43) - 0.5f) * 2f }
        sourceBuf = FloatArray(cols * rows)
        gridCols = cols / GRID_STEP + 2
        gridRows = rows / GRID_STEP + 2
        convGrid = FloatArray(gridCols * gridRows)
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
        if (frameIndex % GRID_REFRESH == 0) updateConv(t)

        if (frameIndex == 0) {
            for (idx in foamA.indices) foamA[idx] = sourceBuf[idx] * 120f
        } else {
            var idx = 0
            for (j in 0 until rows) {
                for (i in 0 until cols) {
                    val advected = sampleBilinear(
                        foamA,
                        i - (WIND_X + curlX[idx] * CURL),
                        j - (WIND_Y + curlY[idx] * CURL)
                    )
                    val left = foamA[j * cols + ((i - 1 + cols) % cols)]
                    val right = foamA[j * cols + (i + 1) % cols]
                    val up = foamA[((j - 1 + rows) % rows) * cols + i]
                    val down = foamA[((j + 1) % rows) * cols + i]
                    val average = (left + right + up + down) * 0.25f
                    val diffused = advected + (average - advected) * DIFFUSE
                    var value = (diffused * DECAY + sourceBuf[idx]).coerceIn(0f, 1f)
                    // 对比度拉伸：高浓度堆得更实、低浓度更稀，形成成团的分布
                    if (value > 0.30f) value = (value + (value - 0.30f) * CONTRAST).coerceAtMost(1f)
                    foamB[idx] = value
                    idx++
                }
            }
            val swap = foamA
            foamA = foamB
            foamB = swap
        }

        var index = 0
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val value = foamA[index] * 1.35f
                val d = dither[index]
                pixels[index++] = when {
                    d < value * 0.55f -> FOAM
                    d < value * 0.95f -> blend(WATER, FOAM_DIM, 0.65f)
                    else -> WATER
                }
            }
        }
        frameIndex++
    }

    // 粗网格噪声 → 细网格源场，每 GRID_REFRESH 帧算一次
    private fun updateConv(t: Float) {
        var gi = 0
        for (gj in 0 until gridRows) {
            val cy = gj * GRID_STEP
            for (gx in 0 until gridCols) {
                val cx = gx * GRID_STEP
                convGrid[gi++] = fbm(cx * 0.045f - t * 0.10f, cy * 0.045f + t * 0.02f, 9)
            }
        }
        var idx = 0
        for (j in 0 until rows) {
            val gy = (j.toFloat() / GRID_STEP).coerceIn(0f, (gridRows - 2).toFloat())
            val y0 = gy.toInt()
            val fy = gy - y0
            for (i in 0 until cols) {
                val gx = (i.toFloat() / GRID_STEP).coerceIn(0f, (gridCols - 2).toFloat())
                val x0 = gx.toInt()
                val fx = gx - x0
                val base = y0 * gridCols + x0
                val top = convGrid[base] + (convGrid[base + 1] - convGrid[base]) * fx
                val bottom = convGrid[base + gridCols] +
                    (convGrid[base + gridCols + 1] - convGrid[base + gridCols]) * fx
                val value = top + (bottom - top) * fy
                sourceBuf[idx++] = max(0f, value - 0.64f) * SOURCE_GAIN
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

    companion object {
        private const val FRAME_MS = 16L
        private const val GRID_STEP = 4
        private const val GRID_REFRESH = 10
        private const val OCTAVES = 2
        private const val WIND_X = 0.96f
        private const val WIND_Y = 0.18f
        private const val CURL = 0.55f
        private const val DIFFUSE = 0.07f
        private const val DECAY = 0.988f
        private const val CONTRAST = 0.55f
        private const val SOURCE_GAIN = 0.010f
        private val WATER = Color.parseColor("#1E5473")
        private val FOAM = Color.parseColor("#E6F1F6")
        private val FOAM_DIM = Color.parseColor("#9DBECE")
    }
}
