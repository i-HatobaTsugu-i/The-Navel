package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 打字机圆键：深色键帽 + 上缘高光 + 白色字模，按下缩小变暗。
 */
class RoundKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val capPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#5C5C5C") }
    private val ringPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.parseColor("#2A2A2A")
    }
    private val glossPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#8A8A8A") }
    private val labelPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#EDEDED")
        textAlign = Paint.Align.CENTER
    }

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }
    var onTap: (() -> Unit)? = null

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp
        if (radius <= 0f) return
        canvas.drawCircle(cx, cy, radius, capPaint)
        canvas.drawCircle(cx, cy - radius * 0.35f, radius * 0.45f, glossPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        labelPaint.textSize = radius * (if (label.length > 1) 0.62f else 1.05f)
        val baseline = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(label, cx, baseline, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                capPaint.color = Color.parseColor("#3E3E3E")
                invalidate()
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start()
            }
            MotionEvent.ACTION_UP -> {
                capPaint.color = Color.parseColor("#5C5C5C")
                invalidate()
                animate().scaleX(1f).scaleY(1f).setDuration(60).start()
            }
            MotionEvent.ACTION_CANCEL -> {
                capPaint.color = Color.parseColor("#5C5C5C")
                invalidate()
                animate().scaleX(1f).scaleY(1f).setDuration(60).start()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        onTap?.invoke()
        return super.performClick()
    }
}
