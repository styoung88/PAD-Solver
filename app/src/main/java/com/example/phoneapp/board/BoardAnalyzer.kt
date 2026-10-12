package com.example.phoneapp.board

data class OrbMatch(
    val color: OrbColor,
    val positions: Set<BoardPosition>,
    val enhancedOrbCount: Int = 0,
    val weakenedOrbCount: Int = 0,
    val comboBonusOrbCount: Int = 0,
) {
    val size: Int get() = positions.size
}

data class BoardAnalysis(
    val matches: List<OrbMatch>,
    /** One list of matches for each simultaneous clear, starting with the first clear. */
    val cascadeWaves: List<List<OrbMatch>> = listOf(matches),
    val columns: Int = Board.DEFAULT_COLUMNS,
    val rows: Int = Board.DEFAULT_ROWS,
) {
    /** Actual matches, including matches created by deterministic gravity. */
    val physicalComboCount: Int = cascadeWaves.sumOf { it.size }
    /** Each cleared bonus orb adds one combo, once, in its clear wave. */
    val bonusComboCount: Int = cascadeWaves.sumOf { wave -> wave.sumOf { it.comboBonusOrbCount } }
    val comboCount: Int = physicalComboCount + bonusComboCount
    val cascadeCount: Int get() = (cascadeWaves.size - 1).coerceAtLeast(0)
    val allMatches: List<OrbMatch> = cascadeWaves.flatten()
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
                groups.add(
                    OrbMatch(
                        color = color,
                        positions = positions,
                        enhancedOrbCount = positions.count { position ->
                            OrbModifier.ENHANCED in board[position].modifiers
                        },
                        weakenedOrbCount = positions.count { position ->
                            OrbModifier.WEAKENED in board[position].modifiers
                        },
                        comboBonusOrbCount = positions.count { position ->
                            OrbModifier.COMBO_BONUS in board[position].modifiers
                        },
                    ),
                )
            }
        }
        return BoardAnalysis(groups, columns = board.columns, rows = board.rows)
    }

    /**
     * Resolves currently available matches, drops surviving orbs, and repeats
     * until no further matches form. Empty cells remain UNKNOWN; no skyfall is modeled.
     */
    fun resolveCascades(board: Board): BoardAnalysis {
        var orbs = board.orbs
        val waves = mutableListOf<List<OrbMatch>>()

        while (true) {
            val wave = analyze(board.copy(orbs = orbs))
            if (wave.matches.isEmpty()) break

            waves += wave.matches
            orbs = dropMatches(board.columns, board.rows, orbs, wave.matches)
        }

        return BoardAnalysis(
            matches = waves.firstOrNull().orEmpty(),
            cascadeWaves = waves,
            columns = board.columns,
            rows = board.rows,
        )
    }

    private fun dropMatches(
        columns: Int,
        rows: Int,
        orbs: List<Orb>,
        matches: List<OrbMatch>,
    ): List<Orb> {
        val cleared = matches.asSequence().flatMap { it.positions.asSequence() }.toSet()
        val dropped = MutableList(columns * rows) { Orb(OrbColor.UNKNOWN) }

        for (column in 0 until columns) {
            var destinationRow = rows - 1
            for (row in rows - 1 downTo 0) {
                if (BoardPosition(row, column) in cleared) continue
                dropped[destinationRow * columns + column] = orbs[row * columns + column]
                destinationRow--
            }
        }
        return dropped
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
