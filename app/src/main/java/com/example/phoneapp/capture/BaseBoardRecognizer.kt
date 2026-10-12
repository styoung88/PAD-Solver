package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.game.GameVariant
import kotlin.math.abs
import kotlin.math.roundToInt

/** Fits a grid using the selected variant's layout and orb evidence. */
internal abstract class BaseBoardRecognizer(
    private val game: GameVariant,
    protected val geometry: BoardGeometry?,
    private val orbRecognizer: BaseOrbRecognizer,
) : BoardRecognizer {
    protected open fun initialHorizontalBounds(bitmap: Bitmap): Pair<Float, Float> {
        val geometry = checkNotNull(geometry)
        return bitmap.width * geometry.leftFraction to bitmap.width * geometry.widthFraction
    }

    final override fun recognize(bitmap: Bitmap): BoardRecognition {
        val geometry = geometry ?: return BoardRecognition.Unsupported(game)
        if (!orbRecognizer.available) return BoardRecognition.Unsupported(game)
        if (bitmap.width < geometry.minimumCaptureWidth || bitmap.height < geometry.minimumCaptureHeight ||
            bitmap.height < bitmap.width * geometry.minimumAspectRatio) {
            return BoardRecognition.Uncertain(0)
        }
        val colorMap = orbRecognizer.prepareImage(bitmap)
        val candidates = geometry.sizes
            .mapNotNull { findCandidate(bitmap, colorMap, it) }
            .sortedByDescending { it.score }
        val usable = candidates.filter { it.complete &&
            it.averageColorSupport >= geometry.minimumAverageColorSupport && it.gridSupport >= geometry.minimumGridGapSupport &&
            it.top >= 0f && it.top + it.height <= bitmap.height }
        val candidate = usable.firstOrNull() ?: return BoardRecognition.Uncertain(candidates.firstOrNull()?.recognized ?: 0)
        // Do not replace a stronger, partially obscured grid with a weaker
        // region just because all of that region's colors happened to classify.
        if (candidates.any { !it.complete && it.score > candidate.score &&
                it.averageColorSupport >= geometry.minimumAverageColorSupport && it.gridSupport >= geometry.minimumGridGapSupport
            }
        ) return BoardRecognition.Uncertain(candidates.first().recognized)
        if (usable.getOrNull(1)?.let {
                candidate.score - it.score < geometry.minimumSizeScoreMargin
            } == true
        ) return BoardRecognition.Uncertain(candidate.recognized)

        val size = candidate.size
        val result = BoardRecognition.Recognized(
            board = Board(
                columns = size.columns,
                rows = size.rows,
                orbs = candidate.colors.mapIndexed { index, sampledColor ->
                    val row = index / size.columns
                    val column = index % size.columns
                    val centerX = candidate.left + (column + 0.5f) * candidate.width / size.columns
                    val centerY = candidate.top + (row + 0.5f) * candidate.height / size.rows
                    val cellSize = candidate.width / size.columns
                    // Refine hazards only after fitting the grid so alignment tolerance
                    // does not flatten the evidence used to locate its rows and columns.
                    val color = orbRecognizer.refineColor(colorMap, centerX, centerY, cellSize, sampledColor)
                    Orb(color, orbRecognizer.detectModifiers(bitmap, centerX, centerY, cellSize, color, colorMap))
                },
            ),
            boardBounds = RectF(candidate.left, candidate.top,
                candidate.left + candidate.width, candidate.top + candidate.height),
            captureWidth = bitmap.width,
            captureHeight = bitmap.height,
        )
        val recognized = result.board.orbs.count { it.color != OrbColor.UNKNOWN }
        return if (recognized == size.cellCount) result else BoardRecognition.Uncertain(recognized)
    }

    protected open fun findCandidate(bitmap: Bitmap, colorMap: ClassifiedImage, size: BoardSize): Candidate? {
        val geometry = checkNotNull(geometry)
        var best: Candidate? = null
        // Start at the variant's search bounds. Only measured gaps may narrow them.
        val (boardLeft, boardWidth) = initialHorizontalBounds(bitmap)
        val cellSize = boardWidth / size.columns
        val boardHeight = cellSize * size.rows
        val minCenterY = boardHeight / 2f + bitmap.height * geometry.minimumCenterYMargin
        // A framed board can have a smaller pitch than the full-width first pass.
        // Allow that first pass near the bottom edge; final fitted bounds must fit.
        val maxCenterY = bitmap.height - boardHeight / 2f + cellSize * geometry.bottomSearchOvershootCells
        val coarseYStep = (bitmap.height * geometry.centerYStep).coerceAtLeast(1f)

        fun candidateAt(centerY: Float, cellHeight: Float = cellSize,
            left: Float = boardLeft, width: Float = boardWidth, recoverUnknown: Boolean = false): Candidate {
            val height = cellHeight * size.rows
            val top = centerY - height / 2f
            val cellWidth = width / size.columns
            val samples = ArrayList<OrbSample>(size.cellCount)
            for (row in 0 until size.rows) for (column in 0 until size.columns) {
                val x = left + (column + 0.5f) * cellWidth
                val y = top + (row + 0.5f) * cellHeight
                val sample = orbRecognizer.sampleOrbColor(colorMap, x, y, cellWidth)
                samples += if (recoverUnknown && sample.color == OrbColor.UNKNOWN) {
                    orbRecognizer.recoverOrbColor(colorMap, x, y, cellWidth) ?: sample
                } else sample
            }
            val colors = samples.map(OrbSample::color)
            val recognized = colors.count { it != OrbColor.UNKNOWN }
            val variety = colors.asSequence().filter { it != OrbColor.UNKNOWN }.distinct().count()
            val averageColorSupport = samples.sumOf { it.colorSupport.toDouble() }.toFloat() / samples.size
            val centerPenalty = abs(centerY - bitmap.height * geometry.preferredCenterY) / bitmap.height
            // Use fractions when comparing sizes; 42 sampled cells must not win
            // simply because there are more of them than on a 20-cell board.
            val score = recognized.toFloat() / size.cellCount * geometry.recognitionWeight +
                variety * geometry.varietyBonus + averageColorSupport * geometry.colorSupportWeight -
                centerPenalty * geometry.centerPenalty
            val candidate = Candidate(size, left, top, width, height, colors, recognized, averageColorSupport, score)
            val support = gridGapSupport(bitmap, candidate)
            return candidate.copy(gridSupport = support, score = score + support * geometry.gridGapWeight)
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
                it.averageColorSupport >= geometry.minimumAverageColorSupport &&
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

    protected open fun fitColumnGaps(bitmap: Bitmap, candidate: Candidate): Pair<Float, Float>? {
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
                    sum / 3f * checkNotNull(geometry).gapPolarity
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
        val (searchLeft, searchWidth) = initialHorizontalBounds(bitmap)
        val leftInset = left - searchLeft
        val rightInset = searchLeft + searchWidth - (left + size.columns * pitch)
        if (pitch !in cellWidth * 0.94f..cellWidth * 1.005f || leftInset < -1.5f || rightInset < -1.5f ||
            abs(leftInset - rightInset) > cellWidth * 0.10f ||
            gaps.indices.any { abs(gaps[it] - (left + (it + 1) * pitch)) > cellWidth * 0.06f }
        ) return null
        // Small inferred insets can come from orb art. Retain full-width centers
        // unless the measured frame is wide enough to distinguish reliably.
        if (maxOf(leftInset, rightInset) < searchWidth * checkNotNull(geometry).minimumFrameInsetFraction) return searchLeft to cellWidth
        return left to pitch
    }

    protected open fun fitRowGaps(bitmap: Bitmap, candidate: Candidate): Pair<Float, Float>? {
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
                    val centerX = candidate.left + (column + checkNotNull(geometry).rowGapSampleFraction) * cellWidth
                    var sum = 0f
                    for (offset in -1..1) {
                        val x = centerX + offset * cellWidth * 0.10f
                        val rim = (brightness(bitmap, x, y - cellWidth * 0.09f) +
                            brightness(bitmap, x, y + cellWidth * 0.09f)) / 2f
                        sum += rim - brightness(bitmap, x, y.toFloat())
                    }
                    sum / 3f * checkNotNull(geometry).gapPolarity
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

    /** Contrasting grid lines on both axes distinguish similar orb-color samples. */
    protected open fun gridGapSupport(bitmap: Bitmap, candidate: Candidate): Float {
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
                        else candidate.left + (cross + checkNotNull(geometry).rowGapSampleFraction) * cellWidth
                    fun value(along: Float): Float = if (vertical) brightness(bitmap, along, crossAt)
                        else brightness(bitmap, crossAt, along)
                    (value(at - pitch * 0.09f) + value(at + pitch * 0.09f)) / 2f - value(at)
                }.median()
                strongest = maxOf(strongest, contrast * checkNotNull(geometry).gapPolarity)
            }
            return (strongest / 0.15f).coerceIn(0f, 1f)
        }
        val support = (1 until size.columns).sumOf { gap(true, it).toDouble() } +
            (1 until size.rows).sumOf { gap(false, it).toDouble() }
        return (support / (size.columns + size.rows - 2)).toFloat()
    }

    protected data class Candidate(
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
}
