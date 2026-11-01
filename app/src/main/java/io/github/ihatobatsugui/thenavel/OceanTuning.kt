package io.github.ihatobatsugui.thenavel

/**
 * 海面泡沫的可调参数。开发者模式下会直接改这里的值，OceanView 每帧读取，即时生效。
 */
object OceanTuning {
    // 风向漂移速度（格/帧）
    var windX = 0.38f
    var windY = 0.07f
    // 泡沫生成强度与发射带阈值（阈值越高越集聚、总量越少）
    var sourceGain = 0.0040f
    var threshold = 0.74f
    // 扩散与衰减
    var diffuse = 0.002f
    var decay = 0.995f

    fun reset() {
        windX = 0.38f
        windY = 0.07f
        sourceGain = 0.0040f
        threshold = 0.74f
        diffuse = 0.002f
        decay = 0.995f
    }
}
