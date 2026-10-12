package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlin.math.roundToInt

/** Shared color sampling and hazard matching; references and modifiers belong to children. */
internal abstract class BaseOrbRecognizer(
    private val sampling: OrbSamplingParameters?,
    private val hazards: HazardSymbolMatcher?,
) {
    val available: Boolean get() = sampling != null

    protected abstract fun classifyColor(hsv: FloatArray): OrbColor?

    fun prepareImage(bitmap: Bitmap): ClassifiedImage = ClassifiedImage.from(bitmap, ::classifyColor)

    open fun detectModifiers(
        bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float, color: OrbColor,
        image: ClassifiedImage? = null,
    ): Set<OrbModifier> = emptySet()

    protected fun hasMarker(
        image: ClassifiedImage, centerX: Float, centerY: Float, cellSize: Float, marker: OrbMarkerTemplate,
    ): Boolean {
        val pitch = cellSize / image.sourcePixelsPerMapPixel
        val x = centerX / image.sourcePixelsPerMapPixel - 0.5f
        val y = centerY / image.sourcePixelsPerMapPixel - 0.5f
        val values = FloatArray(marker.sampleIndices.size)
        for (scale in marker.scales) for (dx in marker.xOffsets) for (dy in marker.yOffsets) {
            for ((sample, index) in marker.sampleIndices.withIndex()) {
                val row = index / marker.side
                val column = index % marker.side
                values[sample] = image.luminanceAt(
                    x + (dx + (column - marker.side / 2) * marker.samplePitch * scale) * pitch,
                    y + (dy + (row - marker.side / 2) * marker.samplePitch * scale) * pitch,
                )
            }
            LuminanceTemplate.normalize(values)
            if (marker.samples.any { it.correlation(values) >= marker.minimumCorrelation }) return true
        }
        return false
    }

    fun sampleOrbColor(
        image: ClassifiedImage,
        centerX: Float,
        centerY: Float,
        cellSize: Float,
    ): OrbSample {
        val sampling = sampling ?: return OrbSample(OrbColor.UNKNOWN, 0f)
        val votes = IntArray(OrbColor.entries.size)
        var sampledPatches = 0
        // Independent patches are less sensitive to interior art and edge badges.
        // Each patch gets one vote so a colorful patch cannot dominate the cell.
        val scale = image.sourcePixelsPerMapPixel
        val mappedCenterX = centerX / scale
        val mappedCenterY = centerY / scale
        val mappedCellSize = cellSize / scale
        // Hazards share hues with ordinary orbs but have consistent symbols.
        // Only hazards use artwork matching; ordinary orbs use the color ring.
        sampleHazard(image, centerX, centerY, cellSize)?.let { return it }
        val patchRadius = mappedCellSize * sampling.patchRadiusFraction
        val patchVotes = IntArray(OrbColor.entries.size)
        val step = (mappedCellSize * sampling.stepFraction).toInt().coerceAtLeast(1)
        for ((offsetFractionX, offsetFractionY) in sampling.patchOffsets) {
            patchVotes.fill(0)
            val offsetX = offsetFractionX * mappedCellSize
            val offsetY = offsetFractionY * mappedCellSize
            val left = (mappedCenterX + offsetX - patchRadius).toInt().coerceAtLeast(0)
            val right = (mappedCenterX + offsetX + patchRadius).toInt().coerceAtMost(image.width - 1)
            val top = (mappedCenterY + offsetY - patchRadius).toInt().coerceAtLeast(0)
            val bottom = (mappedCenterY + offsetY + patchRadius).toInt().coerceAtMost(image.height - 1)
            var y = top
            while (y <= bottom) {
                var x = left
                while (x <= right) {
                    val colorIndex = image.colorAt(x, y)
                    if (colorIndex >= 0) patchVotes[colorIndex]++
                    x += step
                }
                y += step
            }
            val patchWinner = OrbColor.entries
                .filter { it != OrbColor.UNKNOWN }
                .filter { patchVotes[it.ordinal] > 0 }
                .sortedByDescending { patchVotes[it.ordinal] }
                .let { ranked ->
                    val top = ranked.firstOrNull()
                    top?.takeIf {
                        (ranked.getOrNull(1)?.let { second -> patchVotes[second.ordinal] } ?: 0) <
                            patchVotes[it.ordinal] &&
                            patchVotes[it.ordinal].toFloat() / patchVotes.sum() >= sampling.patchVoteFraction
                    }
                }
            sampledPatches++
            if (patchWinner != null && patchVotes[patchWinner.ordinal] > 0) {
                votes[patchWinner.ordinal]++
            }
        }
        val rankedColors = OrbColor.entries
            .filter { it != OrbColor.UNKNOWN }
            .sortedByDescending { votes[it.ordinal] }
        val selected = rankedColors.firstOrNull()?.takeIf {
            (rankedColors.getOrNull(1)?.let { second -> votes[second.ordinal] } ?: 0) < votes[it.ordinal]
        } ?: return OrbSample(OrbColor.UNKNOWN, 0f)
        val classifiedPatches = votes.sum()
        val colorSupport = if (sampledPatches == 0) 0f else votes[selected.ordinal].toFloat() / sampledPatches
        if (classifiedPatches == 0 || votes[selected.ordinal].toFloat() / classifiedPatches < sampling.minimumColorVoteFraction) {
            return OrbSample(OrbColor.UNKNOWN, colorSupport)
        }
        return OrbSample(selected, colorSupport)
    }

    /** Retry only unresolved cells after geometry has been established. */
    fun recoverOrbColor(image: ClassifiedImage, x: Float, y: Float, pitch: Float): OrbSample? {
        val sampling = sampling ?: return null
        sampleHazard(image, x, y, pitch, refine = true)?.let { return it }
        val samples = ArrayList<OrbSample>()
        for (dx in sampling.alignmentOffsets) for (dy in sampling.alignmentOffsets) {
            val sample = sampleOrbColor(image, x + dx * pitch, y + dy * pitch, pitch)
            if (sample.color != OrbColor.UNKNOWN && sample.colorSupport >= sampling.minimumRecoverySupport) samples += sample
        }
        val groups = samples.groupBy { it.color }.values.sortedByDescending { it.size }
        val best = groups.firstOrNull() ?: return null
        if (best.size < sampling.minimumRecoverySamples || best.size < sampling.minimumRecoveryVoteRatio * (groups.getOrNull(1)?.size ?: 0)) return null
        return OrbSample(best.first().color, best.map { it.colorSupport }.average().toFloat())
    }

    fun refineColor(
        image: ClassifiedImage, centerX: Float, centerY: Float, cellSize: Float, sampledColor: OrbColor,
    ): OrbColor = sampleHazard(image, centerX, centerY, cellSize, refine = true)?.color ?: sampledColor

    private fun sampleHazard(
        image: ClassifiedImage,
        centerX: Float,
        centerY: Float,
        cellSize: Float,
        refine: Boolean = false,
    ): OrbSample? {
        val hazards = hazards ?: return null
        val sampling = sampling ?: return null
        val mapScale = image.sourcePixelsPerMapPixel
        val pitch = cellSize / mapScale
        val x = centerX / mapScale - 0.5f
        val y = centerY / mapScale - 0.5f
        val values = FloatArray(hazards.side * hazards.side)
        fun descriptor(scale: Float, dx: Float, dy: Float): FloatArray {
            for (row in 0 until hazards.side) for (column in 0 until hazards.side) {
                values[row * hazards.side + column] = image.luminanceAt(
                    x + (dx + (column - hazards.side / 2) * hazards.samplePitch * scale) * pitch,
                    y + (dy + (row - hazards.side / 2) * hazards.samplePitch * scale) * pitch,
                )
            }
            return values
        }
        val scores = FloatArray(OrbColor.entries.size)
        hazards.accumulateScores(descriptor(1f, 0f, 0f), scores)
        hazards.selectHazard(scores)?.let { (color, support) ->
            return OrbSample(color, support)
        }
        if (!refine) return null
        // Orb art can sit off center and vary slightly in size even within one
        // screenshot. Keep the hazard confidence requirement while
        // searching a small neighborhood; thin route strokes can still leave
        // enough of the skull visible to match without removing overlay pixels.
        scores.fill(0f)
        for (scale in sampling.alignmentScales) for (dx in sampling.alignmentOffsets) for (dy in sampling.alignmentOffsets) {
            hazards.accumulateScores(descriptor(scale, dx, dy), scores)
        }
        return hazards.selectHazard(scores, refined = true)?.let { (color, support) ->
            OrbSample(color, support)
        }
    }

    protected inline fun markerPatch(
        bitmap: Bitmap, x: Float, y: Float, cellSize: Float, hsv: FloatArray, minimumSupport: Float = 0.45f,
        accepts: (FloatArray) -> Boolean,
    ): Boolean {
        val radius = (cellSize * 0.009f).roundToInt().coerceAtLeast(1)
        var accepted = 0
        var samples = 0
        for (py in y.roundToInt() - radius..y.roundToInt() + radius) {
            for (px in x.roundToInt() - radius..x.roundToInt() + radius) {
                if (px !in 0 until bitmap.width || py !in 0 until bitmap.height) continue
                Color.colorToHSV(bitmap.getPixel(px, py), hsv)
                if (accepts(hsv)) accepted++
                samples++
            }
        }
        return samples > 0 && accepted.toFloat() / samples >= minimumSupport
    }
}

internal data class OrbSample(val color: OrbColor, val colorSupport: Float)
