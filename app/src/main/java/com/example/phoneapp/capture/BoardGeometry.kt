package com.example.phoneapp.capture

import com.example.phoneapp.board.BoardSize

/** Layout and search calibration for one game's battle screenshots. */
internal data class BoardGeometry(
    val sizes: List<BoardSize>,
    val minimumCaptureWidth: Int,
    val minimumCaptureHeight: Int,
    val minimumAspectRatio: Float,
    val leftFraction: Float,
    val widthFraction: Float,
    val minimumCenterYMargin: Float,
    val bottomSearchOvershootCells: Float,
    val centerYStep: Float,
    val preferredCenterY: Float,
    val minimumAverageColorSupport: Float,
    val minimumGridGapSupport: Float,
    val minimumSizeScoreMargin: Float,
    val recognitionWeight: Float,
    val varietyBonus: Float,
    val colorSupportWeight: Float,
    val centerPenalty: Float,
    val gridGapWeight: Float,
    val minimumFrameInsetFraction: Float,
    /** Positive for dark gaps; negative for bright grid lines. */
    val gapPolarity: Float = 1f,
    /** Cross-axis position for measuring horizontal lines without orb artwork. */
    val rowGapSampleFraction: Float = 0.5f,
)
