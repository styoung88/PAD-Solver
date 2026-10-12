package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.example.phoneapp.board.OrbColor

internal class ClassifiedImage private constructor(
    val width: Int,
    val height: Int,
    val sourcePixelsPerMapPixel: Int,
    private val colors: ByteArray,
    private val luminance: FloatArray,
) {
    fun colorAt(x: Int, y: Int): Int = colors[y * width + x].toInt()
    fun luminanceAt(x: Float, y: Float): Float {
        val px = x.coerceIn(0f, width - 1f)
        val py = y.coerceIn(0f, height - 1f)
        val left = px.toInt()
        val top = py.toInt()
        val right = minOf(left + 1, width - 1)
        val bottom = minOf(top + 1, height - 1)
        val dx = px - left
        val dy = py - top
        return (luminance[top * width + left] * (1 - dx) + luminance[top * width + right] * dx) * (1 - dy) +
            (luminance[bottom * width + left] * (1 - dx) + luminance[bottom * width + right] * dx) * dy
    }

    companion object {
        private const val MAP_SCALE = 2
        private const val SOURCE_ROWS_PER_BATCH = 16
        private const val NO_COLOR = -1

        fun from(bitmap: Bitmap, classifyColor: (FloatArray) -> OrbColor?): ClassifiedImage {
            val width = (bitmap.width + MAP_SCALE - 1) / MAP_SCALE
            val height = (bitmap.height + MAP_SCALE - 1) / MAP_SCALE
            val colors = ByteArray(width * height) { NO_COLOR.toByte() }
            val luminance = FloatArray(width * height)
            val hsv = FloatArray(3)
            val rowsPerBatch = SOURCE_ROWS_PER_BATCH
            val sourceRows = IntArray(bitmap.width * rowsPerBatch)
            var sourceTop = 0
            while (sourceTop < bitmap.height) {
                val batchHeight = minOf(rowsPerBatch, bitmap.height - sourceTop)
                bitmap.getPixels(sourceRows, 0, bitmap.width, 0, sourceTop, bitmap.width, batchHeight)
                var localY = 0
                while (localY < batchHeight) {
                    val nextLocalY = minOf(localY + 1, batchHeight - 1)
                    val outputY = (sourceTop + localY) / MAP_SCALE
                    for (outputX in 0 until width) {
                        val sourceX = outputX * MAP_SCALE
                        var red = 0
                        var green = 0
                        var blue = 0
                        var count = 0
                        for (dy in localY..nextLocalY) {
                            for (dx in sourceX until minOf(sourceX + MAP_SCALE, bitmap.width)) {
                                val pixel = sourceRows[dy * bitmap.width + dx]
                                red += Color.red(pixel)
                                green += Color.green(pixel)
                                blue += Color.blue(pixel)
                                count++
                            }
                        }
                        val averagedPixel = Color.rgb(red / count, green / count, blue / count)
                        luminance[outputY * width + outputX] =
                            (0.2126f * red + 0.7152f * green + 0.0722f * blue) / count
                        Color.colorToHSV(averagedPixel, hsv)
                        colors[outputY * width + outputX] =
                            (classifyColor(hsv)?.ordinal ?: NO_COLOR).toByte()
                    }
                    localY += MAP_SCALE
                }
                sourceTop += batchHeight
            }
            return ClassifiedImage(width, height, MAP_SCALE, colors, luminance)
        }
    }
}
