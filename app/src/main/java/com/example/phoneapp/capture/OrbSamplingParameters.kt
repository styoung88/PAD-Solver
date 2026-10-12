package com.example.phoneapp.capture

/** Normalized sampling positions and confidence requirements for one orb style. */
internal data class OrbSamplingParameters(
    val patchRadiusFraction: Float,
    val stepFraction: Float,
    val patchOffsets: List<Pair<Float, Float>>,
    val patchVoteFraction: Float,
    val minimumColorVoteFraction: Float,
    val alignmentScales: FloatArray,
    val alignmentOffsets: FloatArray,
    val minimumRecoverySupport: Float,
    val minimumRecoverySamples: Int,
    val minimumRecoveryVoteRatio: Int,
)
