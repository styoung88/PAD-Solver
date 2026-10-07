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
    fun recognize(bitmap: Bitmap, sizeOverride: BoardSize? = null): BoardRecognition {
        // PAD captures include the portrait battle screen above the grid. Square
        // board fragments can otherwise mistake enemy artwork for missing rows.
        if (bitmap.width < 200 || bitmap.height < 300 || bitmap.height < bitmap.width * 1.2f) {
            return BoardRecognition.Uncertain(0)
        }
        val colorMap = ClassifiedImage.from(bitmap, ::classify)
        val candidates = (sizeOverride?.let(::listOf) ?: BoardSize.entries)
            .mapNotNull { findCandidate(bitmap, colorMap, it) }
            .sortedByDescending { it.score }
        val usable = candidates.filter { it.complete &&
            it.averageColorSupport >= MIN_AVERAGE_COLOR_SUPPORT && it.gridSupport >= MIN_GRID_GAP_SUPPORT &&
            it.top >= 0f && it.top + it.height <= bitmap.height }
        val candidate = usable.firstOrNull() ?: return BoardRecognition.Uncertain(candidates.firstOrNull()?.recognized ?: 0)
        if (sizeOverride == null && usable.getOrNull(1)?.let {
                candidate.score - it.score < MIN_SIZE_SCORE_MARGIN
            } == true
        ) return BoardRecognition.Uncertain(candidate.recognized)

        val size = candidate.size
        return BoardRecognition.Recognized(
            board = Board(
                columns = size.columns,
                rows = size.rows,
                orbs = candidate.colors.mapIndexed { index, color ->
                    val row = index / size.columns
                    val column = index % size.columns
                    val centerX = candidate.left + (column + 0.5f) * candidate.width / size.columns
                    val centerY = candidate.top + (row + 0.5f) * candidate.height / size.rows
                    val modifiers = if (hasEnhancedMarker(bitmap, centerX, centerY, candidate.width / size.columns)) {
                        setOf(OrbModifier.ENHANCED)
                    } else emptySet()
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
            left: Float = 0f, width: Float = boardWidth): Candidate {
            val height = cellHeight * size.rows
            val top = centerY - height / 2f
            val cellWidth = width / size.columns
            val samples = ArrayList<OrbSample>(size.cellCount)
            for (row in 0 until size.rows) for (column in 0 until size.columns) {
                samples += sampleOrbColor(colorMap, left + (column + 0.5f) * cellWidth,
                    top + (row + 0.5f) * cellHeight, cellWidth)
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
                it.recognized == size.cellCount &&
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
                left, pitch * size.columns).takeIf { it.complete }
        } ?: candidate
        return refined
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

    /** Detects the small gold + glyph near the lower-right of an enhanced orb. */
    private fun hasEnhancedMarker(bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float): Boolean {
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
                        if (hasGoldPatch(
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
                        if (hasGoldPatch(
                                bitmap,
                                markerCenterX + offsetX * cellSize * armSpacing,
                                markerCenterY + offsetY * cellSize * armSpacing,
                                sampleRadius,
                                patchStep,
                                hsv,
                            )
                        ) goldControlPatches++
                    }
                    if (goldPatches >= MIN_ENHANCED_PLUS_PATCHES &&
                        goldControlPatches <= MAX_ENHANCED_CONTROL_PATCHES
                    ) return true
                }
                markerOffsetX += ENHANCED_MARKER_OFFSET_STEP
            }
            markerOffsetY += ENHANCED_MARKER_OFFSET_STEP
        }
        return false
    }

    private fun hasGoldPatch(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        radius: Int,
        step: Int,
        hsv: FloatArray,
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
                if (hsv[0] in ENHANCED_GOLD_HUE_RANGE &&
                    hsv[1] >= ENHANCED_GOLD_MIN_SATURATION && hsv[2] >= ENHANCED_GOLD_MIN_BRIGHTNESS
                ) goldPixels++
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
        fun betterThan(other: Candidate): Boolean = if (complete != other.complete) complete else score > other.score
    }

    private data class OrbSample(val color: OrbColor, val colorSupport: Float)

    private class ClassifiedImage private constructor(
        val width: Int,
        val height: Int,
        val sourcePixelsPerMapPixel: Int,
        private val colors: ByteArray,
    ) {
        fun colorAt(x: Int, y: Int): Int = colors[y * width + x].toInt()

        companion object {
            fun from(bitmap: Bitmap, classifyColor: (FloatArray) -> OrbColor?): ClassifiedImage {
                val width = (bitmap.width + MAP_SCALE - 1) / MAP_SCALE
                val height = (bitmap.height + MAP_SCALE - 1) / MAP_SCALE
                val colors = ByteArray(width * height) { NO_COLOR.toByte() }
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
                            Color.colorToHSV(averagedPixel, hsv)
                            colors[outputY * width + outputX] =
                                (classifyColor(hsv)?.ordinal ?: NO_COLOR).toByte()
                        }
                        localY += MAP_SCALE
                    }
                    sourceTop += batchHeight
                }
                return ClassifiedImage(width, height, MAP_SCALE, colors)
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
        const val SOURCE_ROWS_PER_BATCH = 16
        const val NO_COLOR = -1
        const val ENHANCED_MARKER_MIN_X_OFFSET = 0.12f
        const val ENHANCED_MARKER_MAX_X_OFFSET = 0.38f
        const val ENHANCED_MARKER_MIN_Y_OFFSET = 0.08f
        const val ENHANCED_MARKER_MAX_Y_OFFSET = 0.52f
        const val ENHANCED_MARKER_OFFSET_STEP = 0.04f
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
