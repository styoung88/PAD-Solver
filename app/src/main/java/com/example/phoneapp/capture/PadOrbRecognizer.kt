package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier

internal class PadOrbRecognizer : BaseOrbRecognizer(
    sampling = OrbSamplingParameters(
        patchRadiusFraction = 0.025f,
        stepFraction = 0.01f,
        // Two interior rings include hearts' pink body, avoiding purple borders.
        patchOffsets = arrayOf(0f to -1f, 0.7f to -0.7f, 1f to 0f, 0.7f to 0.7f,
            0f to 1f, -0.7f to 0.7f, -1f to 0f, -0.7f to -0.7f)
            .flatMap { (x, y) -> listOf(0.22f, 0.30f).map { x * it to y * it } },
        patchVoteFraction = 0.55f,
        minimumColorVoteFraction = 0.38f,
        alignmentScales = floatArrayOf(0.9f, 1f, 1.1f),
        alignmentOffsets = floatArrayOf(-0.08f, -0.04f, 0f, 0.04f, 0.08f),
        minimumRecoverySupport = 0.35f,
        minimumRecoverySamples = 5,
        minimumRecoveryVoteRatio = 2,
    ),
    hazards = PadHazardTemplates.matcher,
) {
    override fun detectModifiers(
        bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float, color: OrbColor,
        image: ClassifiedImage?,
    ): Set<OrbModifier> = buildSet {
        if (color.isAttacking || color == OrbColor.HEART) {
            if (hasEnhancedMarker(bitmap, centerX, centerY, cellSize, color)) add(OrbModifier.ENHANCED)
            else if (hasWeakenedMarker(bitmap, centerX, centerY, cellSize)) add(OrbModifier.WEAKENED)
        }
    }

    override fun classifyColor(hsv: FloatArray): OrbColor? {
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        // Bright, saturated pink is common on PAD heart orbs. Saturation already
        // rejects pale UI highlights, so an upper brightness cutoff drops useful
        // heart pixels without filtering much background noise.
        if (saturation < MIN_SATURATION || value < MIN_BRIGHTNESS) return null
        return when {
            hue < FIRE_CORE_HUE_MAX || hue >= RED_WRAP_HUE_MIN -> OrbColor.FIRE
            hue < FIRE_ORANGE_HUE_MAX -> if (value >= FIRE_ORANGE_MIN_BRIGHTNESS) OrbColor.FIRE else null
            hue < LIGHT_HUE_MAX -> OrbColor.LIGHT
            hue < 166f -> OrbColor.WOOD
            hue < 252f -> OrbColor.WATER
            hue < 310f -> OrbColor.DARK
            hue < RED_WRAP_HUE_MIN -> OrbColor.HEART
            else -> null
        }
    }

    /** Blue horizontal badge with a bright rim, rather than ordinary blue orb art. */
    internal fun hasWeakenedMarker(bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float): Boolean {
        val hsv = FloatArray(3)
        fun blue(x: Float, y: Float) = markerPatch(bitmap, x, y, cellSize, hsv) { hsv ->
            hsv[0] in 175f..235f && hsv[1] in 0.08f..0.75f && hsv[2] >= 0.65f
        }
        fun rim(x: Float, y: Float) = markerPatch(bitmap, x, y, cellSize, hsv, 0.25f) { hsv ->
            hsv[1] < 0.35f && hsv[2] >= 0.72f
        }
        var dy = 0.14f
        // The badge overlays the body, above the orb's blue/white bottom rim.
        while (dy <= 0.32f) {
            var dx = 0.14f
            while (dx <= 0.44f) {
                val x = centerX + dx * cellSize
                val y = centerY + dy * cellSize
                if (blue(x, y)) for (halfWidth in MINUS_HALF_WIDTHS) for (halfHeight in MINUS_HALF_HEIGHTS) {
                    if (blue(x - halfWidth * cellSize, y) && blue(x + halfWidth * cellSize, y) &&
                        rim(x, y - cellSize * halfHeight) && rim(x, y + cellSize * halfHeight) &&
                        rim(x - cellSize * (halfWidth + 0.055f), y) &&
                        rim(x + cellSize * (halfWidth + 0.055f), y) &&
                        !blue(x, y - cellSize * 0.08f) && !blue(x, y + cellSize * 0.08f)
                    ) return true
                }
                dx += 0.01f
            }
            dy += 0.01f
        }
        return false
    }

    /** Detects the small gold + glyph near the lower-right of an enhanced orb. */
    private fun hasEnhancedMarker(bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float, color: OrbColor): Boolean {
        val sampleRadius = (cellSize * ENHANCED_MARKER_SAMPLE_RADIUS).toInt().coerceAtLeast(1)
        val patchStep = sampleRadius
        val hsv = FloatArray(3)

        // PAD's badge sits at the lower-right edge, but its apparent offset and
        // size vary with orb art and screenshot scaling. Search a normalized
        // area around that corner instead of relying on one exact sample point.
        var markerOffsetY = ENHANCED_MARKER_MIN_Y_OFFSET
        while (markerOffsetY <= ENHANCED_MARKER_MAX_Y_OFFSET) {
            var markerOffsetX = ENHANCED_MARKER_MIN_X_OFFSET
            while (markerOffsetX <= ENHANCED_MARKER_MAX_X_OFFSET) {
                val markerCenterX = centerX + cellSize * markerOffsetX
                val markerCenterY = centerY + cellSize * markerOffsetY
                for (armSpacing in ENHANCED_MARKER_ARM_SPACINGS) {
                    var goldPatches = 0
                    for ((offsetX, offsetY) in ENHANCED_MARKER_PLUS_POINTS) {
                        if (hasEnhancedMarkerPatch(
                                bitmap,
                                markerCenterX + offsetX * cellSize * armSpacing,
                                markerCenterY + offsetY * cellSize * armSpacing,
                                sampleRadius,
                                patchStep,
                                hsv,
                            )
                        ) goldPatches++
                    }

                    var goldControlPatches = 0
                    for ((offsetX, offsetY) in ENHANCED_MARKER_DIAGONAL_POINTS) {
                        if (hasEnhancedMarkerPatch(
                                bitmap,
                                markerCenterX + offsetX * cellSize * armSpacing,
                                markerCenterY + offsetY * cellSize * armSpacing,
                                sampleRadius,
                                patchStep,
                                hsv,
                            )
                        ) goldControlPatches++
                    }
                    // Light's gold star can resemble a partially sampled plus.
                    val requiredPatches = if (color == OrbColor.LIGHT) 5 else MIN_ENHANCED_PLUS_PATCHES
                    if (goldPatches >= requiredPatches &&
                        goldControlPatches <= MAX_ENHANCED_CONTROL_PATCHES
                    ) {
                        // A light orb's star also has gold arms over dark corners.
                        // The badge's pale outline distinguishes the two symbols.
                        if (color != OrbColor.LIGHT || ENHANCED_MARKER_DIAGONAL_POINTS.count { (dx, dy) ->
                                hasEnhancedMarkerPatch(bitmap, markerCenterX + dx * cellSize * armSpacing,
                                    markerCenterY + dy * cellSize * armSpacing, sampleRadius, patchStep, hsv,
                                    paleOutline = true)
                            } >= 2
                        ) return true
                    }
                }
                markerOffsetX += ENHANCED_MARKER_OFFSET_STEP
            }
            markerOffsetY += ENHANCED_MARKER_OFFSET_STEP
        }
        return false
    }

    private fun hasEnhancedMarkerPatch(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        radius: Int,
        step: Int,
        hsv: FloatArray,
        paleOutline: Boolean = false,
    ): Boolean {
        var goldPixels = 0
        var sampledPixels = 0
        val left = (centerX.toInt() - radius).coerceAtLeast(0)
        val right = (centerX.toInt() + radius).coerceAtMost(bitmap.width - 1)
        val top = (centerY.toInt() - radius).coerceAtLeast(0)
        val bottom = (centerY.toInt() + radius).coerceAtMost(bitmap.height - 1)
        var y = top
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                val matches = if (paleOutline) hsv[1] < 0.30f && hsv[2] >= ENHANCED_GOLD_MIN_BRIGHTNESS
                else hsv[0] in ENHANCED_GOLD_HUE_RANGE &&
                    hsv[1] >= ENHANCED_GOLD_MIN_SATURATION && hsv[2] >= ENHANCED_GOLD_MIN_BRIGHTNESS
                if (matches) goldPixels++
                sampledPixels++
                x += step
            }
            y += step
        }
        return sampledPixels > 0 && goldPixels.toFloat() / sampledPixels >= ENHANCED_PATCH_GOLD_FRACTION
    }

    private companion object {
        private const val FIRE_CORE_HUE_MAX = 24f
        private const val FIRE_ORANGE_HUE_MAX = 40f
        private const val FIRE_ORANGE_MIN_BRIGHTNESS = 0.54f
        private const val LIGHT_HUE_MAX = 75f
        private const val RED_WRAP_HUE_MIN = 350f
        private const val MIN_SATURATION = 0.28f
        private const val MIN_BRIGHTNESS = 0.20f

        const val ENHANCED_MARKER_MIN_X_OFFSET = 0.12f
        val MINUS_HALF_WIDTHS = floatArrayOf(0.05f, 0.065f, 0.08f)
        val MINUS_HALF_HEIGHTS = floatArrayOf(0.035f, 0.045f, 0.055f, 0.065f)
        const val ENHANCED_MARKER_MAX_X_OFFSET = 0.38f
        const val ENHANCED_MARKER_MIN_Y_OFFSET = 0.08f
        const val ENHANCED_MARKER_MAX_Y_OFFSET = 0.52f
        const val ENHANCED_MARKER_OFFSET_STEP = 0.02f
        const val ENHANCED_MARKER_SAMPLE_RADIUS = 0.014f
        const val ENHANCED_GOLD_MIN_SATURATION = 0.65f
        const val ENHANCED_GOLD_MIN_BRIGHTNESS = 0.82f
        const val ENHANCED_PATCH_GOLD_FRACTION = 0.24f
        val ENHANCED_GOLD_HUE_RANGE = 35f..70f
        const val MIN_ENHANCED_PLUS_PATCHES = 4
        const val MAX_ENHANCED_CONTROL_PATCHES = 1
        val ENHANCED_MARKER_ARM_SPACINGS = floatArrayOf(0.03f, 0.045f, 0.06f)
        val ENHANCED_MARKER_PLUS_POINTS = arrayOf(
            0f to 0f,
            -1f to 0f,
            1f to 0f,
            0f to -1f,
            0f to 1f,
        )
        val ENHANCED_MARKER_DIAGONAL_POINTS = arrayOf(
            -1f to -1f,
            1f to -1f,
            -1f to 1f,
            1f to 1f,
        )
    }
}
