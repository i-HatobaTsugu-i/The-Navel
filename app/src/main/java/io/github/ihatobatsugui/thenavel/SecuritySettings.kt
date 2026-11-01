package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.content.SharedPreferences

/**
 * 安全审查开关。默认全开；关掉某项后 ShipDataGuard 对应检查就不再执行。
 * 总开关关闭时等于完全不审查外来数据。
 */
object SecuritySettings {

    private const val PREFS = "security"
    private var prefs: SharedPreferences? = null

    var enabled = true
    var jsonSizeLimit = true
    var countLimit = true
    var numberCheck = true
    var depthLimit = true
    var imageCheck = true
    var pathWhitelist = true

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", true)
        jsonSizeLimit = p.getBoolean("jsonSize", true)
        countLimit = p.getBoolean("count", true)
        numberCheck = p.getBoolean("numbers", true)
        depthLimit = p.getBoolean("depth", true)
        imageCheck = p.getBoolean("images", true)
        pathWhitelist = p.getBoolean("paths", true)
    }

    fun persist() {
        prefs?.edit()
            ?.putBoolean("enabled", enabled)
            ?.putBoolean("jsonSize", jsonSizeLimit)
            ?.putBoolean("count", countLimit)
            ?.putBoolean("numbers", numberCheck)
            ?.putBoolean("depth", depthLimit)
            ?.putBoolean("images", imageCheck)
            ?.putBoolean("paths", pathWhitelist)
            ?.apply()
    }

    /** 某项检查是否生效 */
    fun isOn(check: Check): Boolean = enabled && when (check) {
        Check.JSON_SIZE -> jsonSizeLimit
        Check.COUNT -> countLimit
        Check.NUMBER -> numberCheck
        Check.DEPTH -> depthLimit
        Check.IMAGE -> imageCheck
        Check.PATH -> pathWhitelist
    }

    enum class Check { JSON_SIZE, COUNT, NUMBER, DEPTH, IMAGE, PATH }
}
