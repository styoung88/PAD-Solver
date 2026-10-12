package com.example.phoneapp.capture

import kotlin.math.sqrt

/** Contrast-normalized artwork samples shared by orb and marker comparison. */
internal class LuminanceTemplate(encoded: String) {
    private val samples = normalize(FloatArray(encoded.length) { encoded[it].digitToInt(16).toFloat() })

    fun correlation(normalizedValues: FloatArray): Float {
        var correlation = 0f
        for (index in normalizedValues.indices) correlation += normalizedValues[index] * samples[index]
        return correlation
    }

    companion object {
        fun normalize(values: FloatArray): FloatArray {
            val mean = values.average().toFloat()
            var norm = 0f
            for (index in values.indices) {
                values[index] -= mean
                norm += values[index] * values[index]
            }
            norm = sqrt(norm).coerceAtLeast(0.0001f)
            for (index in values.indices) values[index] /= norm
            return values
        }
    }
}
