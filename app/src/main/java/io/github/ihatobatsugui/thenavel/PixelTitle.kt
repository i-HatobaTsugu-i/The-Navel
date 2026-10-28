package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 立体标题：1 格暗色描边 + 若干层 1 格步进的深色偏移，再画主色。
 * 不用系统合成粗体（Unifont 笔画会被撑满、字黏在一起），厚度全部由偏移层提供。
 */
class PixelTitle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val unit = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)

    var depth: Int = 3
    var extrusionColor: Int = Color.parseColor("#5A4527")
    var outlineColor: Int = Color.parseColor("#241C10")

    override fun onDraw(canvas: Canvas) {
        val p = paint
        val mainColor = p.color

        p.color = outlineColor
        val dirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dx, dy) in dirs) {
            canvas.save()
            canvas.translate((dx * unit).toFloat(), (dy * unit).toFloat())
            super.onDraw(canvas)
            canvas.restore()
        }

        p.color = extrusionColor
        for (i in depth downTo 1) {
            canvas.save()
            canvas.translate((i * unit).toFloat(), (i * unit).toFloat())
            super.onDraw(canvas)
            canvas.restore()
        }

        p.color = mainColor
        super.onDraw(canvas)
    }
}
