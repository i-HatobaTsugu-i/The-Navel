package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * 船只俯视图：按图层顺序叠加导入的图片。
 * 坐标以图层图片像素为单位（相对画布中心），量取与取景都只看「有像素的内容」，
 * 图片四周的透明留白会被忽略。1px = 0.1m。
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

    /** 显示刻度尺与长宽量取 */
    var showRuler = false
        set(value) {
            field = value
            invalidate()
        }

    /** 允许双指缩放与拖动 */
    var interactive = false
        set(value) {
            field = value
            invalidate()
        }

    var zoom = 1f
        set(value) {
            field = value.coerceIn(0.3f, 6f)
            invalidate()
        }

    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f

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
    private val opaqueCache = HashMap<String, RectF>()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = zoom * detector.scaleFactor
                return true
            }
        }
    )

    fun refresh() {
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return super.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    panX += event.x - lastX
                    panY += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastX = 0f
                lastY = 0f
            }
        }
        return true
    }

    private class Item(val bitmap: Bitmap, val layer: ShipLayer, val draw: RectF, val ink: RectF)

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
            val ink = opaqueRect(layer.imagePath, bitmap)
            items.add(
                Item(
                    bitmap, layer,
                    // 绘制用整张图的范围
                    RectF(
                        -bitmap.width / 2f + layer.offsetX,
                        -bitmap.height / 2f + layer.offsetY,
                        bitmap.width / 2f + layer.offsetX,
                        bitmap.height / 2f + layer.offsetY
                    ),
                    // 取景/量取只用有像素的部分
                    RectF(
                        ink.left + layer.offsetX,
                        ink.top + layer.offsetY,
                        ink.right + layer.offsetX,
                        ink.bottom + layer.offsetY
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
            l = minOf(l, item.ink.left)
            t = minOf(t, item.ink.top)
            r = maxOf(r, item.ink.right)
            b = maxOf(b, item.ink.bottom)
        }

        val contentW = (r - l).coerceAtLeast(1f)
        val contentH = (b - t).coerceAtLeast(1f)
        // 只按有内容的部分取景，四周留出刻度与箭头的位置
        val fit = minOf(w * 0.86f / contentW, h * 0.74f / contentH)
        val scale = fit * zoom
        val ox = w / 2f - (l + r) / 2f * scale + panX
        val oy = h / 2f - (t + b) / 2f * scale + panY

        items.forEach { item ->
            val rect = item.draw
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

    /** 图片里真正有像素的范围（相对图片中心），用于取景与量取 */
    private fun opaqueRect(key: String, bitmap: Bitmap): RectF {
        opaqueCache[key]?.let { return it }
        var l = bitmap.width
        var t = bitmap.height
        var r = -1
        var b = -1
        val step = if (bitmap.width > 600) 2 else 1
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                    if (x < l) l = x
                    if (y < t) t = y
                    if (x > r) r = x
                    if (y > b) b = y
                }
            }
        }
        val halfW = bitmap.width / 2f
        val halfH = bitmap.height / 2f
        val rect = if (r < 0) {
            RectF(-halfW, -halfH, halfW, halfH)
        } else {
            RectF(l - halfW, t - halfH, r + 1 - halfW, b + 1 - halfH)
        }
        opaqueCache[key] = rect
        return rect
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
