package io.github.ihatobatsugui.thenavel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.Toast
import kotlin.math.min

class MainActivity : Activity() {

    private val pixelFont by lazy { resources.getFont(R.font.unifont) }
    private val titleFont by lazy { Typeface.create(pixelFont, Typeface.BOLD) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)
        showMenu()
    }

    private fun showMenu() {
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#10141A"))
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        val title = PixelTitle(this).apply {
            text = "The Navel"
            textSize = 48f
            typeface = titleFont
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F2EAD3"))
            extrusionColor = Color.parseColor("#6E5A33")
            depth = 4
            setPadding(0, (24 * dp).toInt(), 0, 0)
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 弹性空隔，把控件压到底部
        root.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val side = (16 * dp).toInt()
            setPadding(side, 0, side, 0)
        }
        val maxWidth = min(
            (resources.displayMetrics.widthPixels - 32 * dp).toInt(),
            (640 * dp).toInt()
        )

        val labels = listOf("新游戏", "载入", "模组", "设置", "退出")
        labels.chunked(3).forEachIndexed { rowIndex, row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.forEach { label ->
                val button = menuButton(label)
                if (label == "退出") {
                    button.setOnClickListener { finishAffinity() }
                }
                rowLayout.addView(button, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = if (rowLayout.childCount == 0) 0 else (8 * dp).toInt()
                })
            }
            // 最后一行不满时补空位，保证按钮宽度与上一行一致
            repeat(3 - row.size) {
                rowLayout.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                    marginStart = (8 * dp).toInt()
                })
            }
            menu.addView(rowLayout, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (rowIndex == 0) 0 else (8 * dp).toInt()
            })
        }

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
        }
        val version = TextView(this).apply {
            text = "版本 ${versionName()}"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#8A9099"))
        }
        val logButton = menuButton("错误日志").apply {
            setOnClickListener { showLog() }
        }
        footer.addView(version, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(logButton, LinearLayout.LayoutParams(
            (132 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (12 * dp).toInt()
        })

        root.addView(menu, LinearLayout.LayoutParams(
            maxWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(footer, LinearLayout.LayoutParams(
            maxWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (16 * dp).toInt()
        })

        setContentView(root)
        hideSystemBars()
    }

    private fun showLog() {
        val dp = resources.displayMetrics.density
        val log = CrashReport.read(this) ?: "暂无错误记录"

        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#10141A"))
        }
        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 48 * dp).toInt(),
            (520 * dp).toInt()
        )
        val panelHeight = min(
            (resources.displayMetrics.heightPixels * 0.7f).toInt(),
            (300 * dp).toInt()
        )

        val panel = PaperPanel(this).apply {
            val padH = (20 * dp).toInt()
            val padV = (18 * dp).toInt()
            setPadding(padH, padV, padH, padV)
        }

        val body = PaperTextView(this).apply {
            text = log
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }
        val scroll = ScrollView(this).apply { addView(body) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        val copy = menuButton("复制日志").apply { setOnClickListener { copyLog(log) } }
        val back = menuButton("返回").apply { setOnClickListener { showMenu() } }
        bar.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(back, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (8 * dp).toInt()
        })

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        panel.addView(column, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT))

        root.addView(panel, android.widget.FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        hideSystemBars()
    }

    private fun menuButton(label: String): PixelButton = PixelButton(this).apply {
        text = label
        textSize = 16f
        typeface = pixelFont
        setTextColor(Color.parseColor("#16191F"))
        val padH = (12 * resources.displayMetrics.density).toInt()
        val padV = (6 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)
    }

    private fun copyLog(log: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("The-Navel 日志", log))
        Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show()
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "?"

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
