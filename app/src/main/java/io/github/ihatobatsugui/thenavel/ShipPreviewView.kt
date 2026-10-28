package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 船只俯视图：按图层顺序叠加导入的图片（等比缩放、居中）。
 * 图层不使用矢量绘制，没有导入图片时就是空白的。
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        layers.filter { it.imagePath.isNotBlank() }.forEach { layer ->
            if (!cache.containsKey(layer.imagePath)) {
                cache[layer.imagePath] = runCatching {
                    BitmapFactory.decodeFile(layer.imagePath)
                }.getOrNull()
            }
            val bitmap = cache[layer.imagePath] ?: return@forEach
            val scale = minOf(w * 0.9f / bitmap.width, h * 0.9f / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val left = (w - dw) / 2f
            val top = (h - dh) / 2f
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), bitmapPaint)
        }
    }
}
