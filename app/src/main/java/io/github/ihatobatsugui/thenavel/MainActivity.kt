package io.github.ihatobatsugui.thenavel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

class MainActivity : Activity() {

    private val pixelFont by lazy { resources.getFont(R.font.unifont) }

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
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_anchor_title)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(icon, LinearLayout.LayoutParams(
            (56 * dp).toInt(), (56 * dp).toInt()))

        val title = TextView(this).apply {
            text = "The Navel"
            textSize = 48f
            typeface = pixelFont
            letterSpacing = 0.18f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.parseColor("#F2EAD3"))
        }
        header.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (12 * dp).toInt()
        })

        root.addView(header, LinearLayout.LayoutParams(
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

        val labels = listOf("新游戏", "载入", "编辑器", "模组", "设置", "鸣谢", "退出")
        labels.chunked(3).forEachIndexed { rowIndex, row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.forEach { label ->
                val button = menuButton(label)
                when (label) {
                    "退出" -> button.setOnClickListener { finishAffinity() }
                    "鸣谢" -> button.setOnClickListener { showCredits() }
                    "编辑器" -> button.setOnClickListener { showEditor() }
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

        val container = FrameLayout(this)
        container.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(container)
        hideSystemBars()
    }

    private fun showLog() {
        val dp = resources.displayMetrics.density
        val log = CrashReport.read(this) ?: "暂无错误记录"

        val root = FrameLayout(this)
        root.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply {
            setBackgroundColor(SCRIM)
            setOnClickListener { showMenu() }
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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
            isClickable = true
        }

        val body = PaperTextView(this).apply {
            text = log
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }
        val scroll = ScrollView(this).apply {
            // 内容不足一屏时也把 Textview 拉满，否则横线只画得出一行
            isFillViewport = true
            addView(body)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        val copy = menuButton("复制日志").apply { setOnClickListener { copyLog(log) } }
        bar.addView(copy, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        root.addView(panel, FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        hideSystemBars()
    }

    private fun showCredits() {
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply {
            setBackgroundColor(SCRIM)
            setOnClickListener { showMenu() }
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 48 * dp).toInt(),
            (420 * dp).toInt()
        )

        val panel = PaperPanel(this).apply {
            val padH = (20 * dp).toInt()
            setPadding(padH, (18 * dp).toInt(), padH, (18 * dp).toInt())
            isClickable = true
        }

        val text = PaperTextView(this).apply {
            text = "代码:Deepseek大肥鱼\n美工:Deepseek大肥鱼,鳩羽つぐ"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding((8 * dp).toInt(), 0, 0, (8 * dp).toInt())
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        root.addView(panel, FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        hideSystemBars()
    }

    private fun showEditor() {
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply {
            setBackgroundColor(SCRIM)
            setOnClickListener { showMenu() }
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 48 * dp).toInt(),
            (420 * dp).toInt()
        )
        val panel = PaperPanel(this).apply {
            val padH = (20 * dp).toInt()
            setPadding(padH, (18 * dp).toInt(), padH, (18 * dp).toInt())
            isClickable = true
        }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("编辑船只", "编辑战役", "返回").forEachIndexed { index, label ->
            val button = menuButton(label).apply {
                when (label) {
                    "编辑船只" -> setOnClickListener { showShipEditor() }
                    "返回" -> setOnClickListener { showMenu() }
                }
            }
            column.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (index == 0) 0 else (10 * dp).toInt()
            })
        }
        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        root.addView(panel, FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        hideSystemBars()
    }

    // 船只编辑器壳：目前只有画布框架，零件与操作待接入
    private fun showShipEditor() {
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply {
            setBackgroundColor(SCRIM)
            setOnClickListener { showEditor() }
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 32 * dp).toInt(),
            (640 * dp).toInt()
        )
        val panelHeight = (resources.displayMetrics.heightPixels * 0.8f).toInt()

        val panel = PaperPanel(this).apply {
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
            isClickable = true
        }

        val title = TextView(this).apply {
            text = "船只编辑器"
            textSize = 24f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
        }
        val hint = TextView(this).apply {
            text = "画布与零件库待接入"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#5A5A5A"))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(hint, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            })
        }
        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        root.addView(panel, FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
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

    companion object {
        private const val SCRIM = 0x59000000
    }
}
