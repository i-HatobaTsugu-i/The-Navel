package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 立体标题：把文字按 1 格步进重复绘制若干层暗色，再画主色，
 * 得到像素风的厚度感（不用模糊阴影，避免糊边）。
 */
class PixelTitle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val unit = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)

    var depth: Int = 4
    var extrusionColor: Int = Color.parseColor("#6E5A33")

    override fun onDraw(canvas: Canvas) {
        val p = paint
        val mainColor = p.color
        for (i in depth downTo 1) {
            canvas.save()
            canvas.translate((i * unit).toFloat(), (i * unit).toFloat())
            p.color = extrusionColor
            super.onDraw(canvas)
            canvas.restore()
        }
        p.color = mainColor
        super.onDraw(canvas)
    }
}
