package io.github.ihatobatsugui.thenavel

/**
 * 海面泡沫的可调参数。开发者模式下会直接改这里的值，OceanView 每帧读取，即时生效。
 */
object OceanTuning {
    // 风向漂移速度（格/帧）
    var windX = 0.12f
    var windY = 0.04f
    // 泡沫生成强度与发射带阈值（阈值越高越集聚、总量越少）
    var sourceGain = 0.05f
    var threshold = 0.78f
    // 扩散与衰减
    var diffuse = 0f
    var decay = 0.99f
    // 渲染：浓度上限与显示倍率
    var foamCap = 1.0f
    var renderGain = 3.0f

    fun reset() {
        windX = 0.12f
        windY = 0.04f
        sourceGain = 0.05f
        threshold = 0.78f
        diffuse = 0f
        decay = 0.99f
        foamCap = 1.0f
        renderGain = 3.0f
    }
}
