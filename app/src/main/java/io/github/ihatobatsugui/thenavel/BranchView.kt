package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * 图层隶属的直角连接线：从顶部竖直下来，再横出一小段指向右侧的子图层。
 */
class BranchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val unit = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)
    private val paint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#9DB7D8")
        strokeWidth = unit.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = (3 * unit).toFloat()
        val midY = height / 2f
        // 竖线
        canvas.drawRect(x, 0f, x + unit, midY, paint)
        // 横线
        canvas.drawRect(x, midY, width.toFloat(), midY + unit, paint)
    }
}
