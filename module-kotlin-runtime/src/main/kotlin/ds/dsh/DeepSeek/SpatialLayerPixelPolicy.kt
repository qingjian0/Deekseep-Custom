package ds.dsh.DeepSeek

import kotlin.math.floor

/**
 * Pure pixel-plane transformations used by SpatialLayerCache.
 *
 * Keep integer arithmetic, float interpolation and alpha handling aligned with the original
 * Java implementation. Bitmap decoding, segmentation and PNG I/O remain in Java.
 */
object SpatialLayerPixelPolicy {
    @JvmStatic
    fun buildMidgroundPixels(pixels: IntArray, keepMask: ByteArray): IntArray {
        val count = minOf(pixels.size, keepMask.size)
        return IntArray(count) { index ->
            val sourceColor = pixels[index]
            val keep = keepMask[index].toInt() and 0xFF
            val sourceAlpha = sourceColor ushr 24
            val midAlpha = sourceAlpha * keep / 255
            (sourceColor and 0x00FFFFFF) or (midAlpha shl 24)
        }
    }

    @JvmStatic
    fun buildBackplatePixels(
        pixels: IntArray,
        keepMask: ByteArray,
        width: Int,
        height: Int
    ): IntArray {
        val backgroundField = buildBackgroundField(pixels, keepMask, width, height)
        val count = minOf(pixels.size, keepMask.size, backgroundField.size)
        return IntArray(count) { index ->
            val keep = keepMask[index].toInt() and 0xFF
            if (keep == 0) pixels[index]
            else blend(pixels[index], backgroundField[index], keep)
        }
    }

    @JvmStatic
    fun buildBackgroundField(
        pixels: IntArray,
        keepMask: ByteArray,
        width: Int,
        height: Int
    ): IntArray {
        val columns = maxOf(8, minOf(48, (width + 31) / 32))
        val rows = maxOf(8, minOf(48, (height + 31) / 32))
        val cells = columns * rows
        val redSums = LongArray(cells)
        val greenSums = LongArray(cells)
        val blueSums = LongArray(cells)
        val samples = IntArray(cells)
        var globalRed = 0L
        var globalGreen = 0L
        var globalBlue = 0L
        var globalSamples = 0

        for (y in 0 until height) {
            val cellY = minOf(rows - 1, y * rows / height)
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                if ((keepMask[index].toInt() and 0xFF) > 16) continue
                val color = pixels[index]
                val cell = cellY * columns + minOf(columns - 1, x * columns / width)
                val r = red(color)
                val g = green(color)
                val b = blue(color)
                redSums[cell] += r.toLong()
                greenSums[cell] += g.toLong()
                blueSums[cell] += b.toLong()
                samples[cell]++
                globalRed += r.toLong()
                globalGreen += g.toLong()
                globalBlue += b.toLong()
                globalSamples++
            }
        }
        val fallback = if (globalSamples == 0) 0xFF202020.toInt() else rgb(
            (globalRed / globalSamples).toInt(),
            (globalGreen / globalSamples).toInt(),
            (globalBlue / globalSamples).toInt()
        )
        val colors = IntArray(cells)
        for (cell in 0 until cells) {
            if (samples[cell] > 0) {
                colors[cell] = rgb(
                    (redSums[cell] / samples[cell]).toInt(),
                    (greenSums[cell] / samples[cell]).toInt(),
                    (blueSums[cell] / samples[cell]).toInt()
                )
                continue
            }
            val gridRow = cell / columns
            val column = cell % columns
            var left = column - 1
            while (left >= 0 && samples[gridRow * columns + left] == 0) left--
            var right = column + 1
            while (right < columns && samples[gridRow * columns + right] == 0) right++
            colors[cell] = when {
                left >= 0 && right < columns -> {
                    val fraction = (column - left) / (right - left).toFloat()
                    mix(
                        averageColor(redSums, greenSums, blueSums,
                            gridRow * columns + left, fallback),
                        averageColor(redSums, greenSums, blueSums, samples,
                            gridRow * columns + right, fallback),
                        fraction
                    )
                }
                left >= 0 -> averageColor(
                    redSums, greenSums, blueSums, samples, gridRow * columns + left, fallback)
                right < columns -> averageColor(
                    red, green, blue, samples, gridRow * columns + right, fallback)
                else -> fallback
            }
        }

        val field = IntArray(width * height)
        for (y in 0 until height) {
            val gridY = (y + 0.5f) * rows / height - 0.5f
            val top = maxOf(0, minOf(rows - 1, floor(gridY.toDouble()).toInt()))
            val bottom = minOf(rows - 1, top + 1)
            val fy = maxOf(0f, minOf(1f, gridY - top))
            val row = y * width
            for (x in 0 until width) {
                val gridX = (x + 0.5f) * columns / width - 0.5f
                val left = maxOf(0, minOf(columns - 1, floor(gridX.toDouble()).toInt()))
                val right = minOf(columns - 1, left + 1)
                val fx = maxOf(0f, minOf(1f, gridX - left))
                val topColor = mix(colors[top * columns + left],
                    colors[top * columns + right], fx)
                val bottomColor = mix(colors[bottom * columns + left],
                    colors[bottom * columns + right], fx)
                field[row + x] = mix(topColor, bottomColor, fy)
            }
        }
        return field
    }

    private fun averageColor(
        red: LongArray, green: LongArray, blue: LongArray,
        samples: IntArray, index: Int, fallback: Int
    ): Int {
        val count = samples[index]
        if (count <= 0) return fallback
        return rgb(
            (red[index] / count).toInt(),
            (green[index] / count).toInt(),
            (blue[index] / count).toInt()
        )
    }

    private fun blend(original: Int, replacement: Int, amount: Int): Int {
        val fraction = maxOf(0f, minOf(1f, amount / 255f))
        val mixed = mix(original, replacement, fraction)
        return (original and 0xFF000000.toInt()) or (mixed and 0x00FFFFFF)
    }

    private fun mix(first: Int, second: Int, fraction: Float): Int {
        val f = maxOf(0f, minOf(1f, fraction))
        val inverse = 1f - f
        return rgb(
            Math.round(red(first) * inverse + red(second) * f),
            Math.round(green(first) * inverse + green(second) * f),
            Math.round(blue(first) * inverse + blue(second) * f)
        )
    }

    private fun red(color: Int): Int = (color ushr 16) and 0xFF
    private fun green(color: Int): Int = (color ushr 8) and 0xFF
    private fun blue(color: Int): Int = color and 0xFF

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        0xFF000000.toInt() or ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or (blue and 0xFF)
}
