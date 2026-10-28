package io.github.ihatobatsugui.thenavel

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)

        val crash = CrashReport.read(this)
        if (crash != null) {
            showCrash(crash)
            return
        }

        setContentView(buildMenu())
        hideSystemBars()
    }

    private fun buildMenu(): View {
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
                textSize = 20f
                setTextColor(Color.parseColor("#16191F"))
                val padH = (16 * dp).toInt()
                val padV = (12 * dp).toInt()
                setPadding(padH, padV, padH, padV)
            }
            root.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * dp).toInt()
            })
        }
        return root
    }

    // 点一下清掉记录并重启，回到正常界面
    private fun showCrash(trace: String) {
        val dp = resources.displayMetrics.density
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"

        val title = TextView(this).apply {
            text = "上次运行崩溃（版本 $version），错误如下"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
        }

        val body = TextView(this).apply {
            text = trace
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#FF8080"))
            val pad = (12 * dp).toInt()
            setPadding(pad, 0, pad, pad)
        }

        val scroll = ScrollView(this).apply { addView(body) }

        val clear = TextView(this).apply {
            text = "清除记录并继续"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#16191F"))
            setBackgroundColor(Color.parseColor("#C8A96B"))
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            isClickable = true
            setOnClickListener { restart() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(clear, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            isClickable = true
            setOnClickListener { restart() }
        }

        setContentView(root)
        hideSystemBars()
    }

    private fun restart() {
        CrashReport.clear(this)
        recreate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.run {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
