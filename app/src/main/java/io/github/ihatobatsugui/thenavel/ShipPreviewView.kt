package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * 船只俯视示意图：船体画成船壳轮廓，炮塔按数量排在船体中线，装饰物落在两侧。
 * 目前图层没有坐标信息，所以只是按图层类型和数量做的示意排布。
 */
class ShipPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var layers: List<ShipLayer> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val hullFill = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
        color = Color.parseColor("#33414D")
    }
    private val hullStroke = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        color = Color.parseColor("#C9D2DA")
    }
    private val turretPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#9AA1AA")
    }
    private val decorPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#B08A45")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val path = Path()
        path.moveTo(w * 0.05f, h * 0.5f)
        path.lineTo(w * 0.30f, h * 0.16f)
        path.lineTo(w * 0.75f, h * 0.16f)
        path.lineTo(w * 0.95f, h * 0.5f)
        path.lineTo(w * 0.75f, h * 0.84f)
        path.lineTo(w * 0.30f, h * 0.84f)
        path.close()
        canvas.drawPath(path, hullFill)
        canvas.drawPath(path, hullStroke)

        val turrets = layers.count { it.type == LayerType.TURRET }
        val size = minOf(h * 0.16f, w * 0.06f)
        for (k in 0 until turrets) {
            val t = if (turrets == 1) 0.5f else 0.62f - k * (0.30f / (turrets - 1))
            canvas.drawRect(w * t - size / 2, h * 0.5f - size / 2,
                w * t + size / 2, h * 0.5f + size / 2, turretPaint)
        }

        val decors = layers.count { it.type == LayerType.DECOR }
        for (k in 0 until decors) {
            val side = if (k % 2 == 0) 0.30f else 0.70f
            val x = w * side
            val y = h * (0.36f + 0.16f * (k / 2))
            canvas.drawRect(x - size / 2, y - size / 2, x + size / 2, y + size / 2, decorPaint)
        }

        if (layers.none { it.type == LayerType.HULL }) {
            // 没有船体图层时给个提示性的虚线框（用四条短线代替）
            val dash = Paint().apply {
                isAntiAlias = false
                color = Color.parseColor("#7F8A94")
            }
            canvas.drawRect(w * 0.05f, h * 0.45f, w * 0.10f, h * 0.55f, dash)
        }
    }
}
