package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 外来船只数据的防线：一切从磁盘读回来的内容都当作不可信数据。
 * 只做"限额 + 清洗 + 丢弃非法项"，不做加密或签名。
 */
object ShipDataGuard {

    const val MAX_SHIPS = 300
    const val MAX_LAYERS = 96
    const val MAX_TEXT = 64
    const val MAX_LEVEL = 999
    const val MAX_DEPTH = 8
    const val MAX_COORD = 4096f
    const val MAX_JSON_BYTES = 8L * 1024 * 1024
    const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    const val MAX_IMAGE_DIM = 4096

    fun text(raw: String): String {
        val cleaned = raw.filter { it >= ' ' || it == '\t' }
        return if (SecuritySettings.isOn(SecuritySettings.Check.COUNT)) cleaned.take(MAX_TEXT) else cleaned
    }

    fun coord(value: Float): Float {
        if (!SecuritySettings.isOn(SecuritySettings.Check.NUMBER)) return value
        return if (value.isFinite()) value.coerceIn(-MAX_COORD, MAX_COORD) else 0f
    }

    /** 校验并清洗单艘船；代号为空视为不可用。 */
    fun sanitize(raw: Ship): Ship? {
        val code = text(raw.code).trim()
        if (code.isEmpty()) return null

        val countOn = SecuritySettings.isOn(SecuritySettings.Check.COUNT)
        val numberOn = SecuritySettings.isOn(SecuritySettings.Check.NUMBER)
        val source = if (countOn) raw.layers.take(MAX_LAYERS) else raw.layers

        val layers = source.mapIndexed { index, layer ->
            val bind = layer.bindTo
            ShipLayer(
                name = text(layer.name),
                type = layer.type,
                level = if (numberOn) layer.level.coerceIn(0, MAX_LEVEL) else layer.level,
                bindTo = if (!numberOn) bind
                else if (bind in 1..raw.layers.size && bind - 1 != index) bind else -1,
                imagePath = if (countOn && layer.imagePath.length > 512) "" else layer.imagePath,
                offsetX = coord(layer.offsetX),
                offsetY = coord(layer.offsetY),
                pivotX = coord(layer.pivotX),
                pivotY = coord(layer.pivotY)
            )
        }

        // 父链过长或成环的图层直接解绑，避免递归过深或渲染异常
        if (SecuritySettings.isOn(SecuritySettings.Check.DEPTH)) {
            layers.forEachIndexed { index, layer ->
                if (layer.bindTo > 0 && chainDepth(layers, index) > MAX_DEPTH) layer.bindTo = -1
            }
        }

        return Ship(
            code = code,
            maxSpeed = text(raw.maxSpeed),
            tonnage = text(raw.tonnage),
            weight = text(raw.weight),
            periodFrom = text(raw.periodFrom),
            periodTo = text(raw.periodTo),
            layers = layers.toMutableList(),
            modifiedAt = text(raw.modifiedAt)
        )
    }

    private fun chainDepth(layers: List<ShipLayer>, start: Int): Int {
        var depth = 0
        var current = start
        val seen = HashSet<Int>()
        while (depth <= layers.size) {
            val bind = layers.getOrNull(current)?.bindTo ?: return depth
            if (bind <= 0 || bind > layers.size) return depth
            val parent = bind - 1
            if (!seen.add(parent)) return Int.MAX_VALUE
            depth++
            current = parent
        }
        return Int.MAX_VALUE
    }

    /** 图片路径必须落在应用自己的图层目录里，防止指向任意文件。 */
    fun isInside(dir: File, path: String): Boolean = runCatching {
        val base = dir.canonicalFile
        val target = File(path).canonicalFile
        target.path.startsWith(base.path + File.separator)
    }.getOrDefault(false)

    /**
     * 安全解码图层图片：路径白名单 + 文件大小上限 + 尺寸上限 + 降采样。
     * 解不出来就返回 null，调用方跳过该图层。
     */
    fun decodeLayer(context: Context, path: String): Bitmap? {
        if (path.isBlank()) return null
        val dir = File(context.filesDir, "layers")
        if (SecuritySettings.isOn(SecuritySettings.Check.PATH) && !isInside(dir, path)) return null
        val file = File(path)
        if (!file.exists()) return null

        val imageOn = SecuritySettings.isOn(SecuritySettings.Check.IMAGE)
        if (imageOn && file.length() > MAX_IMAGE_BYTES) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (imageOn && (bounds.outWidth > MAX_IMAGE_DIM || bounds.outHeight > MAX_IMAGE_DIM)) return null

        val options = BitmapFactory.Options().apply {
            if (imageOn) inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1024)
        }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var size = 1
        while (width / size > target || height / size > target) size *= 2
        return size
    }
}
