package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 立体标题：沿右下方向堆若干层，越远越暗，再画主色。
 * 不做四周描边（1 格描边会把相邻字母连成一团），也不依赖系统合成粗体。
 */
class PixelTitle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val unit = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)

    var depth: Int = 4
    var extrusionNear: Int = Color.parseColor("#7A6238")
    var extrusionFar: Int = Color.parseColor("#2A2114")

    override fun onDraw(canvas: Canvas) {
        val p = paint
        val mainColor = p.color

        for (i in depth downTo 1) {
            val t = i.toFloat() / depth
            p.color = blend(extrusionNear, extrusionFar, t)
            canvas.save()
            canvas.translate((i * unit).toFloat(), (i * unit).toFloat())
            super.onDraw(canvas)
            canvas.restore()
        }

        p.color = mainColor
        super.onDraw(canvas)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt().coerceIn(0, 255),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt().coerceIn(0, 255),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt().coerceIn(0, 255)
    )
}
