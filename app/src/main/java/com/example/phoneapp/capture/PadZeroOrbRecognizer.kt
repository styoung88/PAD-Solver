package com.example.phoneapp.capture

import android.graphics.Bitmap
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier

/** Ordinary PAD 0 orbs, its jammer symbol and +1 combo badges. */
internal class PadZeroOrbRecognizer : BaseOrbRecognizer(
    sampling = OrbSamplingParameters(
        patchRadiusFraction = 0.025f,
        stepFraction = 0.01f,
        patchOffsets = arrayOf(0f to -1f, 0.7f to -0.7f, 1f to 0f, 0.7f to 0.7f,
            0f to 1f, -0.7f to 0.7f, -1f to 0f, -0.7f to -0.7f)
            .flatMap { (x, y) -> listOf(0.20f, 0.28f).map { x * it to y * it } },
        patchVoteFraction = 0.55f,
        minimumColorVoteFraction = 0.40f,
        alignmentScales = floatArrayOf(0.9f, 1f, 1.1f),
        alignmentOffsets = floatArrayOf(-0.06f, -0.03f, 0f, 0.03f, 0.06f),
        minimumRecoverySupport = 0.40f,
        minimumRecoverySamples = 5,
        minimumRecoveryVoteRatio = 2,
    ),
    hazards = PadZeroOrbReferences.hazards,
) {
    override fun classifyColor(hsv: FloatArray): OrbColor? {
        if (hsv[1] < 0.28f || hsv[2] < 0.20f) return null
        return when {
            hsv[0] < 25f || hsv[0] >= 350f -> OrbColor.FIRE
            hsv[0] < 80f -> OrbColor.LIGHT
            hsv[0] < 170f -> OrbColor.WOOD
            hsv[0] < 252f -> OrbColor.WATER
            hsv[0] < 310f -> OrbColor.DARK
            else -> OrbColor.HEART
        }
    }

    override fun detectModifiers(
        bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float, color: OrbColor,
        image: ClassifiedImage?,
    ): Set<OrbModifier> {
        val classified = image ?: prepareImage(bitmap)
        return buildSet {
            if (hasMarker(classified, centerX, centerY, cellSize, PadZeroOrbReferences.comboBonus)) add(OrbModifier.COMBO_BONUS)
        }
    }
}
