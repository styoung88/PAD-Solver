package com.example.phoneapp.capture

import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.game.GameVariant

/** PAD's portrait battle layout, calibrated against the committed capture fixtures. */
internal class PadBoardRecognizer : BaseBoardRecognizer(
    game = GameVariant.PAD,
    geometry = BoardGeometry(
        sizes = BoardSize.entries,
        // Square board fragments can mistake enemy artwork for missing rows.
        minimumCaptureWidth = 200,
        minimumCaptureHeight = 300,
        minimumAspectRatio = 1.2f,
        leftFraction = 0f,
        widthFraction = 1f,
        minimumCenterYMargin = 0.05f,
        bottomSearchOvershootCells = 0.25f,
        centerYStep = 0.006f,
        preferredCenterY = 0.790f,
        minimumAverageColorSupport = 0.30f,
        minimumGridGapSupport = 0.15f,
        minimumSizeScoreMargin = 1f,
        recognitionWeight = 30f,
        varietyBonus = 0.45f,
        colorSupportWeight = 18f,
        centerPenalty = 4f,
        gridGapWeight = 12f,
        minimumFrameInsetFraction = 0.015f,
    ),
    orbRecognizer = PadOrbRecognizer(),
)
