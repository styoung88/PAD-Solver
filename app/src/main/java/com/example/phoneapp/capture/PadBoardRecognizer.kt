package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import kotlin.math.abs

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

        var best: Candidate? = null
        val minWidth = bitmap.width * MIN_BOARD_WIDTH_FRACTION
        val maxWidth = bitmap.width * MAX_BOARD_WIDTH_FRACTION
        val widthStep = bitmap.width * BOARD_WIDTH_STEP
        var boardWidth = minWidth
        while (boardWidth <= maxWidth) {
            val cellSize = boardWidth / Board.DEFAULT_COLUMNS
            val boardHeight = cellSize * Board.DEFAULT_ROWS
            val minCenterY = boardHeight / 2f + bitmap.height * MIN_CENTER_Y_MARGIN
            val maxCenterY = bitmap.height - boardHeight / 2f - bitmap.height * MAX_CENTER_Y_MARGIN
            val yStep = (bitmap.height * CENTER_Y_STEP).coerceAtLeast(1f)
            var centerY = minCenterY
            while (centerY <= maxCenterY) {
                val left = (bitmap.width - boardWidth) / 2f
                val top = centerY - boardHeight / 2f
                val colors = ArrayList<OrbColor>(Board.DEFAULT_COLUMNS * Board.DEFAULT_ROWS)
                for (row in 0 until Board.DEFAULT_ROWS) {
                    for (column in 0 until Board.DEFAULT_COLUMNS) {
                        val x = left + (column + 0.5f) * cellSize
                        val y = top + (row + 0.5f) * cellSize
                        colors += sampleOrbColor(bitmap, x, y, cellSize)
                    }
                }
                val recognized = colors.count { it != OrbColor.UNKNOWN }
                val variety = colors.asSequence().filter { it != OrbColor.UNKNOWN }.distinct().count()
                val centerPenalty = abs(centerY - bitmap.height * PREFERRED_CENTER_Y) / bitmap.height
                val widthPenalty = abs(boardWidth / bitmap.width - PREFERRED_WIDTH_FRACTION)
                val score = recognized + variety * VARIETY_BONUS - centerPenalty * CENTER_PENALTY -
                    widthPenalty * WIDTH_PENALTY
                val candidate = Candidate(left, top, boardWidth, boardHeight, colors, recognized, score)
                if (best == null || candidate.score > best.score) best = candidate
                centerY += yStep
            }
            boardWidth += widthStep
        }

        val candidate = best ?: return BoardRecognition.Uncertain(0)
        if (candidate.recognized < MIN_RECOGNIZED_CELLS ||
            candidate.colors.filter { it != OrbColor.UNKNOWN }.distinct().size < MIN_COLOR_VARIETY
        ) {
            return BoardRecognition.Uncertain(candidate.recognized)
        }

        return BoardRecognition.Recognized(
            board = Board(orbs = candidate.colors.map(::Orb)),
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

    private fun sampleOrbColor(bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float): OrbColor {
        val votes = IntArray(OrbColor.entries.size)
        val radius = cellSize * SAMPLE_RADIUS_FRACTION
        val step = (cellSize * SAMPLE_STEP_FRACTION).toInt().coerceAtLeast(1)
        val hsv = FloatArray(3)
        var y = (centerY - radius).toInt().coerceAtLeast(0)
        val bottom = (centerY + radius).toInt().coerceAtMost(bitmap.height - 1)
        val left = (centerX - radius).toInt().coerceAtLeast(0)
        val right = (centerX + radius).toInt().coerceAtMost(bitmap.width - 1)
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                classify(hsv)?.let { votes[it.ordinal]++ }
                x += step
            }
            y += step
        }
        val selected = OrbColor.entries
            .filter { it != OrbColor.UNKNOWN }
            .maxByOrNull { votes[it.ordinal] } ?: return OrbColor.UNKNOWN
        val classifiedVotes = votes.sum()
        if (classifiedVotes == 0 || votes[selected.ordinal].toFloat() / classifiedVotes < MIN_COLOR_VOTE_FRACTION) {
            return OrbColor.UNKNOWN
        }
        return selected
    }

    private fun classify(hsv: FloatArray): OrbColor? {
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        if (saturation < MIN_SATURATION || value < MIN_BRIGHTNESS || value > MAX_BRIGHTNESS) return null
        return when {
            hue < 18f || hue >= 350f -> OrbColor.FIRE
            hue < 72f -> OrbColor.LIGHT
            hue >= 320f -> OrbColor.HEART
            hue < 166f -> OrbColor.WOOD
            hue < 252f -> OrbColor.WATER
            hue < 310f -> OrbColor.DARK
            hue < 320f -> OrbColor.DARK
            else -> null
        }
    }

    private data class Candidate(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val colors: List<OrbColor>,
        val recognized: Int,
        val score: Float,
    )

    private companion object {
        const val MIN_BOARD_WIDTH_FRACTION = 0.68f
        const val MAX_BOARD_WIDTH_FRACTION = 0.96f
        const val BOARD_WIDTH_STEP = 0.025f
        const val MIN_CENTER_Y_MARGIN = 0.13f
        const val MAX_CENTER_Y_MARGIN = 0.13f
        const val CENTER_Y_STEP = 0.006f
        const val PREFERRED_CENTER_Y = 0.48f
        const val PREFERRED_WIDTH_FRACTION = 0.88f
        const val VARIETY_BONUS = 0.45f
        const val CENTER_PENALTY = 5f
        const val WIDTH_PENALTY = 1.2f
        const val SAMPLE_RADIUS_FRACTION = 0.18f
        const val SAMPLE_STEP_FRACTION = 0.04f
        const val MIN_COLOR_VOTE_FRACTION = 0.38f
        const val MIN_SATURATION = 0.28f
        const val MIN_BRIGHTNESS = 0.16f
        const val MAX_BRIGHTNESS = 0.98f
        const val MIN_RECOGNIZED_CELLS = Board.DEFAULT_COLUMNS * Board.DEFAULT_ROWS
        const val MIN_COLOR_VARIETY = 4
    }
}
