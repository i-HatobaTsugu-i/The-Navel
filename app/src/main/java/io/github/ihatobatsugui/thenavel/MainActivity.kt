package io.github.ihatobatsugui.thenavel

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.min

class MainActivity : Activity() {

    private val pixelFont by lazy { resources.getFont(R.font.unifont) }

    private enum class Screen { MENU, EDITOR, SHIP_EDITOR, LOG, CREDITS }

    private var screen = Screen.MENU
    private var layersBox: LinearLayout? = null
    private var shipPreview: ShipPreviewView? = null
    private var pendingLayer = -1
    private var currentPanel: View? = null
    private var currentScroll: android.widget.ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        ShipStore.init(this)
        CrashReport.install(this)
        showMenu()
    }

    // ---------- 主菜单 ----------

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
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_anchor_title)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()))

        header.addView(TextView(this).apply {
            text = "The Navel"
            textSize = 48f
            typeface = pixelFont
            letterSpacing = 0.18f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.parseColor("#F2EAD3"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (12 * dp).toInt()
        })
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

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
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
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
        footer.addView(TextView(this).apply {
            text = "版本 ${versionName()}"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#8A9099"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(menuButton("错误日志").apply { setOnClickListener { showLog() } },
            LinearLayout.LayoutParams((132 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
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

    // ---------- 纸张页面外壳：整张纸滚动、短内容垂直居中、点空白返回 ----------

    private fun showPaperPage(panel: View, widthPx: Int, onBlankTap: (() -> Unit)?) {
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(OceanView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply {
            setBackgroundColor(SCRIM)
            if (onBlankTap != null) setOnClickListener { leave(onBlankTap) }
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val host = FrameLayout(this).apply {
            minimumHeight = resources.displayMetrics.heightPixels
        }
        host.addView(panel, FrameLayout.LayoutParams(widthPx,
            FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(host)
        }
        root.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)
        hideSystemBars()
        currentPanel = panel
        currentScroll = scroll
        popIn(panel)
    }

    // 把输入字段滚进可视区：只滚必要的量，底部留 24dp 余量
    private fun ensureVisible(field: View) {
        val scroll = currentScroll ?: return
        if (scroll.height <= 0) return
        val fieldPos = IntArray(2)
        field.getLocationInWindow(fieldPos)
        val scrollPos = IntArray(2)
        scroll.getLocationInWindow(scrollPos)
        val topInContent = fieldPos[1] - scrollPos[1] + scroll.scrollY
        val bottomInContent = topInContent + field.height
        val margin = (24 * resources.displayMetrics.density).toInt()
        val viewport = scroll.height
        val target = when {
            bottomInContent + margin > viewport -> bottomInContent + margin - viewport
            topInContent - margin < 0 -> (topInContent - margin).coerceAtLeast(0)
            else -> return
        }
        scroll.smoothScrollTo(0, target)
    }

    // 纸张收起：反向动画后再切页
    private fun leave(action: () -> Unit) {
        val panel = currentPanel
        if (panel == null) {
            action()
            return
        }
        panel.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(140)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { action() }
            .start()
    }

    private fun closeDialog(panel: View, dialog: Dialog) {
        panel.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(120)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { dialog.dismiss() }
            .start()
    }

    // 纸张上弹动画
    private fun popIn(view: View) {
        view.alpha = 0f
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(170)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    // ---------- 错误日志 ----------

    private fun showLog() {
        screen = Screen.LOG
        val dp = resources.displayMetrics.density
        val log = CrashReport.read(this) ?: "暂无错误记录"
        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 48 * dp).toInt(),
            (520 * dp).toInt()
        )
        val panel = paperPanel()
        val body = PaperTextView(this).apply {
            text = log
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding((8 * dp).toInt(), 0, 0, (8 * dp).toInt())
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(body, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(linkButton("复制日志") { copyLog(log) }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            })
            addView(linkButton("返回") { leave { showMenu() } }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * dp).toInt()
            })
        }
        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        showPaperPage(panel, panelWidth) { showMenu() }
    }

    // ---------- 鸣谢 ----------

    private fun showCredits() {
        screen = Screen.CREDITS
        val dp = resources.displayMetrics.density
        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 48 * dp).toInt(),
            (420 * dp).toInt()
        )
        val panel = paperPanel()
        val text = PaperTextView(this).apply {
            text = "代码:Deepseek大肥鱼\n美工:Deepseek大肥鱼,鳩羽つぐ"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding((8 * dp).toInt(), 0, 0, (8 * dp).toInt())
        }
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(linkButton("返回") { leave { showMenu() } }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            })
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        showPaperPage(panel, panelWidth) { showMenu() }
    }

    // ---------- 编辑器菜单 ----------

    private fun showEditor() {
        screen = Screen.EDITOR
        val dp = resources.displayMetrics.density
        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 48 * dp).toInt(),
            (420 * dp).toInt()
        )
        val panel = paperPanel()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("编辑船只", "编辑战役").forEachIndexed { index, label ->
            column.addView(linkButton(label) {
                if (label == "编辑船只") showShipEditor()
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (index == 0) 0 else (12 * dp).toInt()
            })
        }
        column.addView(linkButton("返回") { leave { showMenu() } }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * dp).toInt()
        })
        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        showPaperPage(panel, panelWidth) { showMenu() }
    }

    // ---------- 船只编辑器 ----------

    private fun showShipEditor() {
        screen = Screen.SHIP_EDITOR
        val dp = resources.displayMetrics.density
        val panelWidth = min(
            (resources.displayMetrics.widthPixels - 24 * dp).toInt(),
            (720 * dp).toInt()
        )
        val panel = paperPanel()

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(iconView(R.drawable.ic_plus) { confirmNewShip() },
            LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()))
        topBar.addView(iconView(R.drawable.ic_floppy) { showLoadShips() },
            LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
            marginStart = (12 * dp).toInt()
        })
        topBar.addView(TextView(this).apply {
            text = "船只编辑器"
            textSize = 24f
            typeface = pixelFont
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#2B2B2B"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(linkButton("返回") { leave { showEditor() } }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        outer.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val preview = ShipPreviewView(this).apply { layers = ShipStore.draft.layers }
        shipPreview = preview
        content.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (110 * dp).toInt()))

        content.addView(systemRow("船只代号", ShipStore.draft.code, "点击输入",
            InputType.TYPE_CLASS_TEXT, 24) { ShipStore.draft.code = it }, fieldParams(dp))
        content.addView(systemRow("固定航速", ShipStore.draft.maxSpeed, "点击输入",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, 8, suffix = "knots") {
            ShipStore.draft.maxSpeed = it
        }, fieldParams(dp))
        content.addView(systemRow("吨位", ShipStore.draft.tonnage, "点击输入",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, 10, suffix = "tons") {
            ShipStore.draft.tonnage = it
        }, fieldParams(dp))
        content.addView(systemRow("权重", ShipStore.draft.weight, "点击输入",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, 10) {
            ShipStore.draft.weight = it
        }, fieldParams(dp))
        content.addView(periodRow(), fieldParams(dp))

        content.addView(TextView(this).apply {
            text = "图层"
            textSize = 16f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
            setPadding(0, (12 * dp).toInt(), 0, (2 * dp).toInt())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layersBox = box
        content.addView(box, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        content.addView(linkButton("添加图层") {
            ShipStore.draft.layers.add(ShipLayer(level = ShipStore.draft.layers.size + 1))
            refreshLayers()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * dp).toInt()
        })

        outer.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(outer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        showPaperPage(panel, panelWidth) { showEditor() }
        refreshLayers()
    }

    private fun confirmNewShip() {
        if (!draftDirty()) {
            ShipStore.draft = Ship()
            showShipEditor()
            return
        }
        showPaperDialog("当前内容尚未保存", "是否先保存当前船只，再新建？") { column, dialog, panel ->
            column.addView(linkButton("保存并新建") {
                closeDialog(panel, dialog)
                saveShip(silent = true)
                ShipStore.draft = Ship()
                showShipEditor()
            }, dialogButtonParams())
            column.addView(linkButton("直接新建") {
                closeDialog(panel, dialog)
                ShipStore.draft = Ship()
                showShipEditor()
            }, dialogButtonParams())
            column.addView(linkButton("取消") { closeDialog(panel, dialog) }, dialogButtonParams())
        }
    }

    private fun dialogButtonParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (10 * resources.displayMetrics.density).toInt()
    }

    // 纸张风格的弹窗（替代系统 AlertDialog）
    private fun showPaperDialog(
        title: String,
        message: String?,
        build: (LinearLayout, Dialog, View) -> Unit
    ) {
        val dp = resources.displayMetrics.density
        val dialog = Dialog(this)
        val panel = paperPanel()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(this).apply {
            text = title
            textSize = 20f
            typeface = pixelFont
            setTextColor(Color.parseColor("#2B2B2B"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (message != null) {
            column.addView(TextView(this).apply {
                text = message
                textSize = 16f
                typeface = pixelFont
                setTextColor(Color.parseColor("#3A3A3A"))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * dp).toInt()
            })
        }
        build(column, dialog, panel)
        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            min((resources.displayMetrics.widthPixels - 80 * dp).toInt(), (420 * dp).toInt()),
            LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()
        dialog.setCanceledOnTouchOutside(false)
        popIn(panel)
    }

    // ---------- 纸张与弹窗 ----------


    private fun paperPanel(): PaperPanel {
        val dp = resources.displayMetrics.density
        return PaperPanel(this).apply {
            val padH = (18 * dp).toInt()
            setPadding(padH, (14 * dp).toInt(), padH, (14 * dp).toInt())
            isClickable = true
        }
    }

    private fun fieldParams(dp: Float): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (8 * dp).toInt()
    }

    /** 隐藏 1dp 透明 EditText：接收系统输入法输入，界面上只显示自绘文字。 */
    private fun systemField(
        initial: String,
        hint: String,
        inputType: Int,
        maxLen: Int,
        digitsOnly: Boolean,
        onChange: (String) -> Unit
    ): Pair<TextView, EditText> {
        val hintColor = Color.parseColor("#9A9384")
        val inkColor = Color.parseColor("#1F1F1F")
        val display = InputDisplayView(this).apply {
            text = initial.ifEmpty { hint }
            isPlaceholder = initial.isEmpty()
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(if (initial.isEmpty()) hintColor else inkColor)
            isClickable = true
        }
        val filters = mutableListOf<InputFilter>(InputFilter.LengthFilter(maxLen))
        if (digitsOnly) {
            filters.add(InputFilter { source, _, _, _, _, _ ->
                source.filter { it.isDigit() || it == ',' }
            })
        }
        val input = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            isSingleLine = true
            this.inputType = inputType
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            alpha = 0f
            setBackgroundColor(Color.TRANSPARENT)
            this.filters = filters.toTypedArray()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString() ?: ""
                    display.isPlaceholder = value.isEmpty()
                    display.text = if (value.isEmpty()) hint else value
                    display.setTextColor(if (value.isEmpty()) hintColor else inkColor)
                    onChange(value)
                }
            })
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) display.setBlinking(false)
        }
        display.setOnClickListener {
            // 每次点入都把「显示的当前值」同步回隐藏输入框，并重置输入法组合状态；
            // 否则读取船只后输入框里可能残留上一次的组合文本，退格会先清组合而看似“删不掉”
            val current = if (display.isPlaceholder) "" else display.text.toString()
            input.setText(current)
            input.setSelection(current.length)
            input.requestFocus()
            display.setBlinking(true)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            // 输入法弹出后把该行滚到可视区（多次重试以覆盖窗口缩放时序）
            listOf(350L, 600L, 900L).forEach { delay ->
                display.postDelayed({ ensureVisible(display) }, delay)
            }
        }
        return display to input
    }

    private fun systemRow(
        label: String,
        initial: String,
        hint: String,
        inputType: Int,
        maxLen: Int,
        digitsOnly: Boolean = false,
        suffix: String? = null,
        onChange: (String) -> Unit
    ): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = "$label："
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(Color.parseColor("#2B2B2B"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val (display, input) = systemField(initial, hint, inputType, maxLen, digitsOnly, onChange)
        row.addView(display, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(input, LinearLayout.LayoutParams((1 * dp).toInt(), (1 * dp).toInt()))
        if (suffix != null) {
            row.addView(TextView(this).apply {
                text = suffix
                textSize = 16f
                typeface = pixelFont
                includeFontPadding = false
                setTextColor(Color.parseColor("#5A5A5A"))
            }, LinearLayout.LayoutParams(
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
        row.addView(TextView(this).apply {
            text = "可使用时期："
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(Color.parseColor("#2B2B2B"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val (fromDisplay, fromInput) = systemField(
            ShipStore.draft.periodFrom, "Y,M,D", InputType.TYPE_CLASS_TEXT, 12, true
        ) { ShipStore.draft.periodFrom = it }
        val (toDisplay, toInput) = systemField(
            ShipStore.draft.periodTo, "Y,M,D", InputType.TYPE_CLASS_TEXT, 12, true
        ) { ShipStore.draft.periodTo = it }

        row.addView(fromDisplay, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(fromInput, LinearLayout.LayoutParams((1 * dp).toInt(), (1 * dp).toInt()))
        row.addView(TextView(this).apply {
            text = " ~ "
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(Color.parseColor("#2B2B2B"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(toDisplay, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toInput, LinearLayout.LayoutParams((1 * dp).toInt(), (1 * dp).toInt()))
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

    // ---------- 图层 ----------

    private fun orderedLayers(): List<Pair<Int, Boolean>> {
        val layers = ShipStore.draft.layers
        val result = mutableListOf<Pair<Int, Boolean>>()
        val isChild = BooleanArray(layers.size)
        layers.indices.forEach { index ->
            val bind = layers[index].bindTo
            if (bind in 1..layers.size && bind - 1 != index) isChild[index] = true
        }
        layers.indices.filter { !isChild[it] }
            .sortedBy { layers[it].level }
            .forEach { index ->
                result.add(index to false)
                layers.indices.filter { layers[it].bindTo == index + 1 }
                    .sortedBy { layers[it].level }
                    .forEach { child -> result.add(child to true) }
            }
        layers.indices.forEach { index ->
            if (result.none { it.first == index }) result.add(index to false)
        }
        return result
    }

    private fun refreshLayers() {
        val box = layersBox ?: return
        val dp = resources.displayMetrics.density
        box.removeAllViews()
        orderedLayers().forEach { (index, child) ->
            box.addView(layerRow(index, ShipStore.draft.layers[index], child, dp))
        }
        shipPreview?.layers = ShipStore.draft.layers.toList()
    }

    private fun layerRow(index: Int, layer: ShipLayer, child: Boolean, dp: Float): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        if (child) {
            row.addView(BranchView(this), LinearLayout.LayoutParams(
                (16 * dp).toInt(), (26 * dp).toInt()))
        }

        // 层数
        val (levelView, levelInput) = systemField(
            layer.level.toString(), "", InputType.TYPE_CLASS_NUMBER, 3, true
        ) { v ->
            layer.level = v.toIntOrNull() ?: layer.level
        }
        row.addView(levelView, LinearLayout.LayoutParams(
            (36 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(levelInput, LinearLayout.LayoutParams((1 * dp).toInt(), (1 * dp).toInt()))

        // 名称
        val (nameView, nameInput) = systemField(
            layer.name, "图层名", InputType.TYPE_CLASS_TEXT, 12, false
        ) { layer.name = it }
        row.addView(nameView, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(nameInput, LinearLayout.LayoutParams((1 * dp).toInt(), (1 * dp).toInt()))

        // 类型：纯文字、加粗、无箭头，与绑定控件隔开
        val typeView = plainText(layer.type.label, Color.parseColor("#2B2B2B")) {
            layer.type = layer.type.next()
            refreshLayers()
        }
        typeView.typeface = Typeface.create(pixelFont, Typeface.BOLD)
        row.addView(typeView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (16 * dp).toInt()
        })

        // 绑定（纯文字 + ↓）
        row.addView(plainText("${bindLabel(layer)} ↓", Color.parseColor("#2B2B2B")) {
            showBindMenu(layer, index)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (6 * dp).toInt()
        })

        // 导入图片
        row.addView(iconView(R.drawable.ic_download) { pickImage(index) },
            LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply {
                marginStart = (6 * dp).toInt()
            })

        // 删除
        row.addView(iconView(R.drawable.ic_trash) {
            ShipStore.draft.layers.removeAt(index)
            refreshLayers()
        }, LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply {
            marginStart = (6 * dp).toInt()
        })

        return underlined(row)
    }

    private fun plainText(text: String, color: Int, tap: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            typeface = pixelFont
            includeFontPadding = false
            setTextColor(color)
            isClickable = true
            setOnClickListener { tap() }
        }

    private fun pickImage(index: Int) {
        pendingLayer = index
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_LAYER_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_LAYER_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val index = pendingLayer
        if (index !in ShipStore.draft.layers.indices) return
        val dir = File(filesDir, "layers").apply { mkdirs() }
        val target = File(dir, "layer_${System.currentTimeMillis()}.png")
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.onSuccess {
            ShipStore.draft.layers[index].imagePath = target.absolutePath
            refreshLayers()
        }.onFailure {
            Toast.makeText(this, "导入图片失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindLabel(layer: ShipLayer): String =
        if (layer.bindTo <= 0) "绑定:无" else "绑定:${layer.bindTo}"

    private fun showBindMenu(layer: ShipLayer, index: Int) {
        val layers = ShipStore.draft.layers
        val labels = mutableListOf("无")
        val values = mutableListOf(-1)
        layers.forEachIndexed { other, item ->
            if (other != index) {
                val name = item.name.ifBlank { item.type.label }
                labels.add("图层 ${other + 1}：$name")
                values.add(other + 1)
            }
        }
        showPaperDialog("绑定到哪个图层", null) { column, dialog, panel ->
            labels.forEachIndexed { which, label ->
                column.addView(linkButton(label) {
                    layer.bindTo = values[which]
                    closeDialog(panel, dialog)
                    refreshLayers()
                }, dialogButtonParams())
            }
        }
    }

    // ---------- 保存与载入 ----------

    private fun saveShip(silent: Boolean = false, onSaved: (() -> Unit)? = null) {
        val draft = ShipStore.draft
        if (draft.code.isBlank()) {
            if (!silent) Toast.makeText(this, "请先填写船只代号", Toast.LENGTH_SHORT).show()
            return
        }
        val duplicated = ShipStore.ships.any { it.code == draft.code }
        if (duplicated && !silent) {
            showPaperDialog("已存在同名船只", "存档里已有「${draft.code}」，是否覆盖？") { column, dialog, panel ->
                column.addView(linkButton("覆盖") {
                    closeDialog(panel, dialog)
                    ShipStore.save(draft)
                    Toast.makeText(this, "已覆盖 ${draft.code}", Toast.LENGTH_SHORT).show()
                    onSaved?.invoke()
                }, dialogButtonParams())
                column.addView(linkButton("取消") { closeDialog(panel, dialog) }, dialogButtonParams())
            }
            return
        }
        ShipStore.save(draft)
        if (!silent) Toast.makeText(this, "已保存 ${draft.code}", Toast.LENGTH_SHORT).show()
        onSaved?.invoke()
    }

    // 软盘菜单：以弹窗形式盖在船只编辑器上，不离开编辑器
    private fun showLoadShips() {
        val dp = resources.displayMetrics.density
        val dialog = Dialog(this)
        val panel = paperPanel()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(this).apply {
            text = "载入船只"
            textSize = 24f
            typeface = pixelFont
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#2B2B2B"))
            setPadding(0, 0, 0, (8 * dp).toInt())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(listBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        fun renderList() {
            listBox.removeAllViews()
            if (ShipStore.ships.isEmpty()) {
                listBox.addView(TextView(this).apply {
                    text = "暂无已保存的船只"
                    textSize = 16f
                    typeface = pixelFont
                    setTextColor(Color.parseColor("#5A5A5A"))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            } else {
                ShipStore.ships.forEach { ship -> listBox.addView(shipEntry(ship, dp, dialog)) }
            }
        }
        renderList()

        column.addView(linkButton("保存当前船只") {
            saveShip(onSaved = { renderList() })
        }, dialogButtonParams())
        column.addView(linkButton("关闭") { closeDialog(panel, dialog) }, dialogButtonParams())

        panel.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            min((resources.displayMetrics.widthPixels - 40 * dp).toInt(), (520 * dp).toInt()),
            LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()
        dialog.setCanceledOnTouchOutside(false)
        popIn(panel)
    }

    private fun draftDirty(): Boolean {
        val d = ShipStore.draft
        return d.code.isNotBlank() || d.maxSpeed.isNotBlank() ||
            d.tonnage.isNotBlank() || d.weight.isNotBlank() ||
            d.periodFrom.isNotBlank() || d.periodTo.isNotBlank() || d.layers.isNotEmpty()
    }

    // 读取船只：有未保存内容时先询问
    private fun confirmLoad(ship: Ship, dialog: Dialog) {
        if (!draftDirty()) {
            loadShip(ship, dialog)
            return
        }
        showPaperDialog("当前内容尚未保存", "是否先保存当前船只，再读取？") { column, inner, panel ->
            column.addView(linkButton("保存并读取") {
                closeDialog(panel, inner)
                saveShip(silent = true)
                loadShip(ship, dialog)
            }, dialogButtonParams())
            column.addView(linkButton("直接读取") {
                closeDialog(panel, inner)
                loadShip(ship, dialog)
            }, dialogButtonParams())
            column.addView(linkButton("取消") { closeDialog(panel, inner) }, dialogButtonParams())
        }
    }

    private fun loadShip(ship: Ship, dialog: Dialog) {
        ShipStore.draft = ship.duplicate()
        dialog.dismiss()
        showShipEditor()
    }

    // 删除船只：先确认
    private fun confirmDelete(ship: Ship, parent: Dialog) {
        showPaperDialog("删除船只", "确定删除「${ship.code}」？") { column, inner, panel ->
            column.addView(linkButton("删除") {
                closeDialog(panel, inner)
                ShipStore.delete(ship)
                parent.dismiss()
                showLoadShips()
            }, dialogButtonParams())
            column.addView(linkButton("取消") { closeDialog(panel, inner) }, dialogButtonParams())
        }
    }

    private fun shipEntry(ship: Ship, dp: Float, dialog: Dialog): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        row.addView(ShipPreviewView(this).apply {
            layers = ship.layers
            setBackgroundColor(Color.TRANSPARENT)
        }, LinearLayout.LayoutParams((72 * dp).toInt(), (48 * dp).toInt()))

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
            textSize = 12f
            typeface = pixelFont
            setTextColor(Color.parseColor("#5A5A5A"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val extras = listOfNotNull(
            ship.tonnage.takeIf { it.isNotBlank() }?.let { "吨位 $it" }
        )
        if (extras.isNotEmpty()) {
            info.addView(TextView(this).apply {
                text = extras.joinToString("  ")
                textSize = 12f
                typeface = pixelFont
                setTextColor(Color.parseColor("#5A5A5A"))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        row.addView(info, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (10 * dp).toInt()
        })

        row.addView(iconView(R.drawable.ic_edit) { confirmLoad(ship, dialog) },
            LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply {
                marginEnd = (6 * dp).toInt()
            })

        row.addView(iconView(R.drawable.ic_trash) {
            confirmDelete(ship, dialog)
        }, LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()))
        return row
    }

    // ---------- 按钮与工具 ----------

    private fun menuButton(label: String): PixelButton = PixelButton(this).apply {
        text = label
        textSize = 16f
        typeface = pixelFont
        setTextColor(Color.parseColor("#16191F"))
        val padH = (12 * resources.displayMetrics.density).toInt()
        val padV = (6 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)
    }

    /** 纸张页面上的操作按钮：比正文大一号的蓝色文字 + "→" */
    private fun linkButton(label: String, tap: () -> Unit): TextView = TextView(this).apply {
        text = "$label →"
        textSize = 18f
        typeface = pixelFont
        includeFontPadding = false
        setTextColor(Color.parseColor("#1F5FA8"))
        isClickable = true
        setOnClickListener { tap() }
    }

    private fun iconView(resId: Int, tap: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(resId)
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = true
        setOnClickListener { tap() }
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
            // 保持默认的 decorFits=true，ADJUST_RESIZE 才能在输入法弹出时把窗口顶起来
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
        private const val REQUEST_LAYER_IMAGE = 1001
        private val RULE_COLOR = Color.parseColor("#9DB7D8")
    }

}
