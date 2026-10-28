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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 海面背景：蓝底 + 白色泡沫簇。
 * 泡沫不是一个每帧刷新的场，而是若干个有"寿命"的簇：出生 → 逐渐变浓 → 淡出消失，泡沫只是簇浓度的表现。
 * 因此整片泡沫会自然生灭、位置漂移；颜色按浓度与水面混色，过渡连续（不是开关式闪烁）。
 * 每帧只遍历各簇的包围盒，渲染 2dp 一格后最近邻放大，20fps。
 */
class OceanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Cluster {
        var x = 0f
        var y = 0f
        var radius = 0f
        var age = 0f
        var life = 1f
        var vx = 0f
        var vy = 0f
        var seed = 0
    }

    private val cellPx = (2f * resources.displayMetrics.density).roundToInt().coerceAtLeast(2)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val dst = Rect()

    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var alpha = FloatArray(0)
    private var detail = FloatArray(0)
    private var water = IntArray(0)
    private val clusters = ArrayList<Cluster>()
    private var spawnCount = 0

    private var cols = 0
    private var rows = 0
    private var lastFrameAt = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cols = max(1, w / cellPx)
        rows = max(1, h / cellPx)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        pixels = IntArray(cols * rows)
        alpha = FloatArray(cols * rows)
        detail = FloatArray(cols * rows)
        water = IntArray(cols * rows)

        var index = 0
        for (j in 0 until rows) {
            val depth = j.toFloat() / max(rows - 1, 1)
            val base = lerpColor(SHALLOW, DEEP, depth.pow(0.8f))
            for (i in 0 until cols) {
                detail[index] = fbm(i * 0.16f, j * 0.16f, 21)
                water[index] = base
                index++
            }
        }

        clusters.clear()
        spawnCount = 0
        repeat(CLUSTER_COUNT) { spawnCluster(it * 0.9f) }
        lastFrameAt = 0L
    }

    private fun spawnCluster(ageOffset: Float) {
        val s = spawnCount++
        val c = Cluster()
        c.x = hash01(s, 7, 11) * cols
        c.y = hash01(s, 13, 17) * rows
        c.radius = 14f + hash01(s, 19, 23) * 10f
        c.life = 4.5f + hash01(s, 29, 31) * 3.5f
        c.age = (hash01(s, 37, 41) * c.life + ageOffset) % c.life
        c.vx = (hash01(s, 43, 47) - 0.5f) * 4f
        c.vy = (hash01(s, 53, 59) - 0.5f) * 1.2f
        c.seed = s
        clusters.add(c)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameAt == 0L) 0f else (now - lastFrameAt) / 1000f
        if (lastFrameAt == 0L || now - lastFrameAt >= FRAME_MS) {
            lastFrameAt = now
            render(dt.coerceIn(0f, 0.2f))
            bitmap?.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        }
        bitmap?.let {
            dst.set(0, 0, width, height)
            canvas.drawBitmap(it, null, dst, paint)
        }
        postInvalidateOnAnimation()
    }

    private fun render(dt: Float) {
        java.util.Arrays.fill(alpha, 0f)

        for (c in clusters) {
            c.age += dt
            if (c.age >= c.life) {
                // 这一簇“寿终”，在原地附近重新投生一个新的
                val diedX = c.x
                val diedY = c.y
                val fresh = Cluster()
                val s = spawnCount++
                fresh.x = diedX + (hash01(s, 61, 67) - 0.5f) * 30f
                fresh.y = diedY + (hash01(s, 71, 73) - 0.5f) * 20f
                fresh.radius = 14f + hash01(s, 19, 23) * 10f
                fresh.life = 4.5f + hash01(s, 29, 31) * 3.5f
                fresh.age = 0f
                fresh.vx = (hash01(s, 43, 47) - 0.5f) * 4f
                fresh.vy = (hash01(s, 53, 59) - 0.5f) * 1.2f
                fresh.seed = s
                c.x = fresh.x
                c.y = fresh.y
                c.radius = fresh.radius
                c.life = fresh.life
                c.age = 0f
                c.vx = fresh.vx
                c.vy = fresh.vy
                c.seed = fresh.seed
            }

            c.x += c.vx * dt
            c.y += c.vy * dt
            if (c.x < 0f) c.x += cols
            if (c.x >= cols) c.x -= cols
            c.y = c.y.coerceIn(0f, (rows - 1).toFloat())

            val t = c.age / c.life
            val env = smoothStep(0f, 0.22f, t) * (1f - smoothStep(0.62f, 1f, t))
            if (env <= 0.01f) continue

            val rx = c.radius * 1.45f
            val ry = c.radius * 0.85f
            val j0 = max(0, (c.y - ry).toInt())
            val j1 = min(rows - 1, (c.y + ry).toInt() + 1)
            for (j in j0..j1) {
                val dy = (j - c.y) / ry
                val base = j * cols
                val i0 = max(0, (c.x - rx).toInt())
                val i1 = min(cols - 1, (c.x + rx).toInt() + 1)
                for (i in i0..i1) {
                    val dx = (i - c.x) / rx
                    val idx = base + i
                    val shape = 1f - dx * dx - dy * dy + 0.55f * (detail[idx] - 0.5f)
                    if (shape > 0f) {
                        val v = env * shape.pow(1.1f)
                        if (v > alpha[idx]) alpha[idx] = v
                    }
                }
            }
        }

        var index = 0
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val a = (alpha[index] * 8f).roundToInt() / 8f
                pixels[index] = if (a <= 0f) {
                    water[index]
                } else {
                    val p = smoothStep(0.44f, 0.72f, detail[index] * 0.85f + a * 0.40f)
                    blend(water[index], FOAM, min(1f, p * a * 1.6f))
                }
                index++
            }
        }
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
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
        private const val FRAME_MS = 50L
        private const val OCTAVES = 2
        private const val CLUSTER_COUNT = 5
        private val SHALLOW = Color.parseColor("#2F7396")
        private val DEEP = Color.parseColor("#0F2C40")
        private val FOAM = Color.parseColor("#E6F1F6")
    }
}
