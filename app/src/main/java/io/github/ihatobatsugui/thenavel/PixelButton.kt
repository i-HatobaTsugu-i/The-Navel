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
 * 空心线框按钮：线宽固定 1 个像素格（2dp），直角，中心透明。
 * 线框用四条实心矩形拼出来而不是 drawRect 描边——描边会落在半像素上并触发抗锯齿，
 * 在 1 格粗的线上会变成灰边或 3px 粗。
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

    var frameColor: Int = Color.WHITE
    var frameColorPressed: Int = Color.WHITE

    init {
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
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
        paint.color = if (isPressed) frameColorPressed else frameColor
        val w = width.toFloat()
        val h = height.toFloat()
        val t = unit.toFloat()
        canvas.drawRect(0f, 0f, w, t, paint)
        canvas.drawRect(0f, h - t, w, h, paint)
        canvas.drawRect(0f, 0f, t, h, paint)
        canvas.drawRect(w - t, 0f, w, h, paint)
    }

    companion object {
        private const val GRID_DP = 2f
    }
}
