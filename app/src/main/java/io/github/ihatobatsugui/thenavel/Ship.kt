package io.github.ihatobatsugui.thenavel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LayerType(val label: String) {
    HULL("船体"),
    TURRET("炮塔"),
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
    var imagePath: String = ""
)

data class Ship(
    var code: String = "",
    var maxSpeed: String = "",
    var periodFrom: String = "",
    var periodTo: String = "",
    var layers: MutableList<ShipLayer> = mutableListOf(),
    var modifiedAt: String = ""
) {
    fun duplicate(): Ship = Ship(
        code = code,
        maxSpeed = maxSpeed,
        periodFrom = periodFrom,
        periodTo = periodTo,
        layers = layers.map { it.copy() }.toMutableList(),
        modifiedAt = modifiedAt
    )
}

/** 会话内的船只仓库：编辑中的草稿和已保存的船只都放在这里，退出界面后内容不丢。 */
object ShipStore {

    val ships = mutableListOf<Ship>()
    var draft: Ship = Ship()

    fun now(): String = SimpleDateFormat("yyyy.MM.DD", Locale.US).format(Date())

    fun save(draft: Ship) {
        if (draft.code.isBlank()) return
        draft.modifiedAt = now()
        val existing = ships.indexOfFirst { it.code == draft.code }
        if (existing >= 0) ships[existing] = draft.duplicate() else ships.add(draft.duplicate())
    }

    fun delete(ship: Ship) {
        ships.removeAll { it.code == ship.code }
    }
}
