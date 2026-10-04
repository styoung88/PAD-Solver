package com.example.phoneapp.solver

import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbMatch

/** PAD match formations that can be required by a solve goal. */
enum class MatchPattern(val displayName: String) {
    ROW("Row"),
    TPA("TPA"),
    L_SHAPE("L shape"),
    CROSS("Cross"),
    VDP("VDP (3×3 square)");

    fun countMatches(analysis: BoardAnalysis, color: OrbColor? = null): Int =
        analysis.allMatches.sumOf { match ->
            if (color != null && match.color != color) 0 else countIn(match)
        }

    private fun countIn(match: OrbMatch): Int = when (this) {
        ROW -> countRows(match)
        TPA -> if (match.size == 4) 1 else 0
        L_SHAPE -> if (match.positions.isLShape()) 1 else 0
        CROSS -> if (match.positions.isCross()) 1 else 0
        VDP -> if (match.positions.isThreeByThreeSquare()) 1 else 0
    }

    private fun countRows(match: OrbMatch): Int = match.positions
        .groupBy(BoardPosition::row)
        .values
        .count { cells ->
            val columns = cells.map(BoardPosition::column).sorted()
            var runLength = 1
            var hasRow = false
            for ((left, right) in columns.zipWithNext()) {
                runLength = if (right == left + 1) runLength + 1 else 1
                if (runLength >= 6) hasRow = true
            }
            hasRow
        }

    private fun Set<BoardPosition>.isLShape(): Boolean {
        if (size != 5) return false
        for (corner in this) {
            for (vertical in listOf(-1, 1)) {
                for (horizontal in listOf(-1, 1)) {
                    val shape = setOf(
                        corner,
                        BoardPosition(corner.row + vertical, corner.column),
                        BoardPosition(corner.row + 2 * vertical, corner.column),
                        BoardPosition(corner.row, corner.column + horizontal),
                        BoardPosition(corner.row, corner.column + 2 * horizontal),
                    )
                    if (this == shape) return true
                }
            }
        }
        return false
    }

    private fun Set<BoardPosition>.isCross(): Boolean {
        if (size != 5) return false
        return any { center ->
            this == setOf(
                center,
                BoardPosition(center.row - 1, center.column),
                BoardPosition(center.row + 1, center.column),
                BoardPosition(center.row, center.column - 1),
                BoardPosition(center.row, center.column + 1),
            )
        }
    }

    private fun Set<BoardPosition>.isThreeByThreeSquare(): Boolean {
        if (size != 9) return false
        val top = minOf { it.row }
        val left = minOf { it.column }
        return all { it.row in top..(top + 2) && it.column in left..(left + 2) } &&
            (top..(top + 2)).all { row -> (left..(left + 2)).all { column ->
                BoardPosition(row, column) in this
            } }
    }
}
