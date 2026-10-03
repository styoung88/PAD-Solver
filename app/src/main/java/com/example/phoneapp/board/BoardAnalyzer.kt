package com.example.phoneapp.board

data class OrbMatch(
    val color: OrbColor,
    val positions: Set<BoardPosition>,
) {
    val size: Int get() = positions.size
}

data class BoardAnalysis(
    val matches: List<OrbMatch>,
) {
    val comboCount: Int get() = matches.size
}

object BoardAnalyzer {
    fun analyze(board: Board): BoardAnalysis {
        val matched = Array(board.rows) { Array(board.columns) { false } }

        for (row in 0 until board.rows) {
            var start = 0
            while (start < board.columns) {
                val color = board.orbs[row * board.columns + start].color
                var end = start + 1
                while (color != OrbColor.UNKNOWN && end < board.columns &&
                    board.orbs[row * board.columns + end].color == color
                ) {
                    end++
                }
                if (color != OrbColor.UNKNOWN && end - start >= MIN_MATCH) {
                    for (column in start until end) matched[row][column] = true
                }
                start = end
            }
        }

        for (column in 0 until board.columns) {
            var start = 0
            while (start < board.rows) {
                val color = board.orbs[start * board.columns + column].color
                var end = start + 1
                while (color != OrbColor.UNKNOWN && end < board.rows &&
                    board.orbs[end * board.columns + column].color == color
                ) {
                    end++
                }
                if (color != OrbColor.UNKNOWN && end - start >= MIN_MATCH) {
                    for (row in start until end) matched[row][column] = true
                }
                start = end
            }
        }

        val visited = Array(board.rows) { Array(board.columns) { false } }
        val groups = mutableListOf<OrbMatch>()
        for (row in 0 until board.rows) {
            for (column in 0 until board.columns) {
                if (!matched[row][column] || visited[row][column]) continue
                val color = board.orbs[row * board.columns + column].color
                val pending = ArrayDeque<BoardPosition>()
                val positions = linkedSetOf<BoardPosition>()
                pending.add(BoardPosition(row, column))
                visited[row][column] = true

                while (pending.isNotEmpty()) {
                    val current = pending.removeFirst()
                    positions.add(current)
                    for ((nextRow, nextColumn) in neighbors(current, board.rows, board.columns)) {
                        if (!visited[nextRow][nextColumn] && matched[nextRow][nextColumn] &&
                            board.orbs[nextRow * board.columns + nextColumn].color == color
                        ) {
                            visited[nextRow][nextColumn] = true
                            pending.add(BoardPosition(nextRow, nextColumn))
                        }
                    }
                }
                groups.add(OrbMatch(color, positions))
            }
        }
        return BoardAnalysis(groups)
    }

    private fun neighbors(position: BoardPosition, rows: Int, columns: Int): List<Pair<Int, Int>> =
        buildList(4) {
            if (position.row > 0) add(position.row - 1 to position.column)
            if (position.row + 1 < rows) add(position.row + 1 to position.column)
            if (position.column > 0) add(position.row to position.column - 1)
            if (position.column + 1 < columns) add(position.row to position.column + 1)
        }

    private const val MIN_MATCH = 3
}
