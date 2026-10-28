package io.github.ihatobatsugui.thenavel

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E1620"))
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
        }

        listOf("开始", "较长的按钮文本示例").forEach { label ->
            val button = PixelButton(this).apply {
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
                val padH = (10 * dp).toInt()
                val padV = (6 * dp).toInt()
                setPadding(padH, padV, padH, padV)
            }
            root.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * dp).toInt()
            })
        }

        setContentView(root)
    }
}
