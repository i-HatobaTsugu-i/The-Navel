package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextView

/**
 * 自绘输入显示：底部是隐藏 EditText 在真正接收输入，这里只负责显示文字并在编辑时闪烁光标。
 */
class InputDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val caretPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#1F1F1F")
    }

    private var blinking = false
    private var caretOn = true

    /** 当前显示的是占位提示而不是真实内容 */
    var isPlaceholder = false

    private val blink = object : Runnable {
        override fun run() {
            caretOn = !caretOn
            invalidate()
            if (blinking) postDelayed(this, 480)
        }
    }

    fun setBlinking(on: Boolean) {
        if (blinking == on) return
        blinking = on
        caretOn = on
        removeCallbacks(blink)
        if (on) postDelayed(blink, 480)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (blinking && isPlaceholder) {
            // 编辑空字段时隐藏占位文字，只在起始位置画光标
            if (caretOn) drawCaret(canvas, paddingLeft.toFloat())
            return
        }
        super.onDraw(canvas)
        if (!blinking || !caretOn) return
        val textWidth = paint.measureText(text.toString())
        drawCaret(canvas, paddingLeft + textWidth + 2f)
    }

    private fun drawCaret(canvas: Canvas, x: Float) {
        val top = paddingTop + 2f
        val bottom = height - paddingBottom - 2f
        if (bottom <= top) return
        caretPaint.color = currentTextColor
        canvas.drawRect(x, top, x + 2f, bottom, caretPaint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
    }
}
