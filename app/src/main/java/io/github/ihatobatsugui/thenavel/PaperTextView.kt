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
            // 按行数画到底：包打最后一行，不能因 padding 而漏掉它下面的线
            val available = height - paddingTop - paddingBottom
            val lines = maxOf(1, Math.round(available / lh.toFloat()))
            for (k in 1..lines) {
                val y = paddingTop + k * lh
                if (y > height) break
                val lineY = (y - half).toInt() + half
                canvas.drawLine(0f, lineY, width.toFloat(), lineY, rulePaint)
            }
            canvas.drawLine(marginX, 0f, marginX, height.toFloat(), marginPaint)
        }
        super.onDraw(canvas)
    }
}
