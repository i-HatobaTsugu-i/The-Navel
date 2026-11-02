package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 船只俯视图：按图层顺序叠加导入的图片。
 * 坐标统一以「图层图片像素」为单位（相对画布中心），先算出所有图层的整体范围，
 * 再等比缩放到预览区域内，所以不同尺寸的预览看到的相对位置一致。
 * 1px = 0.1m，用于刻度与长宽量取。
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

    /** 显示刻度尺与长宽量取（船只编辑器用） */
    var showRuler = false
        set(value) {
            field = value
            invalidate()
        }

    /** 放大倍数，围绕预览中心缩放 */
    var zoom = 1f
        set(value) {
            field = value.coerceIn(0.3f, 4f)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private val bgPaint = Paint().apply { color = Color.parseColor("#E4DDCB") }
    private val borderPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Color.parseColor("#B9AF96")
    }
    private val tickPaint = Paint().apply {
        isAntiAlias = false
        strokeWidth = density
        color = Color.parseColor("#7A7468")
    }
    private val textPaint = Paint().apply {
        isAntiAlias = false
        textSize = 10f * density
        color = Color.parseColor("#5A5348")
    }
    private val pivotPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
        color = Color.parseColor("#C0392B")
    }
    private val bowPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#1F5FA8")
    }
    private val cache = HashMap<String, Bitmap?>()

    fun refresh() {
        invalidate()
    }

    private class Item(val bitmap: Bitmap, val layer: ShipLayer, val rect: RectF)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, borderPaint)
        drawBowArrow(canvas, h)

        val items = mutableListOf<Item>()
        layers.filter { it.imagePath.isNotBlank() }.forEach { layer ->
            val bitmap = cache.getOrPut(layer.imagePath) {
                ShipDataGuard.decodeLayer(context, layer.imagePath)
            } ?: return@forEach
            val halfW = bitmap.width / 2f
            val halfH = bitmap.height / 2f
            items.add(
                Item(
                    bitmap, layer,
                    RectF(
                        -halfW + layer.offsetX,
                        -halfH + layer.offsetY,
                        halfW + layer.offsetX,
                        halfH + layer.offsetY
                    )
                )
            )
        }
        if (items.isEmpty()) return

        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        items.forEach { item ->
            l = minOf(l, item.rect.left)
            t = minOf(t, item.rect.top)
            r = maxOf(r, item.rect.right)
            b = maxOf(b, item.rect.bottom)
        }

        val contentW = (r - l).coerceAtLeast(1f)
        val contentH = (b - t).coerceAtLeast(1f)
        // 留出边距给刻度和船首箭头
        val fit = minOf(w * 0.88f / contentW, h * 0.78f / contentH)
        val scale = fit * zoom
        val ox = w / 2f - (l + r) / 2f * scale
        val oy = h / 2f - (t + b) / 2f * scale

        items.forEach { item ->
            val rect = item.rect
            canvas.drawBitmap(
                item.bitmap, null,
                RectF(
                    ox + rect.left * scale,
                    oy + rect.top * scale,
                    ox + rect.right * scale,
                    oy + rect.bottom * scale
                ),
                bitmapPaint
            )
            if (item.layer.type == LayerType.TURRET) {
                val px = ox + item.layer.pivotX * scale
                val py = oy + item.layer.pivotY * scale
                val rad = 4f * density
                canvas.drawCircle(px, py, rad, pivotPaint)
                canvas.drawLine(px - rad, py, px + rad, py, pivotPaint)
                canvas.drawLine(px, py - rad, px, py + rad, pivotPaint)
            }
        }

        if (showRuler) drawRuler(canvas, w, h, scale, ox, oy, l, t, r, b)
    }

    /** 顶部量长、左侧量宽，单位米（1px = 0.1m） */
    private fun drawRuler(
        canvas: Canvas,
        w: Float,
        h: Float,
        scale: Float,
        ox: Float,
        oy: Float,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        val left = ox + l * scale
        val right = ox + r * scale
        val top = oy + t * scale
        val bottom = oy + b * scale
        val topY = (top - 8f * density).coerceIn(textPaint.textSize + 4f, h - 1f)
        val leftX = (left - 8f * density).coerceIn(1f, w - 12f * density)

        canvas.drawLine(left, topY, right, topY, tickPaint)
        canvas.drawLine(left, topY - 4f * density, left, topY + 4f * density, tickPaint)
        canvas.drawLine(right, topY - 4f * density, right, topY + 4f * density, tickPaint)

        val step = 50f * scale          // 50 逻辑 px = 5m
        var x = left
        var index = 0
        while (x <= right && step > 1f) {
            val len = if (index % 5 == 0) 4f * density else 2f * density
            canvas.drawLine(x, topY, x, topY - len, tickPaint)
            x += step
            index++
        }
        canvas.drawText(formatMeters((r - l) * METERS_PER_PX), left, topY - 6f * density, textPaint)

        canvas.drawLine(leftX, top, leftX, bottom, tickPaint)
        canvas.drawLine(leftX - 4f * density, top, leftX + 4f * density, top, tickPaint)
        canvas.drawLine(leftX - 4f * density, bottom, leftX + 4f * density, bottom, tickPaint)
        var y = top
        index = 0
        while (y <= bottom && step > 1f) {
            val len = if (index % 5 == 0) 4f * density else 2f * density
            canvas.drawLine(leftX, y, leftX + len, y, tickPaint)
            y += step
            index++
        }
        canvas.drawText(
            formatMeters((b - t) * METERS_PER_PX),
            leftX + 6f * density,
            bottom + textPaint.textSize,
            textPaint
        )
    }

    private fun formatMeters(value: Float): String =
        String.format(java.util.Locale.US, "%.1fm", value)

    /** 正前方始终朝左 */
    private fun drawBowArrow(canvas: Canvas, h: Float) {
        val cx = 12f * density
        val cy = h / 2f
        val s = 6f * density
        val path = Path().apply {
            moveTo(cx - s, cy)
            lineTo(cx + s, cy - s)
            lineTo(cx + s, cy + s)
            close()
        }
        canvas.drawPath(path, bowPaint)
    }

    private companion object {
        // 1px = 0.1m
        const val METERS_PER_PX = 0.1f
    }
}
