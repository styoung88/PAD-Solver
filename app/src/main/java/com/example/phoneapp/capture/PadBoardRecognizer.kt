package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface BoardRecognition {
    data class Recognized(
        val board: Board,
        /** Bounds are in screenshot pixels and describe the outside of the grid. */
        val boardBounds: RectF,
        val captureWidth: Int,
        val captureHeight: Int,
    ) : BoardRecognition

    data class Uncertain(val recognizedCells: Int) : BoardRecognition
}

/** Recognizes the centered PAD grid using orb colors and spacing evidence. */
class PadBoardRecognizer {
    fun recognize(bitmap: Bitmap): BoardRecognition {
        // PAD captures include the portrait battle screen above the grid. Square
        // board fragments can otherwise mistake enemy artwork for missing rows.
        if (bitmap.width < 200 || bitmap.height < 300 || bitmap.height < bitmap.width * 1.2f) {
            return BoardRecognition.Uncertain(0)
        }
        val colorMap = ClassifiedImage.from(bitmap, ::classify)
        val candidates = BoardSize.entries
            .mapNotNull { findCandidate(bitmap, colorMap, it) }
            .sortedByDescending { it.score }
        val usable = candidates.filter { it.complete &&
            it.averageColorSupport >= MIN_AVERAGE_COLOR_SUPPORT && it.gridSupport >= MIN_GRID_GAP_SUPPORT &&
            it.top >= 0f && it.top + it.height <= bitmap.height }
        val candidate = usable.firstOrNull() ?: return BoardRecognition.Uncertain(candidates.firstOrNull()?.recognized ?: 0)
        // Do not replace a stronger, partially obscured grid with a weaker
        // region just because all of that region's colors happened to classify.
        if (candidates.any { !it.complete && it.score > candidate.score &&
                it.averageColorSupport >= MIN_AVERAGE_COLOR_SUPPORT && it.gridSupport >= MIN_GRID_GAP_SUPPORT
            }
        ) return BoardRecognition.Uncertain(candidates.first().recognized)
        if (usable.getOrNull(1)?.let {
                candidate.score - it.score < MIN_SIZE_SCORE_MARGIN
            } == true
        ) return BoardRecognition.Uncertain(candidate.recognized)

        val size = candidate.size
        return BoardRecognition.Recognized(
            board = Board(
                columns = size.columns,
                rows = size.rows,
                orbs = candidate.colors.mapIndexed { index, sampledColor ->
                    val row = index / size.columns
                    val column = index % size.columns
                    val centerX = candidate.left + (column + 0.5f) * candidate.width / size.columns
                    val centerY = candidate.top + (row + 0.5f) * candidate.height / size.rows
                    val cellSize = candidate.width / size.columns
                    // Refine symbols only after fitting the grid so alignment tolerance
                    // does not flatten the evidence used to locate its rows and columns.
                    val color = sampleHazard(colorMap, centerX, centerY, cellSize, refine = true)?.color
                        ?: sampledColor
                    val modifiers = buildSet {
                        if (hasLockedMarker(bitmap, centerX, centerY, cellSize)) add(OrbModifier.LOCKED)
                        if (color.isAttacking || color == OrbColor.HEART) {
                            if (hasEnhancedMarker(bitmap, centerX, centerY, cellSize, color)) add(OrbModifier.ENHANCED)
                            else if (hasWeakenedMarker(bitmap, centerX, centerY, cellSize)) add(OrbModifier.WEAKENED)
                        }
                    }
                    Orb(color, modifiers)
                },
            ),
            boardBounds = RectF(candidate.left, candidate.top,
                candidate.left + candidate.width, candidate.top + candidate.height),
            captureWidth = bitmap.width,
            captureHeight = bitmap.height,
        )
    }

    private fun findCandidate(bitmap: Bitmap, colorMap: ClassifiedImage, size: BoardSize): Candidate? {
        var best: Candidate? = null
        // Start at full screenshot width. Only consistent measured column gaps
        // can establish a small frame inset; color support alone must not narrow it.
        val boardWidth = bitmap.width.toFloat()
        val cellSize = boardWidth / size.columns
        val boardHeight = cellSize * size.rows
        val minCenterY = boardHeight / 2f + bitmap.height * MIN_CENTER_Y_MARGIN
        // A framed board can have a smaller pitch than the full-width first pass.
        // Allow that first pass near the bottom edge; final fitted bounds must fit.
        val maxCenterY = bitmap.height - boardHeight / 2f + cellSize * 0.25f
        val coarseYStep = (bitmap.height * CENTER_Y_STEP).coerceAtLeast(1f)

        fun candidateAt(centerY: Float, cellHeight: Float = cellSize,
            left: Float = 0f, width: Float = boardWidth, recoverUnknown: Boolean = false): Candidate {
            val height = cellHeight * size.rows
            val top = centerY - height / 2f
            val cellWidth = width / size.columns
            val samples = ArrayList<OrbSample>(size.cellCount)
            for (row in 0 until size.rows) for (column in 0 until size.columns) {
                val x = left + (column + 0.5f) * cellWidth
                val y = top + (row + 0.5f) * cellHeight
                val sample = sampleOrbColor(colorMap, x, y, cellWidth)
                samples += if (recoverUnknown && sample.color == OrbColor.UNKNOWN) {
                    recoverOrbColor(colorMap, x, y, cellWidth) ?: sample
                } else sample
            }
            val colors = samples.map(OrbSample::color)
            val recognized = colors.count { it != OrbColor.UNKNOWN }
            val variety = colors.asSequence().filter { it != OrbColor.UNKNOWN }.distinct().count()
            val averageColorSupport = samples.sumOf { it.colorSupport.toDouble() }.toFloat() / samples.size
            val centerPenalty = abs(centerY - bitmap.height * PREFERRED_CENTER_Y) / bitmap.height
            // Use fractions when comparing sizes; 42 sampled cells must not win
            // simply because there are more of them than on a 20-cell board.
            val score = recognized.toFloat() / size.cellCount * RECOGNITION_WEIGHT +
                variety * VARIETY_BONUS + averageColorSupport * COLOR_SUPPORT_WEIGHT -
                centerPenalty * CENTER_PENALTY
            val candidate = Candidate(size, left, top, width, height, colors, recognized, averageColorSupport, score)
            val support = gridGapSupport(bitmap, candidate)
            return candidate.copy(gridSupport = support, score = score + support * GRID_GAP_WEIGHT)
        }

        var centerY = minCenterY
        while (centerY <= maxCenterY) {
            val candidate = candidateAt(centerY)
            val currentBest = best
            if (currentBest == null || candidate.betterThan(currentBest)) best = candidate
            centerY += coarseYStep
        }
        val coarseBest = best ?: return null
        val coarseCenterY = coarseBest.top + coarseBest.height / 2f
        var refinedCenterY = (coarseCenterY - coarseYStep).coerceAtLeast(minCenterY)
        val refineUntil = (coarseCenterY + coarseYStep).coerceAtMost(maxCenterY)
        while (refinedCenterY <= refineUntil) {
            val candidate = candidateAt(refinedCenterY)
            if (candidate.betterThan(checkNotNull(best))) best = candidate
            refinedCenterY += 1f
        }
        val fullWidthCandidate = checkNotNull(best)
        val fittedColumns = fitColumnGaps(bitmap, fullWidthCandidate)
        val colorCandidate = fittedColumns?.let { (left, pitch) ->
            candidateAt(fullWidthCandidate.top + fullWidthCandidate.height / 2f,
                pitch, left, pitch * size.columns)
        } ?: fullWidthCandidate
        val fittedRows = fitRowGaps(bitmap, colorCandidate)
        val candidate = fittedRows?.let { (top, pitch) ->
            candidateAt(top + pitch * size.rows / 2f, pitch, colorCandidate.left, colorCandidate.width).takeIf {
                it.averageColorSupport >= MIN_AVERAGE_COLOR_SUPPORT &&
                    it.averageColorSupport >= colorCandidate.averageColorSupport - 0.10f
            }
        } ?: colorCandidate
        // With row centers aligned, measure columns again to remove orb-art bias
        // from the initial full-width scan's slightly different vertical pitch.
        val refinedColumns = if (fittedColumns != null && fittedRows != null) {
            // Refinement should align the same measured gaps. Orb borders can
            // otherwise draw a second fit inward and shrink an already fitted grid.
            fitColumnGaps(bitmap, candidate)?.takeIf { (left, pitch) ->
                abs(pitch - fittedColumns.second) <= fittedColumns.second * 0.01f &&
                    abs(left - fittedColumns.first) <= fittedColumns.second * 0.05f
            }
        } else null
        val refined = refinedColumns?.let { (left, pitch) ->
            candidateAt(candidate.top + candidate.height / 2f, candidate.height / size.rows,
                left, pitch * size.columns).takeIf {
                    it.averageColorSupport >= candidate.averageColorSupport - 0.10f
                }
        } ?: candidate
        return candidateAt(refined.top + refined.height / 2f, refined.height / size.rows,
            refined.left, refined.width, recoverUnknown = true)
    }

    /** Retry only unresolved cells after geometry has been established. */
    private fun recoverOrbColor(image: ClassifiedImage, x: Float, y: Float, pitch: Float): OrbSample? {
        sampleHazard(image, x, y, pitch, refine = true)?.let { return it }
        val samples = ArrayList<OrbSample>()
        for (dx in SYMBOL_OFFSETS) for (dy in SYMBOL_OFFSETS) {
            val sample = sampleOrbColor(image, x + dx * pitch, y + dy * pitch, pitch)
            if (sample.color != OrbColor.UNKNOWN && sample.colorSupport >= 0.35f) samples += sample
        }
        val groups = samples.groupBy { it.color }.values.sortedByDescending { it.size }
        val best = groups.firstOrNull() ?: return null
        if (best.size < 5 || best.size < 2 * (groups.getOrNull(1)?.size ?: 0)) return null
        return OrbSample(best.first().color, best.map { it.colorSupport }.average().toFloat())
    }

    private fun brightness(bitmap: Bitmap, x: Float, y: Float): Float {
        val pixel = bitmap.getPixel(x.roundToInt().coerceIn(0, bitmap.width - 1),
            y.roundToInt().coerceIn(0, bitmap.height - 1))
        return (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) +
            0.0722f * Color.blue(pixel)) / 255f
    }

    private fun FloatArray.median(): Float {
        sort()
        return if (size % 2 == 0) (this[size / 2 - 1] + this[size / 2]) / 2f else this[size / 2]
    }

    private fun fitColumnGaps(bitmap: Bitmap, candidate: Candidate): Pair<Float, Float>? {
        val size = candidate.size
        val cellWidth = candidate.width / size.columns
        val cellHeight = candidate.height / size.rows
        val gaps = FloatArray(size.columns - 1)
        for (column in 1 until size.columns) {
            val expected = candidate.left + column * cellWidth
            var bestContrast = 0f
            val band = ArrayList<Pair<Int, Float>>()
            val radius = cellWidth * 0.16f
            for (x in (expected - radius).roundToInt()..(expected + radius).roundToInt()) {
                val contrast = FloatArray(size.rows) { row ->
                    val centerY = candidate.top + (row + 0.5f) * cellHeight
                    var sum = 0f
                    for (offset in -1..1) {
                        val y = centerY + offset * cellHeight * 0.10f
                        sum += (brightness(bitmap, x - cellWidth * 0.09f, y) +
                            brightness(bitmap, x + cellWidth * 0.09f, y)) / 2f - brightness(bitmap, x.toFloat(), y)
                    }
                    sum / 3f
                }.median()
                band += x to contrast
                bestContrast = maxOf(bestContrast, contrast)
            }
            if (bestContrast < 0.08f) return null
            val strongest = band.filter { it.second >= bestContrast * 0.80f }
            gaps[column - 1] = strongest.sumOf { (it.first * it.second).toDouble() }.toFloat() /
                strongest.sumOf { it.second.toDouble() }.toFloat()
        }
        val meanIndex = size.columns / 2f
        val meanX = gaps.average().toFloat()
        val denominator = gaps.indices.sumOf { ((it + 1 - meanIndex) * (it + 1 - meanIndex)).toDouble() }.toFloat()
        val pitch = gaps.indices.sumOf { ((it + 1 - meanIndex) * (gaps[it] - meanX)).toDouble() }.toFloat() / denominator
        val left = meanX - meanIndex * pitch
        val rightInset = bitmap.width - (left + size.columns * pitch)
        if (pitch !in cellWidth * 0.94f..cellWidth * 1.005f || left < -1.5f || rightInset < -1.5f ||
            abs(left - rightInset) > cellWidth * 0.10f ||
            gaps.indices.any { abs(gaps[it] - (left + (it + 1) * pitch)) > cellWidth * 0.06f }
        ) return null
        // Small inferred insets can come from orb art. Retain full-width centers
        // unless the measured frame is wide enough to distinguish reliably.
        if (maxOf(left, rightInset) < bitmap.width * MIN_FRAME_INSET_FRACTION) return 0f to cellWidth
        return left to pitch
    }

    private fun fitRowGaps(bitmap: Bitmap, candidate: Candidate): Pair<Float, Float>? {
        val size = candidate.size
        val cellWidth = candidate.width / size.columns
        val gaps = FloatArray(size.rows - 1)
        for (row in 1 until size.rows) {
            val expected = candidate.top + row * candidate.height / size.rows
            var bestContrast = 0f
            val searchRadius = cellWidth * 0.12f
            val band = ArrayList<Pair<Int, Float>>()
            for (y in (expected - searchRadius).roundToInt()..(expected + searchRadius).roundToInt()) {
                val contrast = FloatArray(size.columns) { column ->
                    val centerX = candidate.left + (column + 0.5f) * cellWidth
                    var sum = 0f
                    for (offset in -1..1) {
                        val x = centerX + offset * cellWidth * 0.10f
                        val rim = (brightness(bitmap, x, y - cellWidth * 0.09f) +
                            brightness(bitmap, x, y + cellWidth * 0.09f)) / 2f
                        sum += rim - brightness(bitmap, x, y.toFloat())
                    }
                    sum / 3f
                }.median()
                band += y to contrast
                bestContrast = maxOf(bestContrast, contrast)
            }
            if (bestContrast < 0.08f) return null
            val strongest = band.filter { it.second >= bestContrast * 0.80f }
            gaps[row - 1] = strongest.sumOf { (it.first * it.second).toDouble() }.toFloat() /
                strongest.sumOf { it.second.toDouble() }.toFloat()
        }
        // Fit gap index 1..rows-1 = grid top + index * pitch for any supported size.
        val meanIndex = size.rows / 2f
        val meanY = gaps.average().toFloat()
        val denominator = gaps.indices.sumOf { ((it + 1 - meanIndex) * (it + 1 - meanIndex)).toDouble() }.toFloat()
        val pitch = gaps.indices.sumOf { ((it + 1 - meanIndex) * (gaps[it] - meanY)).toDouble() }.toFloat() / denominator
        val top = meanY - meanIndex * pitch
        if (pitch !in cellWidth * 0.96f..cellWidth * 1.04f || top < 0f ||
            top + pitch * size.rows > bitmap.height ||
            gaps.indices.any { abs(gaps[it] - (top + (it + 1) * pitch)) > cellWidth * 0.035f }
        ) return null
        return top to pitch
    }

    /** Dark gaps on both axes help distinguish grids with similar orb-color samples. */
    private fun gridGapSupport(bitmap: Bitmap, candidate: Candidate): Float {
        val size = candidate.size
        val cellWidth = candidate.width / size.columns
        val cellHeight = candidate.height / size.rows
        fun gap(vertical: Boolean, index: Int): Float {
            val count = if (vertical) size.rows else size.columns
            val pitch = if (vertical) cellWidth else cellHeight
            val origin = if (vertical) candidate.left else candidate.top
            val expected = origin + index * pitch
            var strongest = 0f
            for (offset in -3..3) {
                val at = expected + offset * pitch * 0.02f
                val contrast = FloatArray(count) { cross ->
                    val crossAt = if (vertical) candidate.top + (cross + 0.5f) * cellHeight
                        else candidate.left + (cross + 0.5f) * cellWidth
                    fun value(along: Float): Float = if (vertical) brightness(bitmap, along, crossAt)
                        else brightness(bitmap, crossAt, along)
                    (value(at - pitch * 0.09f) + value(at + pitch * 0.09f)) / 2f - value(at)
                }.median()
                strongest = maxOf(strongest, contrast)
            }
            return (strongest / 0.15f).coerceIn(0f, 1f)
        }
        val support = (1 until size.columns).sumOf { gap(true, it).toDouble() } +
            (1 until size.rows).sumOf { gap(false, it).toDouble() }
        return (support / (size.columns + size.rows - 2)).toFloat()
    }

    private fun sampleOrbColor(
        image: ClassifiedImage,
        centerX: Float,
        centerY: Float,
        cellSize: Float,
    ): OrbSample {
        val votes = IntArray(OrbColor.entries.size)
        var sampledPatches = 0
        // PAD draws the orb's color around its interior art. Sampling a small ring of
        // independent patches is less sensitive to that art and to the + marker at
        // the lower-right edge of enhanced orbs. Each patch gets one vote so a
        // particularly colorful patch cannot dominate the whole cell.
        val scale = image.sourcePixelsPerMapPixel
        val mappedCenterX = centerX / scale
        val mappedCenterY = centerY / scale
        val mappedCellSize = cellSize / scale
        // Hazard symbols share hues with ordinary orbs. Compare their center art
        // against both hazard and ordinary symbols before using the color ring.
        sampleHazard(image, centerX, centerY, cellSize)?.let { return it }
        val patchRadius = mappedCellSize * SAMPLE_PATCH_RADIUS_FRACTION
        val patchVotes = IntArray(OrbColor.entries.size)
        val step = (mappedCellSize * SAMPLE_STEP_FRACTION).toInt().coerceAtLeast(1)
        for ((offsetFractionX, offsetFractionY) in SAMPLE_PATCH_OFFSETS) {
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
                            patchVotes[it.ordinal].toFloat() / patchVotes.sum() >= PATCH_COLOR_VOTE_FRACTION
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
        if (classifiedPatches == 0 || votes[selected.ordinal].toFloat() / classifiedPatches < MIN_COLOR_VOTE_FRACTION) {
            return OrbSample(OrbColor.UNKNOWN, colorSupport)
        }
        return OrbSample(selected, colorSupport)
    }

    private fun classify(hsv: FloatArray): OrbColor? {
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

    /** Gold padlock at the upper-left: body, shackle, and dark keyhole. */
    internal fun hasLockedMarker(bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float): Boolean {
        val hsv = FloatArray(3)
        fun gold(x: Float, y: Float) = markerPatch(bitmap, x, y, cellSize, hsv) { hsv ->
            hsv[0] in 35f..70f && hsv[1] >= 0.20f && hsv[2] >= 0.60f
        }
        var dy = -0.30f
        while (dy <= -0.08f) {
            var dx = -0.44f
            while (dx <= -0.18f) {
                val x = centerX + dx * cellSize
                val y = centerY + dy * cellSize
                for (scale in LOCK_SCALES) {
                    fun at(ox: Float, oy: Float) = gold(x + ox * cellSize * scale, y + oy * cellSize * scale)
                    if (at(-0.075f, 0f) && at(0.075f, 0f) && at(0f, 0.07f) &&
                        at(-0.06f, -0.14f) && at(0.06f, -0.14f) && at(0f, -0.20f) &&
                        !at(0f, 0f) && !at(0f, -0.13f) &&
                        !at(-0.15f, -0.14f) && !at(0.15f, -0.14f)
                    ) return true
                }
                dx += 0.02f
            }
            dy += 0.02f
        }
        return false
    }

    private inline fun markerPatch(
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

    private data class Candidate(
        val size: BoardSize,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val colors: List<OrbColor>,
        val recognized: Int,
        val averageColorSupport: Float,
        val score: Float,
        val gridSupport: Float = 0f,
    ) {
        val complete: Boolean get() = recognized == size.cellCount
        fun betterThan(other: Candidate): Boolean = score > other.score
    }

    private fun sampleHazard(
        image: ClassifiedImage,
        centerX: Float,
        centerY: Float,
        cellSize: Float,
        refine: Boolean = false,
    ): OrbSample? {
        val mapScale = image.sourcePixelsPerMapPixel
        val pitch = cellSize / mapScale
        val x = centerX / mapScale - 0.5f
        val y = centerY / mapScale - 0.5f
        val values = FloatArray(OrbSymbolTemplates.SIDE * OrbSymbolTemplates.SIDE)
        fun descriptor(scale: Float, dx: Float, dy: Float): FloatArray {
            for (row in 0 until OrbSymbolTemplates.SIDE) for (column in 0 until OrbSymbolTemplates.SIDE) {
                values[row * OrbSymbolTemplates.SIDE + column] = image.luminanceAt(
                    x + (dx + (column - OrbSymbolTemplates.SIDE / 2) * OrbSymbolTemplates.SAMPLE_PITCH * scale) * pitch,
                    y + (dy + (row - OrbSymbolTemplates.SIDE / 2) * OrbSymbolTemplates.SAMPLE_PITCH * scale) * pitch,
                )
            }
            return values
        }
        OrbSymbolTemplates.classify(descriptor(1f, 0f, 0f))?.let { (color, support) ->
            return OrbSample(color, support)
        }
        if (!refine) return null
        // Orb art can sit off center and vary slightly in size even within one
        // screenshot. Keep the confidence and ordinary-symbol comparison while
        // searching a small neighborhood; thin route strokes can still leave
        // enough of the skull visible to match without removing overlay pixels.
        val scores = FloatArray(OrbColor.entries.size)
        for (scale in SYMBOL_SCALES) for (dx in SYMBOL_OFFSETS) for (dy in SYMBOL_OFFSETS) {
            OrbSymbolTemplates.accumulateScores(descriptor(scale, dx, dy), scores)
        }
        return OrbSymbolTemplates.selectHazard(scores, jammerThreshold = 0.65f)?.let { (color, support) ->
            OrbSample(color, support)
        }
    }

    private data class OrbSample(val color: OrbColor, val colorSupport: Float)

    private class ClassifiedImage private constructor(
        val width: Int,
        val height: Int,
        val sourcePixelsPerMapPixel: Int,
        private val colors: ByteArray,
        private val luminance: FloatArray,
    ) {
        fun colorAt(x: Int, y: Int): Int = colors[y * width + x].toInt()
        fun luminanceAt(x: Float, y: Float): Float {
            val px = x.coerceIn(0f, width - 1f)
            val py = y.coerceIn(0f, height - 1f)
            val left = px.toInt()
            val top = py.toInt()
            val right = minOf(left + 1, width - 1)
            val bottom = minOf(top + 1, height - 1)
            val dx = px - left
            val dy = py - top
            return (luminance[top * width + left] * (1 - dx) + luminance[top * width + right] * dx) * (1 - dy) +
                (luminance[bottom * width + left] * (1 - dx) + luminance[bottom * width + right] * dx) * dy
        }

        companion object {
            fun from(bitmap: Bitmap, classifyColor: (FloatArray) -> OrbColor?): ClassifiedImage {
                val width = (bitmap.width + MAP_SCALE - 1) / MAP_SCALE
                val height = (bitmap.height + MAP_SCALE - 1) / MAP_SCALE
                val colors = ByteArray(width * height) { NO_COLOR.toByte() }
                val luminance = FloatArray(width * height)
                val hsv = FloatArray(3)
                val rowsPerBatch = SOURCE_ROWS_PER_BATCH
                val sourceRows = IntArray(bitmap.width * rowsPerBatch)
                var sourceTop = 0
                while (sourceTop < bitmap.height) {
                    val batchHeight = minOf(rowsPerBatch, bitmap.height - sourceTop)
                    bitmap.getPixels(sourceRows, 0, bitmap.width, 0, sourceTop, bitmap.width, batchHeight)
                    var localY = 0
                    while (localY < batchHeight) {
                        val nextLocalY = minOf(localY + 1, batchHeight - 1)
                        val outputY = (sourceTop + localY) / MAP_SCALE
                        for (outputX in 0 until width) {
                            val sourceX = outputX * MAP_SCALE
                            var red = 0
                            var green = 0
                            var blue = 0
                            var count = 0
                            for (dy in localY..nextLocalY) {
                                for (dx in sourceX until minOf(sourceX + MAP_SCALE, bitmap.width)) {
                                    val pixel = sourceRows[dy * bitmap.width + dx]
                                    red += Color.red(pixel)
                                    green += Color.green(pixel)
                                    blue += Color.blue(pixel)
                                    count++
                                }
                            }
                            val averagedPixel = Color.rgb(red / count, green / count, blue / count)
                            luminance[outputY * width + outputX] =
                                (0.2126f * red + 0.7152f * green + 0.0722f * blue) / count
                            Color.colorToHSV(averagedPixel, hsv)
                            colors[outputY * width + outputX] =
                                (classifyColor(hsv)?.ordinal ?: NO_COLOR).toByte()
                        }
                        localY += MAP_SCALE
                    }
                    sourceTop += batchHeight
                }
                return ClassifiedImage(width, height, MAP_SCALE, colors, luminance)
            }
        }
    }

    private companion object {
        const val MIN_CENTER_Y_MARGIN = 0.05f
        const val CENTER_Y_STEP = 0.006f
        // Calibrated against the orb centers in the full-resolution PAD
        // capture fixture; this keeps vertical candidate scoring centered on
        // the actual orb grid rather than the color-support plateau above it.
        const val PREFERRED_CENTER_Y = 0.790f
        const val VARIETY_BONUS = 0.45f
        const val COLOR_SUPPORT_WEIGHT = 18f
        const val CENTER_PENALTY = 4f
        const val FIRE_CORE_HUE_MAX = 24f
        const val FIRE_ORANGE_HUE_MAX = 40f
        const val FIRE_ORANGE_MIN_BRIGHTNESS = 0.54f
        const val LIGHT_HUE_MAX = 75f
        const val RED_WRAP_HUE_MIN = 350f
        const val SAMPLE_PATCH_RADIUS_FRACTION = 0.025f
        const val SAMPLE_STEP_FRACTION = 0.01f
        const val PATCH_COLOR_VOTE_FRACTION = 0.55f
        // Hearts can have purple outer borders. Two interior rings let their
        // pink body contribute without relying on the central orb artwork.
        val SAMPLE_PATCH_OFFSETS = arrayOf(
            0f to -1f,
            0.7f to -0.7f,
            1f to 0f,
            0.7f to 0.7f,
            0f to 1f,
            -0.7f to 0.7f,
            -1f to 0f,
            -0.7f to -0.7f,
        ).flatMap { (x, y) -> listOf(0.22f, 0.30f).map { x * it to y * it } }
        const val MIN_COLOR_VOTE_FRACTION = 0.38f
        const val MIN_SATURATION = 0.28f
        const val MIN_BRIGHTNESS = 0.20f
        const val MIN_AVERAGE_COLOR_SUPPORT = 0.30f
        const val RECOGNITION_WEIGHT = 30f
        const val GRID_GAP_WEIGHT = 12f
        const val MIN_GRID_GAP_SUPPORT = 0.15f
        const val MIN_FRAME_INSET_FRACTION = 0.015f
        const val MIN_SIZE_SCORE_MARGIN = 1f
        const val MAP_SCALE = 2
        val SYMBOL_SCALES = floatArrayOf(0.9f, 1f, 1.1f)
        val SYMBOL_OFFSETS = floatArrayOf(-0.08f, -0.04f, 0f, 0.04f, 0.08f)
        const val SOURCE_ROWS_PER_BATCH = 16
        const val NO_COLOR = -1
        const val ENHANCED_MARKER_MIN_X_OFFSET = 0.12f
        val MINUS_HALF_WIDTHS = floatArrayOf(0.05f, 0.065f, 0.08f)
        val MINUS_HALF_HEIGHTS = floatArrayOf(0.035f, 0.045f, 0.055f, 0.065f)
        val LOCK_SCALES = floatArrayOf(0.85f, 1f, 1.15f)
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
