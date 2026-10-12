package com.example.phoneapp.capture

import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.game.GameVariant

/** PAD 0's bright, inset 6x5 grid, calibrated from the supplied battle captures. */
internal class PadZeroBoardRecognizer : BaseBoardRecognizer(
    game = GameVariant.PAD_ZERO,
    geometry = BoardGeometry(
        sizes = listOf(BoardSize.STANDARD),
        minimumCaptureWidth = 200,
        minimumCaptureHeight = 300,
        minimumAspectRatio = 1.2f,
        leftFraction = 0.018f,
        widthFraction = 0.964f,
        minimumCenterYMargin = 0.40f,
        bottomSearchOvershootCells = 0.25f,
        centerYStep = 0.004f,
        preferredCenterY = 0.8125f,
        minimumAverageColorSupport = 0.40f,
        minimumGridGapSupport = 0.15f,
        minimumSizeScoreMargin = 1f,
        recognitionWeight = 30f,
        varietyBonus = 0.45f,
        colorSupportWeight = 18f,
        centerPenalty = 4f,
        gridGapWeight = 12f,
        minimumFrameInsetFraction = 0.015f,
        gapPolarity = -1f,
        rowGapSampleFraction = 0.9f,
    ),
    orbRecognizer = PadZeroOrbRecognizer(),
)
