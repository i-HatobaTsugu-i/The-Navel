package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * 像素风红色垃圾桶按钮：1dp 一格画出来的图元，点按有轻微缩放。
 */
class TrashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val unit = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)
    private val paint = Paint().apply { isAntiAlias = false }

    var onTap: (() -> Unit)? = null

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val cols = PATTERN[0].length
        val rows = PATTERN.size
        setMeasuredDimension(cols * unit, rows * unit)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (y in PATTERN.indices) {
            val row = PATTERN[y]
            for (x in row.indices) {
                val color = when (row[x]) {
                    'L' -> LID
                    'D' -> DARK
                    'R' -> BODY
                    else -> 0
                }
                if (color != 0) {
                    paint.color = color
                    canvas.drawRect(
                        (x * unit).toFloat(), (y * unit).toFloat(),
                        ((x + 1) * unit).toFloat(), ((y + 1) * unit).toFloat(), paint
                    )
                }
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

    companion object {
        private val BODY = Color.parseColor("#C0392B")
        private val LID = Color.parseColor("#E74C3C")
        private val DARK = Color.parseColor("#7B241C")

        private val PATTERN = arrayOf(
            "   LLLL   ",
            "  LLLLLL  ",
            " DDDDDDDD ",
            "  RRRRRR  ",
            "  R DR DR ",
            "  R DR DR ",
            "  R DR DR ",
            "  R DR DR ",
            "  R DR DR ",
            "  R DR DR ",
            "  RRRRRR  ",
            "   DDDD   "
        )
    }
}
