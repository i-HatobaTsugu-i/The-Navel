package io.github.ihatobatsugui.thenavel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

class MainActivity : Activity() {

    private val pixelFont by lazy { resources.getFont(R.font.unifont) }

    private enum class Screen { MENU, EDITOR, SHIP_EDITOR, LOAD_SHIPS, LOG, CREDITS }

    private var screen = Screen.MENU
    private var layersBox: LinearLayout? = null
    private var shipPreview: ShipPreviewView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)
        showMenu()
    }

    private fun showMenu() {
        screen = Screen.MENU
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
        screen = Screen.LOG
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
        screen = Screen.CREDITS
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
        screen = Screen.EDITOR
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
        listOf("编辑船只", "编辑战役").forEachIndexed { index, label ->
            val button = menuButton(label).apply {
                when (label) {
                    "编辑船只" -> setOnClickListener { showShipEditor() }
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
        screen = Screen.SHIP_EDITOR
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // 空白处不响应点击：只能按返回键或右上角“返回”退出
        root.addView(View(this).apply { setBackgroundColor(SCRIM) }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 24 * dp).toInt(),
            (720 * dp).toInt()
        )
        val panel = PaperPanel(this).apply {
            val padH = (16 * dp).toInt()
            setPadding(padH, (12 * dp).toInt(), padH, (12 * dp).toInt())
            isClickable = true
        }

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val load = menuButton("载入船只").apply { setOnClickListener { showLoadShips() } }
        val back = menuButton("返回").apply { setOnClickListener { showEditor() } }
        topBar.addView(load, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(back, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        outer.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val title = TextView(this).apply {
            text = "船只编辑器"
            textSize = 24f
            typeface = pixelFont
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#2B2B2B"))
            setPadding(0, (8 * dp).toInt(), 0, (6 * dp).toInt())
        }
        outer.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val preview = ShipPreviewView(this).apply { layers = ShipStore.draft.layers }
        shipPreview = preview
        content.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (110 * dp).toInt()))

        content.addView(fieldRow("船只代号", ShipStore.draft.code,
            InputType.TYPE_CLASS_TEXT) { ShipStore.draft.code = it }, fieldParams(dp))
        content.addView(fieldRow("最高速度", ShipStore.draft.maxSpeed,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "knot") {
            ShipStore.draft.maxSpeed = it
        }, fieldParams(dp))
        content.addView(periodRow(), fieldParams(dp))

        val layerTitle = TextView(this).apply {
            text = "图层"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
            setPadding(0, (12 * dp).toInt(), 0, (2 * dp).toInt())
        }
        content.addView(layerTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layersBox = box
        content.addView(box, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val addLayer = menuButton("添加图层").apply {
            setOnClickListener {
                ShipStore.draft.layers.add(ShipLayer())
                refreshLayers()
            }
        }
        content.addView(addLayer, LinearLayout.LayoutParams(
            (140 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * dp).toInt()
        })

        val save = menuButton("保存").apply { setOnClickListener { saveShip() } }
        content.addView(save, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (14 * dp).toInt()
        })

        val scroll = ScrollView(this).apply { addView(content) }
        outer.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        panel.addView(outer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val panelHeight = (resources.displayMetrics.heightPixels * 0.92f).toInt()
        root.addView(panel, FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        hideSystemBars()
        refreshLayers()
    }

    private fun fieldParams(dp: Float): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (8 * dp).toInt()
    }

    private fun editField(
        initial: String,
        hintText: String? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        onChange: (String) -> Unit
    ): EditText = EditText(this).apply {
        setText(initial)
        hint = hintText
        textSize = 16f
        typeface = pixelFont
        setTextColor(Color.parseColor("#1F1F1F"))
        setHintTextColor(Color.parseColor("#9A9384"))
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, 0, 0, 0)
        includeFontPadding = false
        minHeight = 0
        isSingleLine = true
        this.inputType = inputType
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                onChange(s?.toString() ?: "")
            }
        })
    }

    // 表单行：标签 + 输入框（可带单位）+ 下方蓝线
    private fun fieldRow(
        label: String,
        initial: String,
        inputType: Int,
        suffix: String? = null,
        onChange: (String) -> Unit
    ): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = TextView(this).apply {
            text = "$label："
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(Color.parseColor("#2B2B2B"))
        }
        row.addView(name, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(editField(initial, null, inputType, onChange), LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (suffix != null) {
            val unit = TextView(this).apply {
                text = suffix
                textSize = 16f
                typeface = pixelFont
                includeFontPadding = false
                setTextColor(Color.parseColor("#5A5A5A"))
            }
            row.addView(unit, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (6 * dp).toInt()
            })
        }
        return underlined(row)
    }

    private fun periodRow(): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = TextView(this).apply {
            text = "可使用时期："
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(Color.parseColor("#2B2B2B"))
        }
        val tilde = TextView(this).apply {
            text = " ~ "
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(Color.parseColor("#2B2B2B"))
        }
        row.addView(name, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(editField(ShipStore.draft.periodFrom, "Y,M,D") {
            ShipStore.draft.periodFrom = it
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(tilde, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(editField(ShipStore.draft.periodTo, "Y,M,D") {
            ShipStore.draft.periodTo = it
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return underlined(row)
    }

    private fun underlined(row: View): View {
        val dp = resources.displayMetrics.density
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        container.addView(View(this).apply { setBackgroundColor(RULE_COLOR) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1)))
        return container
    }

    private fun refreshLayers() {
        val box = layersBox ?: return
        val dp = resources.displayMetrics.density
        box.removeAllViews()
        ShipStore.draft.layers.forEachIndexed { index, layer ->
            box.addView(layerRow(index, layer, dp))
        }
        shipPreview?.layers = ShipStore.draft.layers.toList()
    }

    private fun layerRow(index: Int, layer: ShipLayer, dp: Float): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val name = TextView(this).apply {
            text = "${index + 1}."
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(Color.parseColor("#2B2B2B"))
        }
        val typeButton = menuButton(layer.type.label).apply {
            setOnClickListener {
                layer.type = layer.type.next()
                refreshLayers()
            }
        }
        val bindButton = menuButton(bindLabel(layer)).apply {
            setOnClickListener {
                cycleBind(layer, index)
                refreshLayers()
            }
        }
        val remove = menuButton("×").apply {
            setOnClickListener {
                ShipStore.draft.layers.removeAt(index)
                refreshLayers()
            }
        }
        row.addView(name, LinearLayout.LayoutParams(
            (28 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(typeButton, LinearLayout.LayoutParams(
            (104 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(bindButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (6 * dp).toInt()
        })
        row.addView(remove, LinearLayout.LayoutParams(
            (48 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (6 * dp).toInt()
        })
        return row
    }

    private fun bindLabel(layer: ShipLayer): String =
        if (layer.bindTo <= 0) "绑定:无" else "绑定:图层${layer.bindTo}"

    private fun cycleBind(layer: ShipLayer, index: Int) {
        val total = ShipStore.draft.layers.size
        val options = mutableListOf(-1)
        for (k in 1..total) if (k != index + 1) options.add(k)
        val position = options.indexOf(layer.bindTo)
        layer.bindTo = options[(position + 1) % options.size]
    }

    private fun saveShip() {
        val draft = ShipStore.draft
        if (draft.code.isBlank()) {
            Toast.makeText(this, "请先填写船只代号", Toast.LENGTH_SHORT).show()
            return
        }
        ShipStore.save(draft)
        Toast.makeText(this, "已保存 ${draft.code}", Toast.LENGTH_SHORT).show()
    }

    private fun showLoadShips() {
        screen = Screen.LOAD_SHIPS
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply {
            setBackgroundColor(SCRIM)
            setOnClickListener { showShipEditor() }
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 40 * dp).toInt(),
            (520 * dp).toInt()
        )
        val panelHeight = (resources.displayMetrics.heightPixels * 0.8f).toInt()
        val panel = PaperPanel(this).apply {
            val padH = (20 * dp).toInt()
            setPadding(padH, (16 * dp).toInt(), padH, (16 * dp).toInt())
            isClickable = true
        }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "载入船只"
            textSize = 24f
            typeface = pixelFont
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#2B2B2B"))
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        column.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (ShipStore.ships.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "暂无已保存的船只"
                textSize = 16f
                typeface = pixelFont
                setTextColor(Color.parseColor("#5A5A5A"))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        } else {
            ShipStore.ships.forEach { ship -> list.addView(shipEntry(ship, dp)) }
        }
        val scroll = ScrollView(this).apply { addView(list) }
        column.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        hideSystemBars()
    }

    private fun shipEntry(ship: Ship, dp: Float): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            isClickable = true
            setOnClickListener {
                ShipStore.draft = ship.duplicate()
                showShipEditor()
            }
        }
        val thumb = ShipPreviewView(this).apply {
            layers = ship.layers
            setBackgroundColor(Color.TRANSPARENT)
        }
        row.addView(thumb, LinearLayout.LayoutParams(
            (72 * dp).toInt(), (48 * dp).toInt()))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply {
            text = ship.code
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        info.addView(TextView(this).apply {
            text = "最后修改 ${ship.modifiedAt}"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#5A5A5A"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(info, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (10 * dp).toInt()
        })

        val delete = menuButton("删除").apply {
            setOnClickListener {
                ShipStore.delete(ship)
                showLoadShips()
            }
        }
        row.addView(delete, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
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

    override fun onBackPressed() {
        when (screen) {
            Screen.MENU -> super.onBackPressed()
            Screen.SHIP_EDITOR -> showEditor()
            Screen.LOAD_SHIPS -> showShipEditor()
            else -> showMenu()
        }
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

    companion object {
        private const val SCRIM = 0x59000000
        private val RULE_COLOR = Color.parseColor("#9DB7D8")
    }
}
