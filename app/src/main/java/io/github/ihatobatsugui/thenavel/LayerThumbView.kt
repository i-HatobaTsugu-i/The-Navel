package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 图层行里的缩略图：只画图片中有像素的部分，四周空白被裁掉。
 */
class LayerThumbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var path: String = ""
        set(value) {
            if (field == value) return
            field = value
            bitmap = null
            ink = null
            invalidate()
        }

    private var bitmap: Bitmap? = null
    private var ink: Rect? = null
    private val density = resources.displayMetrics.density

    private val paint = Paint().apply { isFilterBitmap = false }
    private val bgPaint = Paint().apply { color = Color.parseColor("#DED6C2") }
    private val borderPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Color.parseColor("#B9AF96")
    }

    private fun load() {
        val bm = ShipDataGuard.decodeLayer(context, path) ?: return
        bitmap = bm
        var l = bm.width
        var t = bm.height
        var r = -1
        var b = -1
        val step = if (bm.width > 400) 3 else 1
        for (y in 0 until bm.height step step) {
            for (x in 0 until bm.width step step) {
                if (Color.alpha(bm.getPixel(x, y)) > 8) {
                    if (x < l) l = x
                    if (y < t) t = y
                    if (x > r) r = x
                    if (y > b) b = y
                }
            }
        }
        ink = if (r < 0) Rect(0, 0, bm.width, bm.height) else Rect(l, t, r + 1, b + 1)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, borderPaint)

        if (bitmap == null && path.isNotBlank()) load()
        val bm = bitmap ?: return
        val src = ink ?: return
        val scale = minOf((w - 2f) / src.width(), (h - 2f) / src.height())
        val dw = src.width() * scale
        val dh = src.height() * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        canvas.drawBitmap(bm, src, RectF(left, top, left + dw, top + dh), paint)
    }
}
