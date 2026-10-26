package mx.lumenforge.photo

import kotlin.math.abs

data class ProcessingParams(
    val denoise: Int = 20,
    val sharpness: Int = 25,
    val saturation: Int = 50,
    val warmth: Int = 50
)

object PhotoAlgorithms {
    fun process(input: IntArray, width: Int, height: Int, params: ProcessingParams): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE)
        require(input.size == width * height)
        require(listOf(params.denoise, params.sharpness, params.saturation, params.warmth)
            .all { it in 0..100 })
        var pix = input.copyOf()
        if (params.denoise > 0 && width > 2 && height > 2)
            pix = denoise(pix, width, height, params.denoise)
        if (params.sharpness > 0 && width > 2 && height > 2)
            pix = sharpen(pix, width, height, params.sharpness)
        if (params.saturation != 50 || params.warmth != 50)
            pix = color(pix, params.saturation, params.warmth)
        return pix
    }

    private fun r(x: Int) = (x ushr 16) and 255
    private fun g(x: Int) = (x ushr 8) and 255
    private fun b(x: Int) = x and 255
    private fun lum(x: Int) = (77 * r(x) + 150 * g(x) + 29 * b(x)) shr 8
    private fun clip(x: Int) = x.coerceIn(0, 255)
    private fun argb(src: Int, red: Int, green: Int, blue: Int) =
        (src and -0x1000000) or (clip(red) shl 16) or (clip(green) shl 8) or clip(blue)

    private fun denoise(input: IntArray, w: Int, h: Int, amount: Int): IntArray {
        val dst = input.copyOf()
        val threshold = 8 + amount * 34 / 100
        val strength = amount * 0.0085f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val index = y * w + x
            val center = input[index]
            var red = 0
            var green = 0
            var blue = 0
            var total = 0
            for (dy in -1..1) for (dx in -1..1) {
                val neighbor = input[index + dy * w + dx]
                if (abs(lum(neighbor) - lum(center)) <= threshold) {
                    val weight = (if (dx == 0) 2 else 1) * (if (dy == 0) 2 else 1)
                    red += r(neighbor) * weight
                    green += g(neighbor) * weight
                    blue += b(neighbor) * weight
                    total += weight
                }
            }
            if (total > 0) dst[index] = argb(center,
                (r(center) + (red / total - r(center)) * strength).toInt(),
                (g(center) + (green / total - g(center)) * strength).toInt(),
                (b(center) + (blue / total - b(center)) * strength).toInt())
        }
        return dst
    }

    private fun sharpen(src: IntArray, w: Int, h: Int, amount: Int): IntArray {
        val dst = src.copyOf()
        val gain = amount * 1.35f / 100
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val c = src[i]
            fun blur(channel: (Int) -> Int): Int =
                (channel(src[i - w - 1]) + 2 * channel(src[i - w]) +
                channel(src[i - w + 1]) + 2 * channel(src[i - 1]) +
                4 * channel(c) + 2 * channel(src[i + 1]) +
                channel(src[i + w - 1]) + 2 * channel(src[i + w]) +
                channel(src[i + w + 1])) / 16
            val br = blur(::r)
            val bg = blur(::g)
            val bb = blur(::b)
            if (abs(lum(c) - ((77 * br + 150 * bg + 29 * bb) shr 8)) >= 2)
                dst[i] = argb(c,
                    (r(c) + (r(c) - br) * gain).toInt(),
                    (g(c) + (g(c) - bg) * gain).toInt(),
                    (b(c) + (b(c) - bb) * gain).toInt())
        }
        return dst
    }

    private fun color(src: IntArray, saturation: Int, warmth: Int): IntArray {
        val out = IntArray(src.size)
        val gain = 1f + (saturation - 50) * 0.009f
        val warm = (warmth - 50) * 0.4f
        for (i in src.indices) {
            val p = src[i]
            val lum = lum(p)
            out[i] = argb(p,
                (lum + (r(p) - lum) * gain + warm).toInt(),
                (lum + (g(p) - lum) * gain + warm * 0.1f).toInt(),
                (lum + (b(p) - lum) * gain - warm).toInt())
        }
        return out
    }
}
