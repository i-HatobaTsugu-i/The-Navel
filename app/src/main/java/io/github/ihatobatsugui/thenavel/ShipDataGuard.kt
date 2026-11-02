package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 外来船只数据的防线：一切从磁盘读回来的内容都当作不可信数据。
 * 阈值来自 SecuritySettings（可在「设置 → 安全性」里改或关掉对应检查）。
 * 唯一不受开关影响的是"环形绑定"——它属于数据本身的错误，必须断开。
 */
object ShipDataGuard {

    fun text(raw: String): String {
        val cleaned = raw.filter { it >= ' ' || it == '\t' }
        return if (SecuritySettings.isOn(SecuritySettings.Check.COUNT)) {
            cleaned.take(SecuritySettings.maxTextLen.coerceAtLeast(1))
        } else {
            cleaned
        }
    }

    fun coord(value: Float): Float {
        if (!SecuritySettings.isOn(SecuritySettings.Check.NUMBER)) return value
        val limit = SecuritySettings.coord()
        return if (value.isFinite()) value.coerceIn(-limit, limit) else 0f
    }

    /** 校验并清洗单艘船；代号为空或结构不可用返回 null。 */
    fun sanitize(raw: Ship): Ship? {
        val code = text(raw.code).trim()
        if (code.isEmpty()) return null

        val countOn = SecuritySettings.isOn(SecuritySettings.Check.COUNT)
        val numberOn = SecuritySettings.isOn(SecuritySettings.Check.NUMBER)
        val layerLimit = if (countOn) SecuritySettings.maxLayers.coerceAtLeast(1) else Int.MAX_VALUE
        val source = raw.layers.take(layerLimit)

        val layers = source.mapIndexed { index, layer ->
            val bind = layer.bindTo
            ShipLayer(
                name = text(layer.name),
                type = layer.type,
                level = if (numberOn) layer.level.coerceIn(0, 9999) else layer.level,
                bindTo = if (!numberOn) bind
                else if (bind in 1..raw.layers.size && bind - 1 != index) bind else -1,
                imagePath = if (countOn && layer.imagePath.length > 1024) "" else layer.imagePath,
                offsetX = coord(layer.offsetX),
                offsetY = coord(layer.offsetY),
                pivotX = coord(layer.pivotX),
                pivotY = coord(layer.pivotY),
                windSpeed = coord(layer.windSpeed),
                sailSpeed = coord(layer.sailSpeed)
            )
        }

        // 环形绑定一律断开（不提供关闭开关）
        layers.forEachIndexed { index, layer ->
            if (layer.bindTo > 0 && hasCycle(layers, index)) layer.bindTo = -1
        }
        // 过深的父链按开关处理
        if (SecuritySettings.isOn(SecuritySettings.Check.DEPTH)) {
            val maxDepth = SecuritySettings.maxDepth.coerceAtLeast(1)
            layers.forEachIndexed { index, layer ->
                if (layer.bindTo > 0 && chainDepth(layers, index) > maxDepth) layer.bindTo = -1
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

    /** 沿父链向上走，若回到走过的节点即为环。 */
    fun hasCycle(layers: List<ShipLayer>, start: Int): Boolean {
        val seen = HashSet<Int>()
        var current = start
        while (true) {
            if (!seen.add(current)) return true
            val bind = layers.getOrNull(current)?.bindTo ?: return false
            if (bind <= 0 || bind > layers.size) return false
            current = bind - 1
        }
    }

    fun chainDepth(layers: List<ShipLayer>, start: Int): Int {
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

    /** candidate 是否位于 root 的子树中（沿父链向上能找到 root）。 */
    fun isInSubtree(layers: List<ShipLayer>, root: Int, candidate: Int): Boolean {
        var current = candidate
        val seen = HashSet<Int>()
        while (current in layers.indices && seen.add(current)) {
            val bind = layers[current].bindTo
            if (bind <= 0 || bind > layers.size) return false
            val parent = bind - 1
            if (parent == root) return true
            current = parent
        }
        return false
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
        if (imageOn && file.length() > SecuritySettings.imageBytes()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (imageOn && (bounds.outWidth > SecuritySettings.maxImageDim.coerceAtLeast(1) ||
                bounds.outHeight > SecuritySettings.maxImageDim.coerceAtLeast(1))
        ) return null

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
