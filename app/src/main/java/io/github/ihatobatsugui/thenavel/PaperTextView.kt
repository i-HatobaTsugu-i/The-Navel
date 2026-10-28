package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextView

/**
 * 线条纸文本：横线跟随行高绘制，保证每一行字都压在一条线上；背景色是纸色。
 * 线画在 super.onDraw 之前，文字盖在线上。
 */
class PaperTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val rulePaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#C3D2E4")
        strokeWidth = 1f
    }
    private val marginPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#E0A8A8")
        strokeWidth = 1f
    }

    init {
        setBackgroundColor(Color.parseColor("#F4EFE2"))
        includeFontPadding = false
    }

    override fun onDraw(canvas: Canvas) {
        val lh = lineHeight
        if (lh > 0) {
            val marginX = paddingLeft / 2
            var y = paddingTop + lh
            while (y < height - paddingBottom) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), rulePaint)
                y += lh
            }
            canvas.drawLine(marginX.toFloat(), 0f, marginX.toFloat(), height.toFloat(), marginPaint)
        }
        super.onDraw(canvas)
    }
}
