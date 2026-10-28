package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 铁板按钮：金色细边框 + 45° 斜纹内板 + 四角螺丝。
 * 内板按"中心鼓起、四周压暗"的径向明暗叠左上镜面高光，量化成少量级差以保持像素风。
 * 整块底色在尺寸变化时渲染进 Bitmap 缓存，onDraw 只贴图。
 * 一格 = 1dp，所有边缘都落在整数像素上。
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

    // 金框：最外沿上/左亮、下/右暗，内圈用基色，读起来是一圈有厚度的金属条
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
        val base = if ((ci + cj) / STRIPE % 2 == 0) PLATE_LIGHT else PLATE_DARK

        val nx = (ci - (pc - 1) / 2.0) / max(pc / 2.0, 1.0)
        val ny = (cj - (pr - 1) / 2.0) / max(pr / 2.0, 1.0)
        val dist = min(1.0, sqrt(nx * nx + ny * ny) / sqrt(2.0))

        var factor = (1.16 - 0.30 * dist - 0.07 * ny).toFloat()
        // 左上方的镜面反射区
        val sx = nx + 0.55
        val sy = ny + 0.55
        if (sx * sx + sy * sy < 0.30) factor += 0.09f
        // 贴框内阴影：左上重、右下轻
        if (ci == 0 || cj == 0) factor -= 0.14f
        if (ci == pc - 1 || cj == pr - 1) factor -= 0.06f

        return shade(base, quantize(factor))
    }

    // 阶梯量化：像素风光靠少数几级明暗读出体积，平滑渐变反而糊
    private fun quantize(f: Float): Float = (f * 16f).roundToInt() / 16f

    private fun shade(rgb: Int, factor: Float): Int {
        val r = (Color.red(rgb) * factor).roundToInt().coerceIn(0, 255)
        val g = (Color.green(rgb) * factor).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(rgb) * factor).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

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
        private const val MIN_ROWS = 64
        private const val BORDER = 4
        private const val STRIPE = 8
        private const val RIVET = 8
        private const val PRESS_OVERLAY = 0x33000000

        private val GOLD_LIGHT = Color.parseColor("#F2CD78")
        private val GOLD = Color.parseColor("#D9A441")
        private val GOLD_DARK = Color.parseColor("#8C6420")
        private val PLATE_LIGHT = Color.parseColor("#9AA1AC")
        private val PLATE_DARK = Color.parseColor("#7C828E")

        private val RING = Color.parseColor("#4A4F58")
        private val HI = Color.parseColor("#D6DAE0")
        private val MID = Color.parseColor("#9AA0A9")
        private val DARK = Color.parseColor("#6F757E")

        // 8×8 格圆头螺丝：左上高光、右下压暗
        private val RIVET_PATTERN = arrayOf(
            intArrayOf(0, 0, RING, RING, RING, RING, 0, 0),
            intArrayOf(0, RING, HI, HI, HI, MID, RING, 0),
            intArrayOf(RING, HI, HI, HI, MID, MID, MID, RING),
            intArrayOf(RING, HI, HI, MID, MID, MID, DARK, RING),
            intArrayOf(RING, HI, MID, MID, MID, DARK, DARK, RING),
            intArrayOf(RING, MID, MID, MID, DARK, DARK, DARK, RING),
            intArrayOf(0, RING, MID, DARK, DARK, DARK, RING, 0),
            intArrayOf(0, 0, RING, RING, RING, RING, 0, 0)
        )
    }
}
