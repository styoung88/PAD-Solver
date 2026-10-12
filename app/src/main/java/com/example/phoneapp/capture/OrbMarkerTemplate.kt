package com.example.phoneapp.capture

/** A variant's marker artwork, search area and confidence requirement. */
internal class OrbMarkerTemplate(
    val side: Int,
    val samplePitch: Float,
    encodedSamples: List<String>,
    val xOffsets: FloatArray,
    val yOffsets: FloatArray,
    val scales: FloatArray,
    val minimumCorrelation: Float,
    ignoredSamples: Set<Int> = emptySet(),
) {
    val sampleIndices = (0 until side * side).filter { it !in ignoredSamples }.toIntArray()

    init {
        require(sampleIndices.isNotEmpty())
        require(ignoredSamples.all { it in 0 until side * side })
    }

    val samples = encodedSamples.map { encoded ->
        require(encoded.length == side * side)
        LuminanceTemplate(sampleIndices.joinToString("") { encoded[it].toString() })
    }
}
