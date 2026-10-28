package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 船只俯视图：按图层顺序叠加导入的图片（等比缩放、居中）。
 * 图层还没导入图片时，退回按图层类型画的示意图。
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
    private val cache = HashMap<String, Bitmap?>()

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val images = layers.filter { it.imagePath.isNotBlank() }
        if (images.isNotEmpty()) {
            images.forEach { layer ->
                val bitmap = cache.getOrPut(layer.imagePath) {
                    runCatching { BitmapFactory.decodeFile(layer.imagePath) }.getOrNull()
                } ?: return@forEach
                val scale = minOf(w * 0.9f / bitmap.width, h * 0.9f / bitmap.height)
                val dw = bitmap.width * scale
                val dh = bitmap.height * scale
                val left = (w - dw) / 2f
                val top = (h - dh) / 2f
                canvas.drawBitmap(bitmap, null,
                    android.graphics.RectF(left, top, left + dw, top + dh), bitmapPaint)
            }
            return
        }

        // 无图片时：示意图
        val path = android.graphics.Path()
        path.moveTo(w * 0.05f, h * 0.5f)
        path.lineTo(w * 0.30f, h * 0.16f)
        path.lineTo(w * 0.75f, h * 0.16f)
        path.lineTo(w * 0.95f, h * 0.5f)
        path.lineTo(w * 0.75f, h * 0.84f)
        path.lineTo(w * 0.30f, h * 0.84f)
        path.close()
        canvas.drawPath(path, hullFill)
        canvas.drawPath(path, hullStroke)
    }
}
