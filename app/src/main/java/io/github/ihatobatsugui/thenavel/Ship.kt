package io.github.ihatobatsugui.thenavel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

enum class LayerType(val label: String) {
    HULL("船体"),
    TURRET("炮塔"),
    FUNNEL("烟囱"),
    SAIL("风帆"),
    BOAT("交通艇"),
    DAVIT("吊艇架"),
    DECOR("装饰物");

    fun next(): LayerType {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}

data class ShipLayer(
    var name: String = "",
    var type: LayerType = LayerType.HULL,
    // 图层所处层数（可填写）
    var level: Int = 1,
    // 绑定的图层序号（1 起），-1 表示不绑定
    var bindTo: Int = -1,
    // 导入的图层图片路径（复制到应用私有目录后的绝对路径）
    var imagePath: String = "",
    // 图层位移与转轴（单位：dp，相对 432 设计画布）
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var pivotX: Float = 0f,
    var pivotY: Float = 0f,
    // 风帆：标准风速与该帆可提供的最大速度
    var windSpeed: Float = 0f,
    var sailSpeed: Float = 0f
)

data class Ship(
    var code: String = "",
    var maxSpeed: String = "",
    var tonnage: String = "",
    var weight: String = "",
    var periodFrom: String = "",
    var periodTo: String = "",
    var layers: MutableList<ShipLayer> = mutableListOf(),
    var modifiedAt: String = ""
) {
    fun duplicate(): Ship = Ship(
        code = code,
        maxSpeed = maxSpeed,
        tonnage = tonnage,
        weight = weight,
        periodFrom = periodFrom,
        periodTo = periodTo,
        layers = layers.map { it.copy() }.toMutableList(),
        modifiedAt = modifiedAt
    )
}

/**
 * 船只仓库：数据落盘到 filesDir/ships.json，应用更新不会丢。
 * 读取时对缺失字段全部给默认值，兼容旧版本写下的存档。
 */
object ShipStore {

    private const val FILE_NAME = "ships.json"
    private var context: Context? = null

    val ships = mutableListOf<Ship>()
    var draft: Ship = Ship()

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    private fun file(): File? = context?.let { File(it.filesDir, FILE_NAME) }

    private fun load() {
        val f = file() ?: return
        if (!f.exists()) return
        if (SecuritySettings.isOn(SecuritySettings.Check.JSON_SIZE) &&
            f.length() > SecuritySettings.jsonBytes()
        ) {
            // 超大存档不解析，改名留档后用空存档启动，避免 OOM
            runCatching { f.renameTo(File(f.parentFile, "ships.oversize.json")) }
            return
        }
        val parsed = mutableListOf<Ship>()
        runCatching {
            val root = JSONObject(f.readText())
            val array = root.optJSONArray("ships") ?: JSONArray()
            for (i in 0 until array.length()) {
                if (SecuritySettings.isOn(SecuritySettings.Check.COUNT) &&
                    parsed.size >= SecuritySettings.maxShips.coerceAtLeast(1)
                ) break
                val o = array.optJSONObject(i) ?: continue
                // 单艘船解析失败不影响其它船
                runCatching {
                    val layers = mutableListOf<ShipLayer>()
                    val layerArray = o.optJSONArray("layers") ?: JSONArray()
                    for (k in 0 until minOf(layerArray.length(), SecuritySettings.maxLayers.coerceAtLeast(1))) {
                        val lo = layerArray.optJSONObject(k) ?: continue
                        layers.add(
                            ShipLayer(
                                name = lo.optString("name", ""),
                                type = runCatching {
                                    LayerType.valueOf(lo.optString("type", LayerType.HULL.name))
                                }.getOrDefault(LayerType.HULL),
                                level = lo.optInt("level", 1),
                                bindTo = lo.optInt("bindTo", -1),
                                imagePath = lo.optString("imagePath", ""),
                                offsetX = lo.optDouble("offsetX", 0.0).toFloat(),
                                offsetY = lo.optDouble("offsetY", 0.0).toFloat(),
                                pivotX = lo.optDouble("pivotX", 0.0).toFloat(),
                                pivotY = lo.optDouble("pivotY", 0.0).toFloat(),
                                windSpeed = lo.optDouble("windSpeed", 0.0).toFloat(),
                                sailSpeed = lo.optDouble("sailSpeed", 0.0).toFloat()
                            )
                        )
                    }
                    val ship = Ship(
                        code = o.optString("code", ""),
                        maxSpeed = o.optString("maxSpeed", ""),
                        tonnage = o.optString("tonnage", ""),
                        weight = o.optString("weight", ""),
                        periodFrom = o.optString("periodFrom", ""),
                        periodTo = o.optString("periodTo", ""),
                        layers = layers,
                        modifiedAt = normalizeDate(o.optString("modifiedAt", ""))
                    )
                    ShipDataGuard.sanitize(ship)?.let { parsed.add(it) }
                }
            }
        }
        ships.clear()
        ships.addAll(parsed)
    }

    private fun persist() {
        val f = file() ?: return
        runCatching {
            val array = JSONArray()
            ships.forEach { ship ->
                val o = JSONObject()
                o.put("code", ship.code)
                o.put("maxSpeed", ship.maxSpeed)
                o.put("tonnage", ship.tonnage)
                o.put("weight", ship.weight)
                o.put("periodFrom", ship.periodFrom)
                o.put("periodTo", ship.periodTo)
                o.put("modifiedAt", ship.modifiedAt)
                val layerArray = JSONArray()
                ship.layers.forEach { layer ->
                    val lo = JSONObject()
                    lo.put("name", layer.name)
                    lo.put("type", layer.type.name)
                    lo.put("level", layer.level)
                    lo.put("bindTo", layer.bindTo)
                    lo.put("imagePath", layer.imagePath)
                    lo.put("offsetX", layer.offsetX.toDouble())
                    lo.put("offsetY", layer.offsetY.toDouble())
                    lo.put("pivotX", layer.pivotX.toDouble())
                    lo.put("pivotY", layer.pivotY.toDouble())
                    lo.put("windSpeed", layer.windSpeed.toDouble())
                    lo.put("sailSpeed", layer.sailSpeed.toDouble())
                    layerArray.put(lo)
                }
                o.put("layers", layerArray)
                array.put(o)
            }
            val json = JSONObject().put("ships", array).toString()
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    fun now(): String = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())

    // 旧版本把日期格式化成了「年.月.年内第几天」（DD），这里修正成「年.月.日」
    private fun normalizeDate(raw: String): String {
        val parts = raw.split('.')
        if (parts.size != 3) return raw
        val year = parts[0].toIntOrNull() ?: return raw
        val month = parts[1].toIntOrNull() ?: return raw
        val day = parts[2].toIntOrNull() ?: return raw
        if (parts[2].length != 3) return raw
        return runCatching {
            val date = LocalDate.of(year, 1, 1).plusDays(day.toLong() - 1)
            String.format(Locale.US, "%04d.%02d.%02d", date.year, date.monthValue, date.dayOfMonth)
        }.getOrDefault(raw)
    }

    fun save(draft: Ship): Boolean {
        val sanitized = ShipDataGuard.sanitize(draft) ?: return false
        sanitized.modifiedAt = now()
        val existing = ships.indexOfFirst { it.code == sanitized.code }
        if (existing >= 0) {
            ships[existing] = sanitized.duplicate()
        } else {
            if (SecuritySettings.isOn(SecuritySettings.Check.COUNT) &&
                ships.size >= SecuritySettings.maxShips.coerceAtLeast(1)
            ) return false
            ships.add(sanitized.duplicate())
        }
        persist()
        return true
    }

    fun delete(ship: Ship) {
        ships.removeAll { it.code == ship.code }
        persist()
    }
}
