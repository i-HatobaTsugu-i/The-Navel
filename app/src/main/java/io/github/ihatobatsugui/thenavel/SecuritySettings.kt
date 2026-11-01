package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.content.SharedPreferences

/**
 * 安全审查开关与阈值。默认全开；关掉某项后 ShipDataGuard 对应检查就不再执行。
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

    // 可手填的阈值
    var maxJsonMb = 8
    var maxShips = 300
    var maxLayers = 96
    var maxTextLen = 64
    var maxCoord = 4096
    var maxDepth = 8
    var maxImageMb = 8
    var maxImageDim = 4096

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
        maxJsonMb = p.getInt("maxJsonMb", 8)
        maxShips = p.getInt("maxShips", 300)
        maxLayers = p.getInt("maxLayers", 96)
        maxTextLen = p.getInt("maxTextLen", 64)
        maxCoord = p.getInt("maxCoord", 4096)
        maxDepth = p.getInt("maxDepth", 8)
        maxImageMb = p.getInt("maxImageMb", 8)
        maxImageDim = p.getInt("maxImageDim", 4096)
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
            ?.putInt("maxJsonMb", maxJsonMb)
            ?.putInt("maxShips", maxShips)
            ?.putInt("maxLayers", maxLayers)
            ?.putInt("maxTextLen", maxTextLen)
            ?.putInt("maxCoord", maxCoord)
            ?.putInt("maxDepth", maxDepth)
            ?.putInt("maxImageMb", maxImageMb)
            ?.putInt("maxImageDim", maxImageDim)
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

    fun jsonBytes(): Long = maxJsonMb.coerceAtLeast(1).toLong() * 1024 * 1024
    fun imageBytes(): Long = maxImageMb.coerceAtLeast(1).toLong() * 1024 * 1024
    fun coord(): Float = maxCoord.coerceAtLeast(1).toFloat()

    enum class Check { JSON_SIZE, COUNT, NUMBER, DEPTH, IMAGE, PATH }
}
