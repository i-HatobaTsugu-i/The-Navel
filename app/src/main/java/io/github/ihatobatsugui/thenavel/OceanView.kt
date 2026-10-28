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
 * 海面背景：蓝底 + 零散的白色碎沫，60Hz 刷新。
 * 泡沫由两层噪声算出的密度直接撒点（"一点一点"的碎沫），保留一帧淡出记忆，源移走后原地只变淡不重生。
 * 噪声在半分辨率网格上求值、逐格双线性采样，把每帧计算量降到 1/4，才跑得起 60fps。
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
    private var foam = FloatArray(0)
    private var patchGrid = FloatArray(0)
    private var texGrid = FloatArray(0)
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
        foam = FloatArray(cols * rows)
        gridCols = cols / GRID_STEP + 2
        gridRows = rows / GRID_STEP + 2
        patchGrid = FloatArray(gridCols * gridRows)
        texGrid = FloatArray(gridCols * gridRows)
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
        updateGrids(t)

        var index = 0
        for (j in 0 until rows) {
            val depth = j.toFloat() / max(rows - 1, 1)
            val water = lerpColor(SHALLOW, DEEP, depth.pow(0.8f))
            val gy = j.toFloat() / GRID_STEP
            val gy0 = gy.toInt()
            val fy = gy - gy0
            for (i in 0 until cols) {
                val gx = i.toFloat() / GRID_STEP
                val gx0 = gx.toInt()
                val fx = gx - gx0
                val patch = sampleGrid(patchGrid, gx0, gy0, fx, fy)
                val tex = sampleGrid(texGrid, gx0, gy0, fx, fy)

                val mask = smoothStep(0.48f, 0.68f, patch)
                val f = 0.55f * patch + 0.45f * tex
                val source = max(0f, (f - 0.54f) / 0.20f) * mask

                val previous = if (frameIndex == 0) {
                    if (hash01(i, j, 4242) < SEED_RATE) 0.9f else 0f
                } else {
                    foam[index]
                }
                val value = max(source, previous * DECAY)
                foam[index] = value

                val r = hash01(i, j, stamp)
                pixels[index++] = when {
                    r < value * 0.105f -> FOAM
                    r < value * 0.20f -> blend(water, FOAM_DIM, 0.60f)
                    else -> water
                }
            }
        }
        frameIndex++
    }

    // 噪声在半分辨率网格上求值
    private fun updateGrids(t: Float) {
        var gi = 0
        for (gj in 0 until gridRows) {
            val cy = gj * GRID_STEP
            for (gx in 0 until gridCols) {
                val cx = gx * GRID_STEP
                patchGrid[gi] = fbm(cx * 0.055f - t * 0.22f, cy * 0.045f + t * 0.05f, 1)
                texGrid[gi] = fbm(cx * 0.130f - t * 0.5f, cy * 0.120f + t * 0.18f, 72)
                gi++
            }
        }
    }

    private fun sampleGrid(grid: FloatArray, x0: Int, y0: Int, fx: Float, fy: Float): Float {
        val i00 = y0 * gridCols + x0
        val i10 = i00 + 1
        val i01 = i00 + gridCols
        val i11 = i01 + 1
        val top = grid[i00] + (grid[i10] - grid[i00]) * fx
        val bottom = grid[i01] + (grid[i11] - grid[i01]) * fx
        return top + (bottom - top) * fy
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
        private const val FRAME_MS = 16L
        private const val GRID_STEP = 2
        private const val OCTAVES = 2
        // 60fps 下按 ~0.55s 的时间常数换算出的每帧衰减
        private const val DECAY = 0.97f
        private const val SEED_RATE = 0.004f
        private val SHALLOW = Color.parseColor("#2F7396")
        private val DEEP = Color.parseColor("#0F2C40")
        private val FOAM = Color.parseColor("#E6F1F6")
        private val FOAM_DIM = Color.parseColor("#9DBECE")
    }
}
