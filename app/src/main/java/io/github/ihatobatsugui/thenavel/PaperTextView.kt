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
        color = Color.parseColor("#B4C6DE")
        strokeWidth = context.resources.displayMetrics.density.coerceAtLeast(1f)
    }
    private val marginPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#D98F8F")
        strokeWidth = context.resources.displayMetrics.density.coerceAtLeast(1f)
    }

    init {
        setBackgroundColor(Color.parseColor("#F4EFE2"))
        includeFontPadding = false
    }

    override fun onDraw(canvas: Canvas) {
        val lh = lineHeight
        if (lh > 0) {
            val half = rulePaint.strokeWidth / 2f
            val marginX = paddingLeft / 2f
            var y = paddingTop + lh
            while (y < height - paddingBottom) {
                // 对齐到整像素中心，避免 1px 线被拆到两行而变淡
                val lineY = (y - half).toInt() + half
                canvas.drawLine(0f, lineY, width.toFloat(), lineY, rulePaint)
                y += lh
            }
            canvas.drawLine(marginX, 0f, marginX, height.toFloat(), marginPaint)
        }
        super.onDraw(canvas)
    }
}
