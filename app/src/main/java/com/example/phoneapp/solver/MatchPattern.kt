package com.example.phoneapp.solver

import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbMatch
import java.util.concurrent.ConcurrentHashMap

/** PAD match formations that can be required by a solve goal. */
enum class MatchPattern(val displayName: String, private val minimumOrbs: Int) {
    ROW("Row", 6),
    TPA("TPA", 4),
    L_SHAPE("L shape", 5),
    CROSS("Cross", 5),
    VDP("VDP (3×3 square)", 9);

    fun countMatches(analysis: BoardAnalysis, color: OrbColor? = null): Int =
        analysis.allMatches.sumOf { match ->
            if (!match.color.isAttacking || color != null && match.color != color) 0 else countIn(match, analysis.columns)
        }

    /** Necessary inventory and geometry checks, without assuming a route is reachable. */
    fun canForm(board: Board, color: OrbColor? = null): Boolean {
        if (color != null && !color.isAttacking) return false
        val shapes = cachedTemplates(board.columns, board.rows)
        if (shapes.isEmpty()) return false
        val counts = board.orbs.groupingBy { it.color }.eachCount()
        return (color?.let(::listOf) ?: OrbColor.attackingColors).any { (counts[it] ?: 0) >= requiredOrbs(board) }
    }

    private fun requiredOrbs(board: Board): Int = if (this == ROW) board.columns else minimumOrbs

    private fun cachedTemplates(columns: Int, rows: Int): List<Set<BoardPosition>> =
        templateCache.computeIfAbsent(Triple(this, columns, rows)) { templates(columns, rows) }

    /**
     * Counts the best partial arrangement of this pattern currently on the board.
     * This is a beam-search heuristic only; it does not make a route satisfy the goal.
     */
    fun potentialScore(board: Board, color: OrbColor? = null): Int {
        if (color != null && !color.isAttacking) return 0
        // TPA is the common expensive case when no color is selected: the
        // beam search evaluates every route candidate, and the generic shape
        // path below allocates collections for each of the 36 line templates
        // and each attacking color. Score those straight four-cell windows
        // directly from cached cell indices instead.
        if (this == TPA) return tpaPotentialScore(board, color)

        // Each pattern has different geometry even when board dimensions match.
        val shapes = cachedTemplates(board.columns, board.rows)
        val colors = if (color == null) OrbColor.attackingColors else listOf(color)
        val orbPositionsByColor = colors.associateWith { candidateColor ->
            board.orbs.mapIndexedNotNull { index, orb ->
                if (orb.color == candidateColor) board.positionOf(index) else null
            }
        }
        var bestCoverage = -1
        var bestScore = 0
        for (shape in shapes) {
            for (candidateColor in colors) {
                if (orbPositionsByColor.getValue(candidateColor).size < requiredOrbs(board)) continue
                val matchingPositions = shape.filter { position -> board[position].color == candidateColor }
                val coverage = matchingPositions.size
                if (coverage < bestCoverage) continue
                val distance = distanceToFillShape(
                    shape = shape,
                    matchingPositions = matchingPositions.toSet(),
                    colorPositions = orbPositionsByColor.getValue(candidateColor),
                )
                val alignmentBonus = alignmentBonus(shape, matchingPositions.toSet())
                val score = coverage * COVERAGE_WEIGHT + alignmentBonus - distance
                if (coverage > bestCoverage || coverage == bestCoverage && score > bestScore) {
                    bestCoverage = coverage
                    bestScore = score
                }
            }
        }
        return bestScore
    }

    private fun tpaPotentialScore(board: Board, color: OrbColor?): Int {
        val columns = board.columns
        val rows = board.rows
        val windows = tpaTemplateCache.computeIfAbsent(columns to rows) {
            buildList {
                for (row in 0 until rows) for (left in 0..(columns - 4)) {
                    val start = row * columns + left
                    add(intArrayOf(start, start + 1, start + 2, start + 3))
                }
                for (column in 0 until columns) for (top in 0..(rows - 4)) {
                    val start = top * columns + column
                    add(intArrayOf(start, start + columns, start + 2 * columns, start + 3 * columns))
                }
            }
        }

        val selectedColors = if (color == null) OrbColor.attackingColors else listOf(color)
        val colorCells = Array(OrbColor.entries.size) { IntArray(board.orbs.size) }
        val colorCounts = IntArray(OrbColor.entries.size)
        val colorAtCell = IntArray(board.orbs.size)
        board.orbs.forEachIndexed { index, orb ->
            val colorOrdinal = orb.color.ordinal
            colorAtCell[index] = colorOrdinal
            colorCells[colorOrdinal][colorCounts[colorOrdinal]++] = index
        }

        val missingCells = IntArray(4)
        val surplusCells = IntArray(board.orbs.size)
        var bestCoverage = -1
        var bestScore = 0
        for (window in windows) {
            for (candidateColor in selectedColors) {
                val colorOrdinal = candidateColor.ordinal
                if (colorCounts[colorOrdinal] < minimumOrbs) continue
                var coverage = 0
                var run = 0
                var longestRun = 0
                var missingCount = 0
                for (cell in window) {
                    if (colorAtCell[cell] == colorOrdinal) {
                        coverage++
                        run++
                        longestRun = maxOf(longestRun, run)
                    } else {
                        run = 0
                        missingCells[missingCount++] = cell
                    }
                }
                if (coverage < bestCoverage) continue

                // Match missing slots to the nearest surplus orbs, preserving
                // the prior greedy Manhattan-distance heuristic without
                // allocating lists, sets, or BoardPosition values per window.
                var surplusCount = 0
                val cellsOfColor = colorCells[colorOrdinal]
                for (colorIndex in 0 until colorCounts[colorOrdinal]) {
                    val cell = cellsOfColor[colorIndex]
                    if (window.none { it == cell }) surplusCells[surplusCount++] = cell
                }
                var distance = 0
                var remainingMissing = missingCount
                var remainingSurplus = surplusCount
                while (remainingMissing > 0 && remainingSurplus > 0) {
                    var closestMissing = 0
                    var closestSurplus = 0
                    var closestDistance = Int.MAX_VALUE
                    for (missingIndex in 0 until remainingMissing) {
                        val missing = missingCells[missingIndex]
                        for (surplusIndex in 0 until remainingSurplus) {
                            val surplus = surplusCells[surplusIndex]
                            val candidateDistance = kotlin.math.abs(missing / columns - surplus / columns) +
                                kotlin.math.abs(missing % columns - surplus % columns)
                            if (candidateDistance < closestDistance) {
                                closestDistance = candidateDistance
                                closestMissing = missingIndex
                                closestSurplus = surplusIndex
                            }
                        }
                    }
                    distance += closestDistance
                    for (index in closestMissing until remainingMissing - 1) {
                        missingCells[index] = missingCells[index + 1]
                    }
                    for (index in closestSurplus until remainingSurplus - 1) {
                        surplusCells[index] = surplusCells[index + 1]
                    }
                    remainingMissing--
                    remainingSurplus--
                }

                val score = coverage * COVERAGE_WEIGHT + longestRun * STRAIGHT_RUN_BONUS - distance
                if (coverage > bestCoverage || coverage == bestCoverage && score > bestScore) {
                    bestCoverage = coverage
                    bestScore = score
                }
            }
        }
        return bestScore
    }

    /** Shape-specific partial progress, used only to order incomplete beam states. */
    private fun alignmentBonus(
        shape: Set<BoardPosition>,
        matchingPositions: Set<BoardPosition>,
    ): Int = when (this) {
        ROW, TPA -> longestStraightRun(shape, matchingPositions) * STRAIGHT_RUN_BONUS
        L_SHAPE -> bestLArmBalance(shape, matchingPositions)
        CROSS -> crossAlignmentBonus(shape, matchingPositions)
        VDP -> squareLineBonus(shape, matchingPositions)
    }

    private fun longestStraightRun(
        shape: Set<BoardPosition>,
        matchingPositions: Set<BoardPosition>,
    ): Int {
        val rows = shape.map(BoardPosition::row).distinct()
        val columns = shape.map(BoardPosition::column).distinct()
        val line = if (rows.size == 1) {
            shape.map(BoardPosition::column).sorted().map { BoardPosition(rows.single(), it) }
        } else {
            shape.map(BoardPosition::row).sorted().map { BoardPosition(it, columns.single()) }
        }
        var best = 0
        var run = 0
        var previous: BoardPosition? = null
        for (position in line) {
            run = if (position in matchingPositions && previous != null &&
                (position.row == previous!!.row + 1 || position.column == previous!!.column + 1)
            ) run + 1 else if (position in matchingPositions) 1 else 0
            best = maxOf(best, run)
            previous = position
        }
        return best
    }

    private fun bestLArmBalance(
        shape: Set<BoardPosition>,
        matchingPositions: Set<BoardPosition>,
    ): Int {
        var best = 0
        val arms = lArmTemplateCache.computeIfAbsent(shape, ::lArms)
        for ((verticalArm, horizontalArm) in arms) {
            val verticalProgress = verticalArm.count(matchingPositions::contains)
            val horizontalProgress = horizontalArm.count(matchingPositions::contains)
            best = maxOf(best, minOf(verticalProgress, horizontalProgress) * ARM_BALANCE_BONUS +
                maxOf(verticalProgress, horizontalProgress) * ARM_PROGRESS_BONUS)
        }
        return best
    }

    private fun lArms(shape: Set<BoardPosition>): List<Pair<List<BoardPosition>, List<BoardPosition>>> =
        buildList {
            for (corner in shape) for (vertical in listOf(-1, 1)) for (horizontal in listOf(-1, 1)) {
                val verticalArm = listOf(
                    corner,
                    BoardPosition(corner.row + vertical, corner.column),
                    BoardPosition(corner.row + 2 * vertical, corner.column),
                )
                val horizontalArm = listOf(
                    corner,
                    BoardPosition(corner.row, corner.column + horizontal),
                    BoardPosition(corner.row, corner.column + 2 * horizontal),
                )
                if ((verticalArm + horizontalArm).toSet() == shape) add(verticalArm to horizontalArm)
            }
        }

    private fun crossAlignmentBonus(
        shape: Set<BoardPosition>,
        matchingPositions: Set<BoardPosition>,
    ): Int {
        val center = shape.firstOrNull { candidate ->
            listOf(
                BoardPosition(candidate.row - 1, candidate.column),
                BoardPosition(candidate.row + 1, candidate.column),
                BoardPosition(candidate.row, candidate.column - 1),
                BoardPosition(candidate.row, candidate.column + 1),
            ).all(shape::contains)
        } ?: return 0
        val arms = listOf(
            BoardPosition(center.row - 1, center.column),
            BoardPosition(center.row + 1, center.column),
            BoardPosition(center.row, center.column - 1),
            BoardPosition(center.row, center.column + 1),
        )
        return (if (center in matchingPositions) CROSS_CENTER_BONUS else 0) +
            arms.count(matchingPositions::contains) * CROSS_ARM_BONUS
    }

    private fun squareLineBonus(
        shape: Set<BoardPosition>,
        matchingPositions: Set<BoardPosition>,
    ): Int {
        val completedRows = shape.map(BoardPosition::row).distinct().count { row ->
            shape.filter { it.row == row }.all(matchingPositions::contains)
        }
        val completedColumns = shape.map(BoardPosition::column).distinct().count { column ->
            shape.filter { it.column == column }.all(matchingPositions::contains)
        }
        return (completedRows + completedColumns) * SQUARE_COMPLETED_LINE_BONUS
    }

    private fun distanceToFillShape(
        shape: Set<BoardPosition>,
        matchingPositions: Set<BoardPosition>,
        colorPositions: List<BoardPosition>,
    ): Int {
        val missing = shape.filterNot(matchingPositions::contains).toMutableList()
        val surplus = colorPositions.filterNot(shape::contains).toMutableList()
        var distance = 0
        while (missing.isNotEmpty() && surplus.isNotEmpty()) {
            var closestMissing = 0
            var closestSurplus = 0
            var closestDistance = Int.MAX_VALUE
            for (missingIndex in missing.indices) {
                for (surplusIndex in surplus.indices) {
                    val candidateDistance = missing[missingIndex].manhattanDistance(surplus[surplusIndex])
                    if (candidateDistance < closestDistance) {
                        closestDistance = candidateDistance
                        closestMissing = missingIndex
                        closestSurplus = surplusIndex
                    }
                }
            }
            distance += closestDistance
            missing.removeAt(closestMissing)
            surplus.removeAt(closestSurplus)
        }
        return distance
    }

    private fun countIn(match: OrbMatch, columns: Int): Int = when (this) {
        ROW -> countRows(match, columns)
        TPA -> if (match.positions.isStraightFour()) 1 else 0
        L_SHAPE -> if (match.positions.isLShape()) 1 else 0
        CROSS -> if (match.positions.isCross()) 1 else 0
        VDP -> if (match.positions.isThreeByThreeSquare()) 1 else 0
    }

    private fun Set<BoardPosition>.isStraightFour(): Boolean {
        if (size != 4) return false
        val start = first()
        return all { it.row == start.row } && maxOf { it.column } - minOf { it.column } == 3 ||
            all { it.column == start.column } && maxOf { it.row } - minOf { it.row } == 3
    }

    private fun countRows(match: OrbMatch, width: Int): Int = match.positions
        .groupBy(BoardPosition::row)
        .values
        .count { cells ->
            cells.size == width && cells.all { it.column in 0 until width }
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

    private fun BoardPosition.manhattanDistance(other: BoardPosition): Int =
        kotlin.math.abs(row - other.row) + kotlin.math.abs(column - other.column)

    private fun templates(columns: Int, rows: Int): List<Set<BoardPosition>> = buildList {
        when (this@MatchPattern) {
            ROW -> for (row in 0 until rows) {
                if (columns >= 3) add((0 until columns).map { BoardPosition(row, it) }.toSet())
            }
            TPA -> {
                for (row in 0 until rows) for (left in 0..(columns - 4)) {
                    add((left until left + 4).map { BoardPosition(row, it) }.toSet())
                }
                for (column in 0 until columns) for (top in 0..(rows - 4)) {
                    add((top until top + 4).map { BoardPosition(it, column) }.toSet())
                }
            }
            L_SHAPE -> for (row in 0 until rows) for (column in 0 until columns) {
                for (vertical in listOf(-1, 1)) for (horizontal in listOf(-1, 1)) {
                    val shape = setOf(
                        BoardPosition(row, column),
                        BoardPosition(row + vertical, column),
                        BoardPosition(row + 2 * vertical, column),
                        BoardPosition(row, column + horizontal),
                        BoardPosition(row, column + 2 * horizontal),
                    )
                    if (shape.all { it.row in 0 until rows && it.column in 0 until columns }) add(shape)
                }
            }
            CROSS -> for (row in 1 until rows - 1) for (column in 1 until columns - 1) {
                add(setOf(
                    BoardPosition(row, column),
                    BoardPosition(row - 1, column),
                    BoardPosition(row + 1, column),
                    BoardPosition(row, column - 1),
                    BoardPosition(row, column + 1),
                ))
            }
            VDP -> for (top in 0..(rows - 3)) for (left in 0..(columns - 3)) {
                add((top..top + 2).flatMap { row ->
                    (left..left + 2).map { column -> BoardPosition(row, column) }
                }.toSet())
            }
        }
    }

    private companion object {
        val templateCache = ConcurrentHashMap<Triple<MatchPattern, Int, Int>, List<Set<BoardPosition>>>()
        val tpaTemplateCache = ConcurrentHashMap<Pair<Int, Int>, List<IntArray>>()
        val lArmTemplateCache = ConcurrentHashMap<Set<BoardPosition>, List<Pair<List<BoardPosition>, List<BoardPosition>>>>()
        const val COVERAGE_WEIGHT = 100
        const val STRAIGHT_RUN_BONUS = 8
        const val ARM_BALANCE_BONUS = 8
        const val ARM_PROGRESS_BONUS = 4
        const val CROSS_CENTER_BONUS = 28
        const val CROSS_ARM_BONUS = 8
        const val SQUARE_COMPLETED_LINE_BONUS = 10
    }
}
