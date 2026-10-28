package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * 通用像素图标按钮：按字符图案逐格绘制（1dp 一格），点击有轻微缩放。
 */
class PixelIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val unit = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)
    private val paint = Paint().apply { isAntiAlias = false }

    var pattern: Array<String> = emptyArray()
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }
    var palette: Map<Char, Int> = emptyMap()
        set(value) {
            field = value
            invalidate()
        }
    var onTap: (() -> Unit)? = null

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cols = pattern.maxOfOrNull { it.length } ?: 1
        val rows = pattern.size.coerceAtLeast(1)
        setMeasuredDimension(cols * unit, rows * unit)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (y in pattern.indices) {
            val row = pattern[y]
            for (x in row.indices) {
                val color = palette[row[x]] ?: continue
                paint.color = color
                canvas.drawRect(
                    (x * unit).toFloat(), (y * unit).toFloat(),
                    ((x + 1) * unit).toFloat(), ((y + 1) * unit).toFloat(), paint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(70).start()
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        onTap?.invoke()
        return super.performClick()
    }
}
