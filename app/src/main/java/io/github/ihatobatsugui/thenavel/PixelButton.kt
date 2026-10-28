package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 铁板按钮：黄铜细边框 + 45° 拉丝斜纹内板 + 四角螺丝。
 * 斜纹交界留 2 格过渡、叠一层确定性噪点，避免平涂感；整体只做上亮下暗的方向光，不打球面高光。
 * 整块底色渲染进 Bitmap 缓存，onDraw 只贴图。一格 = 1dp。
 */
class PixelButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val unit = (resources.displayMetrics.density * GRID_DP).roundToInt().coerceAtLeast(1)
    private val paint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private var cache: Bitmap? = null

    init {
        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        setWillNotDraw(false)
        minimumHeight = MIN_ROWS * unit
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cache?.recycle()
        cache = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(
            alignToGrid(measuredWidth, MeasureSpec.getMode(widthMeasureSpec)),
            alignToGrid(measuredHeight, MeasureSpec.getMode(heightMeasureSpec))
        )
    }

    // 宽度随文本变化时取整到格，避免出现 1.x 格宽的边线
    private fun alignToGrid(value: Int, mode: Int): Int =
        if (mode == MeasureSpec.EXACTLY) value
        else ceil(value.toFloat() / unit).toInt() * unit

    override fun onDraw(canvas: Canvas) {
        val bg = cache ?: renderBackground().also { cache = it }
        canvas.drawBitmap(bg, 0f, 0f, bitmapPaint)
        if (isPressed) {
            paint.color = PRESS_OVERLAY
            val inset = (BORDER + 2) * unit
            canvas.drawRect(inset.toFloat(), inset.toFloat(),
                (width - inset).toFloat(), (height - inset).toFloat(), paint)
        }
        super.onDraw(canvas)
    }

    private fun renderBackground(): Bitmap {
        val u = unit
        val cols = width / u
        val rows = height / u
        if (cols <= 0 || rows <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val bmp = Bitmap.createBitmap(cols * u, rows * u, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val edge = i < BORDER || j < BORDER || i >= cols - BORDER || j >= rows - BORDER
                val color = if (edge) frameColor(i, j, cols, rows) else plateColor(i, j, cols, rows)
                fill(c, i * u, j * u, (i + 1) * u, (j + 1) * u, color)
            }
        }
        drawRivets(c, cols, rows)
        return bmp
    }

    // 框：最外沿上/左亮、下/右暗，内圈基色，读起来是一条有厚度的黄铜条
    private fun frameColor(i: Int, j: Int, cols: Int, rows: Int): Int = when {
        i == 0 || j == 0 -> GOLD_LIGHT
        i == cols - 1 || j == rows - 1 -> GOLD_DARK
        else -> GOLD
    }

    private fun plateColor(i: Int, j: Int, cols: Int, rows: Int): Int {
        val ci = i - BORDER
        val cj = j - BORDER
        val pc = cols - 2 * BORDER
        val pr = rows - 2 * BORDER

        // 斜纹：交界处用 2 格过渡，避免硬边平涂
        val band = (ci + cj) % (2 * STRIPE)
        val lightBand = band < STRIPE
        val edgeDist = if (lightBand) min(band, STRIPE - band)
        else min(band - STRIPE, 2 * STRIPE - band)
        val mix = min(1.0, edgeDist / 2.0).toFloat()
        val base = if (lightBand) blend(PLATE_DARK, PLATE_LIGHT, mix)
        else blend(PLATE_LIGHT, PLATE_DARK, mix)

        // 方向光：整体上亮下暗，左侧略亮；上部叠一条柔和高光
        val ny = cj.toFloat() / maxOf(pr - 1, 1)
        val nx = ci.toFloat() / maxOf(pc - 1, 1)
        // 每条斜纹自身的亮度微差，避免整片均匀
        val stripeIdx = (ci + cj) / STRIPE
        val variance = (((stripeIdx.toLong() * 2654435761L) % 1000 + 1000) % 1000) / 1000f - 0.5f
        var factor = 1.05f - 0.13f * ny - 0.02f * nx + noise(ci, cj) + variance * 0.03f
        val sheen = maxOf(0f, 1f - abs(ny - 0.22f) / 0.35f)
        factor += 0.035f * sheen
        // 贴框内阴影：左上重、右下轻
        if (ci == 0 || cj == 0) factor -= 0.10f
        if (ci == pc - 1 || cj == pr - 1) factor -= 0.04f

        return shade(base, quantize(factor))
    }

    // 确定性噪点，打破大面积同色，读起来像金属表面的细微不匀
    private fun noise(i: Int, j: Int): Float {
        val v = (i * 73856093) xor (j * 19349663)
        val m = ((v % 7) + 7) % 7
        return (m - 3) * 0.006f
    }

    private fun quantize(f: Float): Float = (f * 64f).roundToInt() / 64f

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        lerp(Color.red(from), Color.red(to), t),
        lerp(Color.green(from), Color.green(to), t),
        lerp(Color.blue(from), Color.blue(to), t)
    )

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    private fun shade(rgb: Int, factor: Float): Int = Color.rgb(
        (Color.red(rgb) * factor).roundToInt().coerceIn(0, 255),
        (Color.green(rgb) * factor).roundToInt().coerceIn(0, 255),
        (Color.blue(rgb) * factor).roundToInt().coerceIn(0, 255)
    )

    private fun drawRivets(c: Canvas, cols: Int, rows: Int) {
        val margin = BORDER + 4
        val offsets = listOf(
            margin to margin,
            cols - margin - RIVET to margin,
            margin to rows - margin - RIVET,
            cols - margin - RIVET to rows - margin - RIVET
        )
        for ((ox, oy) in offsets) {
            for (y in 0 until RIVET) {
                for (x in 0 until RIVET) {
                    val color = RIVET_PATTERN[y][x]
                    if (color != Color.TRANSPARENT) {
                        fill(c, (ox + x) * unit, (oy + y) * unit,
                            (ox + x + 1) * unit, (oy + y + 1) * unit, color)
                    }
                }
            }
        }
    }

    private fun fill(c: Canvas, l: Int, t: Int, r: Int, b: Int, color: Int) {
        paint.color = color
        c.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

    companion object {
        private const val GRID_DP = 1f
        private const val MIN_ROWS = 40
        private const val BORDER = 4
        private const val STRIPE = 8
        private const val RIVET = 6
        private const val PRESS_OVERLAY = 0x33000000

        private val GOLD_LIGHT = Color.parseColor("#C8A96B")
        private val GOLD = Color.parseColor("#B08A45")
        private val GOLD_DARK = Color.parseColor("#6F5528")
        private val PLATE_LIGHT = Color.parseColor("#8F959D")
        private val PLATE_DARK = Color.parseColor("#80868E")

        private val RING = Color.parseColor("#4C5158")
        private val HI = Color.parseColor("#A9AFB7")
        private val MID = Color.parseColor("#868C94")
        private val DARK = Color.parseColor("#666C74")

        // 6×6 格螺丝：左上高光、右下压暗
        private val RIVET_PATTERN = arrayOf(
            intArrayOf(0, RING, RING, RING, RING, 0),
            intArrayOf(RING, HI, HI, MID, MID, RING),
            intArrayOf(RING, HI, MID, MID, DARK, RING),
            intArrayOf(RING, HI, MID, MID, DARK, RING),
            intArrayOf(RING, MID, MID, DARK, DARK, RING),
            intArrayOf(0, RING, RING, RING, RING, 0)
        )
    }
}
