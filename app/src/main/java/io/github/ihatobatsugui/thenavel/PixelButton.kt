package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 铁材质空心线框按钮：3 格铁框斜面 + 1 格金线 + 同灰色内板（顶/左高光、底/右阴影做凸起）。
 * 全部用整数矩形填充：描边会落在半像素上并触发抗锯齿，1 格粗的线会变 3px 或发灰。
 * 单位为 2dp 一格，所有边缘都落在格线上。
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

    var ironLight: Int = Color.parseColor("#9AA1AC")
    var ironMid: Int = Color.parseColor("#6E7480")
    var ironDark: Int = Color.parseColor("#454A54")
    var gold: Int = Color.parseColor("#D9A441")

    init {
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
        minimumHeight = MIN_ROWS * unit
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
        super.onDraw(canvas)
        val u = unit
        val w = width
        val h = height

        // 铁外框 3 格：上/左外缘亮、下/右外缘暗，模拟左上打光
        fill(canvas, 0, 0, w, u, ironLight)
        fill(canvas, 0, u, w, 2 * u, ironMid)
        fill(canvas, 0, 2 * u, w, 3 * u, ironDark)
        fill(canvas, 0, h - u, w, h, ironDark)
        fill(canvas, 0, h - 2 * u, w, h - u, ironMid)
        fill(canvas, 0, h - 3 * u, w, h - 2 * u, ironLight)
        fill(canvas, 0, 3 * u, u, h - 3 * u, ironLight)
        fill(canvas, u, 3 * u, 2 * u, h - 3 * u, ironMid)
        fill(canvas, 2 * u, 3 * u, 3 * u, h - 3 * u, ironDark)
        fill(canvas, w - u, 3 * u, w, h - 3 * u, ironDark)
        fill(canvas, w - 2 * u, 3 * u, w - u, h - 3 * u, ironMid)
        fill(canvas, w - 3 * u, 3 * u, w - 2 * u, h - 3 * u, ironLight)

        // 金线 1 格
        fill(canvas, 3 * u, 3 * u, w - 3 * u, 4 * u, gold)
        fill(canvas, 3 * u, h - 4 * u, w - 3 * u, h - 3 * u, gold)
        fill(canvas, 3 * u, 4 * u, 4 * u, h - 4 * u, gold)
        fill(canvas, w - 4 * u, 4 * u, w - 3 * u, h - 4 * u, gold)

        // 内板：同铁灰，顶/左 1 格高光、底/右 1 格阴影，做出中心凸起
        fill(canvas, 4 * u, 4 * u, w - 4 * u, h - 4 * u, ironMid)
        fill(canvas, 4 * u, 4 * u, w - 4 * u, 5 * u, ironLight)
        fill(canvas, 4 * u, 5 * u, 5 * u, h - 5 * u, ironLight)
        fill(canvas, 4 * u, h - 5 * u, w - 4 * u, h - 4 * u, ironDark)
        fill(canvas, w - 5 * u, 5 * u, w - 4 * u, h - 5 * u, ironDark)

        // 四角铆钉：2×2 格，右下 1 格压暗
        rivet(canvas, u, u)
        rivet(canvas, w - 3 * u, u)
        rivet(canvas, u, h - 3 * u)
        rivet(canvas, w - 3 * u, h - 3 * u)
    }

    private fun rivet(canvas: Canvas, left: Int, top: Int) {
        fill(canvas, left, top, left + 2 * unit, top + 2 * unit, ironLight)
        fill(canvas, left + unit, top + unit, left + 2 * unit, top + 2 * unit, ironDark)
    }

    private fun fill(canvas: Canvas, l: Int, t: Int, r: Int, b: Int, color: Int) {
        paint.color = color
        canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

    companion object {
        private const val GRID_DP = 2f
        private const val MIN_ROWS = 32
    }
}
