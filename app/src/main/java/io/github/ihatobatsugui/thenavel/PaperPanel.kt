package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * 线条纸面板：米色纸面，上下边缘是 1dp 步进的锯齿（像素阶梯，不做抗锯齿）。
 * 只画纸本身，横线由内部的 PaperTextView 按行高绘制，保证每行字压在线上。
 */
class PaperPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val unit = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)

    private val paperPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#F4EFE2")
    }
    private val edgePaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#D9D1BC")
    }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val u = unit.toFloat()
        val w = width.toFloat()
        val h = height.toFloat()
        val tooth = 6 * u

        canvas.drawRect(0f, tooth, w, h - tooth, paperPaint)

        val cols = (width / unit)
        for (i in 0 until cols) {
            val x = i * u
            // 锯齿：16 格一个周期，1 格步进的三角阶梯（最高 7 格）
            val phase = i % 16
            val tri = if (phase < 8) phase else 15 - phase
            val toothH = tri * u
            if (toothH > 0f) {
                canvas.drawRect(x, tooth - toothH, x + u, tooth, paperPaint)
                canvas.drawRect(x, h - tooth, x + u, h - tooth + toothH, paperPaint)
            }
        }
        // 锯齿与纸面交界处压一条浅影，避免纸像漂浮
        canvas.drawRect(0f, tooth, w, tooth + u, edgePaint)
        canvas.drawRect(0f, h - tooth - u, w, h - tooth, edgePaint)
    }
}
