package com.example.phoneapp.capture

import com.example.phoneapp.board.OrbColor

/** Identifies only hazards; ordinary orb colors are determined by color sampling. */
internal class HazardSymbolMatcher(
    val side: Int,
    val samplePitch: Float,
    encodedSamples: List<Pair<OrbColor, String>>,
    private val thresholds: Map<OrbColor, Float>,
    private val refinedThresholds: Map<OrbColor, Float>,
    private val minimumMargin: Float,
    ignoredSamples: Set<Int> = emptySet(),
) {
    private val sampleIndices = (0 until side * side).filter { it !in ignoredSamples }.toIntArray()

    init {
        require(sampleIndices.isNotEmpty())
        require(ignoredSamples.all { it in 0 until side * side })
        require(encodedSamples.isNotEmpty())
        require(encodedSamples.all { (color, _) -> color in HAZARDS })
        require(thresholds.keys == encodedSamples.map { it.first }.toSet())
        require(refinedThresholds.keys.all { it in thresholds })
    }

    private val samples = encodedSamples.map { (color, encoded) ->
        require(encoded.length == side * side)
        color to LuminanceTemplate(sampleIndices.joinToString("") { encoded[it].toString() })
    }

    /** Keep the strongest pose for each supported hazard. */
    fun accumulateScores(values: FloatArray, scores: FloatArray) {
        val normalized = LuminanceTemplate.normalize(
            if (sampleIndices.size == values.size) values else FloatArray(sampleIndices.size) { values[sampleIndices[it]] },
        )
        for ((color, template) in samples) {
            val correlation = template.correlation(normalized)
            scores[color.ordinal] = maxOf(scores[color.ordinal], correlation)
        }
    }

    fun selectHazard(scores: FloatArray, refined: Boolean = false): Pair<OrbColor, Float>? {
        var bestColor = OrbColor.UNKNOWN
        var best = 0f
        var second = 0f
        for (color in thresholds.keys) {
            val correlation = scores[color.ordinal]
            if (correlation > best) {
                second = best
                best = correlation
                bestColor = color
            } else if (correlation > second) second = correlation
        }
        val threshold = (if (refined) refinedThresholds[bestColor] else null) ?: thresholds[bestColor] ?: return null
        return if (best >= threshold && best - second >= minimumMargin) bestColor to best else null
    }

    private companion object {
        val HAZARDS = setOf(OrbColor.JAMMER, OrbColor.POISON, OrbColor.LETHAL_POISON, OrbColor.BOMB)
    }
}
