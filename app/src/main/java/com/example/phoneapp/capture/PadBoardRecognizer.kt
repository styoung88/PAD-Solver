package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.example.phoneapp.board.Board
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

/** First-pass recognizer for the centered, standard 6x5 PAD board. */
class PadBoardRecognizer {
    fun recognize(bitmap: Bitmap): BoardRecognition {
        if (bitmap.width < 200 || bitmap.height < 300) return BoardRecognition.Uncertain(0)
        val colorMap = ClassifiedImage.from(bitmap, ::classify)

        var best: Candidate? = null
        // PAD's 6-column orb grid spans the screenshot width. Searching for a
        // narrower "best" rectangle compressed the column spacing: the middle
        // orbs still looked plausible while S and E drifted toward each other.
        // Anchor horizontal cell centers to the known grid width instead.
        val boardWidth = bitmap.width.toFloat()
        val cellSize = boardWidth / Board.DEFAULT_COLUMNS
        val boardHeight = cellSize * Board.DEFAULT_ROWS
        val minCenterY = boardHeight / 2f + bitmap.height * MIN_CENTER_Y_MARGIN
        val maxCenterY = bitmap.height - boardHeight / 2f - bitmap.height * MAX_CENTER_Y_MARGIN
        val coarseYStep = (bitmap.height * CENTER_Y_STEP).coerceAtLeast(1f)

        fun candidateAt(centerY: Float, cellHeight: Float = cellSize): Candidate {
            val left = 0f
            val height = cellHeight * Board.DEFAULT_ROWS
            val top = centerY - height / 2f
            val samples = ArrayList<OrbSample>(Board.DEFAULT_COLUMNS * Board.DEFAULT_ROWS)
            for (row in 0 until Board.DEFAULT_ROWS) {
                for (column in 0 until Board.DEFAULT_COLUMNS) {
                    val x = left + (column + 0.5f) * cellSize
                    val y = top + (row + 0.5f) * cellHeight
                    samples += sampleOrbColor(colorMap, x, y, cellSize)
                }
            }
            val colors = samples.map(OrbSample::color)
            val recognized = colors.count { it != OrbColor.UNKNOWN }
            val variety = colors.asSequence().filter { it != OrbColor.UNKNOWN }.distinct().count()
            val averageColorSupport = samples.sumOf { it.colorSupport.toDouble() }.toFloat() / samples.size
            val centerPenalty = abs(centerY - bitmap.height * PREFERRED_CENTER_Y) / bitmap.height
            val score = recognized + variety * VARIETY_BONUS + averageColorSupport * COLOR_SUPPORT_WEIGHT -
                centerPenalty * CENTER_PENALTY
            return Candidate(left, top, boardWidth, height, colors, recognized, averageColorSupport, score)
        }

        // Coarse vertical scan locates the board, followed by a pixel-level
        // refinement so the row centers do not inherit the old 0.6%-screen
        // quantization error.
        var centerY = minCenterY
        while (centerY <= maxCenterY) {
            val candidate = candidateAt(centerY)
            val currentBest = best
            if (currentBest == null || candidate.score > currentBest.score) best = candidate
            centerY += coarseYStep
        }
        val coarseBest = best ?: return BoardRecognition.Uncertain(0)
        val coarseCenterY = coarseBest.top + coarseBest.height / 2f
        var refinedCenterY = (coarseCenterY - coarseYStep).coerceAtLeast(minCenterY)
        val refineUntil = (coarseCenterY + coarseYStep).coerceAtMost(maxCenterY)
        while (refinedCenterY <= refineUntil) {
            val candidate = candidateAt(refinedCenterY)
            if (candidate.score > checkNotNull(best).score) best = candidate
            refinedCenterY += 1f
        }

        val colorCandidate = best ?: return BoardRecognition.Uncertain(0)
        // Color support locates the grid but cannot distinguish small spacing
        // changes inside an orb. Fit the four dark row gaps independently; a
        // resized screenshot can have a different vertical and horizontal pitch.
        val fittedRows = fitRowGaps(bitmap, colorCandidate)
        val candidate = fittedRows?.let { (top, pitch) ->
            candidateAt(top + pitch * Board.DEFAULT_ROWS / 2f, pitch).takeIf {
                it.recognized == MIN_RECOGNIZED_CELLS &&
                    it.averageColorSupport >= MIN_AVERAGE_COLOR_SUPPORT &&
                    it.averageColorSupport >= colorCandidate.averageColorSupport - 0.10f
            }
        } ?: colorCandidate
        if (candidate.recognized < MIN_RECOGNIZED_CELLS ||
            candidate.averageColorSupport < MIN_AVERAGE_COLOR_SUPPORT
        ) {
            return BoardRecognition.Uncertain(candidate.recognized)
        }

        return BoardRecognition.Recognized(
            board = Board(
                orbs = candidate.colors.mapIndexed { index, color ->
                    val row = index / Board.DEFAULT_COLUMNS
                    val column = index % Board.DEFAULT_COLUMNS
                    val centerX = candidate.left + (column + 0.5f) * candidate.width / Board.DEFAULT_COLUMNS
                    val centerY = candidate.top + (row + 0.5f) * candidate.height / Board.DEFAULT_ROWS
                    val modifiers = if (color != OrbColor.UNKNOWN && hasEnhancedMarker(
                            bitmap,
                            centerX,
                            centerY,
                            candidate.width / Board.DEFAULT_COLUMNS,
                        )
                    ) {
                        setOf(OrbModifier.ENHANCED)
                    } else {
                        emptySet()
                    }
                    Orb(color, modifiers)
                },
            ),
            boardBounds = RectF(
                candidate.left,
                candidate.top,
                candidate.left + candidate.width,
                candidate.top + candidate.height,
            ),
            captureWidth = bitmap.width,
            captureHeight = bitmap.height,
        )
    }

    private fun fitRowGaps(bitmap: Bitmap, candidate: Candidate): Pair<Float, Float>? {
        val cellWidth = candidate.width / Board.DEFAULT_COLUMNS
        val gaps = FloatArray(Board.DEFAULT_ROWS - 1)
        fun brightness(x: Float, y: Float): Float {
            val pixel = bitmap.getPixel(x.roundToInt().coerceIn(0, bitmap.width - 1),
                y.roundToInt().coerceIn(0, bitmap.height - 1))
            return (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) +
                0.0722f * Color.blue(pixel)) / 255f
        }
        for (row in 1 until Board.DEFAULT_ROWS) {
            val expected = candidate.top + row * candidate.height / Board.DEFAULT_ROWS
            var bestContrast = 0f
            val searchRadius = cellWidth * 0.12f
            val band = ArrayList<Pair<Int, Float>>()
            for (y in (expected - searchRadius).roundToInt()..(expected + searchRadius).roundToInt()) {
                val contrasts = FloatArray(Board.DEFAULT_COLUMNS) { column ->
                    val centerX = candidate.left + (column + 0.5f) * cellWidth
                    var contrast = 0f
                    for (offset in -1..1) {
                        val x = centerX + offset * cellWidth * 0.10f
                        val rim = (brightness(x, y - cellWidth * 0.09f) +
                            brightness(x, y + cellWidth * 0.09f)) / 2f
                        contrast += rim - brightness(x, y.toFloat())
                    }
                    contrast / 3f
                }.apply { sort() }
                // The median resists route strokes and enhanced/locked orb art.
                val contrast = (contrasts[2] + contrasts[3]) / 2f
                band += y to contrast
                bestContrast = maxOf(bestContrast, contrast)
            }
            if (bestContrast < 0.08f) return null
            // Center the dark band rather than choosing one noisy pixel on an
            // orb's brighter upper rim, which biases later rows upward.
            val strongest = band.filter { it.second >= bestContrast * 0.80f }
            gaps[row - 1] = strongest.sumOf { (it.first * it.second).toDouble() }.toFloat() /
                strongest.sumOf { it.second.toDouble() }.toFloat()
        }
        // Least-squares fit: gap index 1..4 = grid top + index * row pitch.
        val meanY = gaps.average().toFloat()
        val pitch = gaps.indices.sumOf { ((it + 1 - 2.5f) * (gaps[it] - meanY)).toDouble() }.toFloat() / 5f
        val top = meanY - 2.5f * pitch
        if (pitch !in cellWidth * 0.96f..cellWidth * 1.04f || top < 0f ||
            top + pitch * Board.DEFAULT_ROWS > bitmap.height ||
            gaps.indices.any { abs(gaps[it] - (top + (it + 1) * pitch)) > cellWidth * 0.035f }
        ) return null
        return top to pitch
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
        val patchOffset = mappedCellSize * SAMPLE_RING_OFFSET_FRACTION
        val patchVotes = IntArray(OrbColor.entries.size)
        val step = (mappedCellSize * SAMPLE_STEP_FRACTION).toInt().coerceAtLeast(1)
        for ((directionX, directionY) in SAMPLE_PATCH_DIRECTIONS) {
            patchVotes.fill(0)
            val offsetX = directionX * patchOffset
            val offsetY = directionY * patchOffset
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
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val colors: List<OrbColor>,
        val recognized: Int,
        val averageColorSupport: Float,
        val score: Float,
    )

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
        const val MAX_CENTER_Y_MARGIN = 0.015f
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
        const val SAMPLE_RING_OFFSET_FRACTION = 0.30f
        const val SAMPLE_STEP_FRACTION = 0.01f
        const val PATCH_COLOR_VOTE_FRACTION = 0.55f
        val SAMPLE_PATCH_DIRECTIONS = arrayOf(
            0f to -1f,
            0.7f to -0.7f,
            1f to 0f,
            0.7f to 0.7f,
            0f to 1f,
            -0.7f to 0.7f,
            -1f to 0f,
            -0.7f to -0.7f,
        )
        const val MIN_COLOR_VOTE_FRACTION = 0.38f
        const val MIN_SATURATION = 0.28f
        const val MIN_BRIGHTNESS = 0.20f
        const val MIN_AVERAGE_COLOR_SUPPORT = 0.30f
        const val MIN_RECOGNIZED_CELLS = Board.DEFAULT_COLUMNS * Board.DEFAULT_ROWS
        const val MAP_SCALE = 2
        const val SOURCE_ROWS_PER_BATCH = 16
        const val NO_COLOR = -1
        const val ENHANCED_MARKER_MIN_X_OFFSET = 0.12f
        const val ENHANCED_MARKER_MAX_X_OFFSET = 0.38f
        const val ENHANCED_MARKER_MIN_Y_OFFSET = 0.08f
        const val ENHANCED_MARKER_MAX_Y_OFFSET = 0.52f
        const val ENHANCED_MARKER_OFFSET_STEP = 0.04f
        const val ENHANCED_MARKER_SAMPLE_RADIUS = 0.014f
        const val ENHANCED_GOLD_MIN_SATURATION = 0.88f
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
