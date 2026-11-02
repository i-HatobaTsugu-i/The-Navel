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
 * 船只俯视图：按图层顺序叠加导入的图片，并应用每层的位移。
 * 坐标统一在 1080 设计画布（px）里计算，所以不同尺寸的预览看到的相对位置一致。
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

    /** 放大倍数，围绕画布中心缩放 */
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, borderPaint)

        val base = minOf(w, h) / LOGICAL_SIZE * zoom
        val cx = w / 2f
        val cy = h / 2f

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        layers.filter { it.imagePath.isNotBlank() }.forEach { layer ->
            val bitmap = cache.getOrPut(layer.imagePath) {
                ShipDataGuard.decodeLayer(context, layer.imagePath)
            } ?: return@forEach
            val dw = bitmap.width * base
            val dh = bitmap.height * base
            val left = cx - dw / 2f + layer.offsetX * base
            val top = cy - dh / 2f + layer.offsetY * base
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), bitmapPaint)
            minX = minOf(minX, left)
            minY = minOf(minY, top)
            maxX = maxOf(maxX, left + dw)
            maxY = maxOf(maxY, top + dh)

            if (layer.type == LayerType.TURRET) {
                val px = cx + layer.pivotX * base
                val py = cy + layer.pivotY * base
                val r = 4f * density
                canvas.drawCircle(px, py, r, pivotPaint)
                canvas.drawLine(px - r, py, px + r, py, pivotPaint)
                canvas.drawLine(px, py - r, px, py + r, pivotPaint)
            }
        }

        if (showRuler && minX < Float.MAX_VALUE) {
            drawRuler(canvas, w, h, base, minX, minY, maxX, maxY)
        }
        drawBowArrow(canvas, h)
    }

    /** 顶部横向量长、左侧纵向量宽；1px = 0.1m，刻度随内容自动伸缩 */
    private fun drawRuler(
        canvas: Canvas,
        w: Float,
        h: Float,
        base: Float,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float
    ) {
        val topY = (minY - 6f * density).coerceIn(textPaint.textSize + 2f, h - 1f)
        val leftX = (minX - 6f * density).coerceIn(1f, w - 1f)
        val step = 10f * base            // 10 逻辑 px = 1m
        val major = 50f * base           // 每 5m 一根长刻度

        canvas.drawLine(minX, topY, maxX, topY, tickPaint)
        canvas.drawLine(minX, topY - 4f * density, minX, topY + 4f * density, tickPaint)
        canvas.drawLine(maxX, topY - 4f * density, maxX, topY + 4f * density, tickPaint)
        var x = minX
        var index = 0
        while (x <= maxX) {
            val len = if (index % 5 == 0) 4f * density else 2f * density
            canvas.drawLine(x, topY, x, topY - len, tickPaint)
            x += step
            index++
        }
        canvas.drawText(formatMeters((maxX - minX) * METERS_PER_PX), minX, topY - 6f * density, textPaint)

        canvas.drawLine(leftX, minY, leftX, maxY, tickPaint)
        canvas.drawLine(leftX - 4f * density, minY, leftX + 4f * density, minY, tickPaint)
        canvas.drawLine(leftX - 4f * density, maxY, leftX + 4f * density, maxY, tickPaint)
        var y = minY
        index = 0
        while (y <= maxY) {
            val len = if (index % 5 == 0) 4f * density else 2f * density
            canvas.drawLine(leftX, y, leftX + len, y, tickPaint)
            y += step
            index++
        }
        canvas.drawText(formatMeters((maxY - minY) * METERS_PER_PX), leftX + 6f * density,
            maxY + textPaint.textSize, textPaint)
    }

    private fun formatMeters(value: Float): String =
        String.format(java.util.Locale.US, "%.1fm", value)

    /** 正前方始终朝左 */
    private fun drawBowArrow(canvas: Canvas, h: Float) {
        val cx = 10f * density
        val cy = h / 2f
        val s = 5f * density
        val path = Path().apply {
            moveTo(cx - s, cy)
            lineTo(cx + s, cy - s)
            lineTo(cx + s, cy + s)
            close()
        }
        canvas.drawPath(path, bowPaint)
    }

    private companion object {
        // 所有图层坐标都以这个设计画布为准
        const val LOGICAL_SIZE = 1080f
        // 1px = 0.1m
        const val METERS_PER_PX = 0.1f
    }
}
