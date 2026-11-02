package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 船只俯视图：按图层顺序叠加导入的图片（等比缩放、居中），并应用每层的位移。
 * 炮塔图层会画出转轴中心标记，方便对齐。
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

    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private val pivotPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
        color = Color.parseColor("#C0392B")
    }
    private val cache = HashMap<String, Bitmap?>()

    fun refresh() {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // 预览按 432 设计画布缩放，位移单位与编辑器一致
        // 位移与转轴直接按预览像素计算（单位 px）
        layers.filter { it.imagePath.isNotBlank() }.forEach { layer ->
            val bitmap = cache.getOrPut(layer.imagePath) {
                ShipDataGuard.decodeLayer(context, layer.imagePath)
            } ?: return@forEach
            val scale = minOf(w * 0.9f / bitmap.width, h * 0.9f / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val left = (w - dw) / 2f + layer.offsetX
            val top = (h - dh) / 2f + layer.offsetY
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), bitmapPaint)

            if (layer.type == LayerType.TURRET) {
                val px = (w / 2f) + layer.pivotX
                val py = (h / 2f) + layer.pivotY
                val r = 4f * resources.displayMetrics.density
                canvas.drawCircle(px, py, r, pivotPaint)
                canvas.drawLine(px - r, py, px + r, py, pivotPaint)
                canvas.drawLine(px, py - r, px, py + r, pivotPaint)
            }
        }
    }
}
