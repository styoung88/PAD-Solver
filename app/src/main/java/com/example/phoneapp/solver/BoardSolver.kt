package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class GoalEvaluation(
    val satisfied: Boolean,
    val primaryScore: Int,
)

/**
 * A goal seam for later conditions such as match-five and shape requirements.
 * The first version uses [MaximizeCombos].
 */
fun interface SolveGoal {
    fun evaluate(analysis: BoardAnalysis): GoalEvaluation

    val preferredColor: OrbColor? get() = null
}

object MaximizeCombos : SolveGoal {
    override fun evaluate(analysis: BoardAnalysis) =
        GoalEvaluation(satisfied = true, primaryScore = analysis.comboCount)
}

/** Board-only estimate for a mono-color team: orb-count factor × total combo factor. */
data class MaximizeColorDamage(val color: OrbColor) : SolveGoal {
    init {
        require(color in ATTACK_COLORS) { "A damage focus must use an attacking orb color." }
    }

    override val preferredColor: OrbColor get() = color

    override fun evaluate(analysis: BoardAnalysis): GoalEvaluation =
        GoalEvaluation(satisfied = true, primaryScore = ColorDamageEstimate.scoreRelativeDamage(analysis, color))

    private companion object {
        val ATTACK_COLORS = setOf(OrbColor.FIRE, OrbColor.WATER, OrbColor.WOOD, OrbColor.LIGHT, OrbColor.DARK)
    }
}

/**
 * A relative board score, not a prediction of actual damage. It models only
 * the size factor of each focused-color match and the global combo factor.
 * Both factors advance in quarter steps. Enhanced orbs add 6% each to their
 * individual match, represented as a percent multiplier on the same score.
 */
object ColorDamageEstimate {
    fun scoreRelativeDamage(analysis: BoardAnalysis, color: OrbColor): Int {
        val comboFactorInQuarters = analysis.comboCount + 3
        return analysis.allMatches.asSequence()
            .filter { it.color == color }
            .sumOf { match ->
                comboFactorInQuarters * (match.size + 1) * (100 + 6 * match.enhancedOrbCount)
            }
    }
}

data class SolveOptions(
    /** Search bound in grid transitions, independent of the game's move timer. */
    val maxOrbMoves: Int = 40,
    val beamWidth: Int = 192,
    val goal: SolveGoal = MaximizeCombos,
) {
    init {
        require(maxOrbMoves >= 0)
        require(beamWidth > 0)
    }
}

data class BoardSolution(
    /** Includes the selected starting cell followed by each cell entered. */
    val path: List<BoardPosition>,
    val predictedMatches: BoardAnalysis,
    val goalSatisfied: Boolean,
    val preferredColor: OrbColor? = null,
) {
    val comboCount: Int get() = predictedMatches.comboCount
    val orbMoves: Int get() = (path.size - 1).coerceAtLeast(0)
}

/**
 * Bounded beam search over the drag path. The objective and path bound are
 * independent so later solve conditions and a move-complexity slider can be
 * added without changing board recognition or the presentation layer.
 */
class BoardSolver {
    suspend fun solve(board: Board, options: SolveOptions = SolveOptions()): BoardSolution =
        withContext(Dispatchers.Default) {
            search(board, options)
        }

    private suspend fun search(board: Board, options: SolveOptions): BoardSolution {
        val startCells = board.orbs.map(::encodeOrb).toIntArray()
        var beam = board.orbs.indices.map { start ->
            rank(
                SearchState(
                    cells = startCells.copyOf(),
                    current = start,
                    previous = -1,
                    path = intArrayOf(start),
                ),
                board.columns,
                board.rows,
                options.goal,
            )
        }
        var best = beam.first()

        for (depth in 0..options.maxOrbMoves) {
            coroutineContext.ensureActive()
            val candidates = LinkedHashMap<String, RankedState>()
            for (ranked in beam) {
                val state = ranked.state
                if (isBetter(ranked, best)) best = ranked
                if (depth == options.maxOrbMoves) continue

                for (next in adjacent(state.current, board.columns, board.rows)) {
                    // Immediately undoing the last swap cannot improve a solution.
                    if (next == state.previous) continue
                    val nextCells = state.cells.copyOf()
                    val moved = nextCells[state.current]
                    nextCells[state.current] = nextCells[next]
                    nextCells[next] = moved
                    val nextPath = state.path.copyOf(state.path.size + 1)
                    nextPath[nextPath.lastIndex] = next
                    val candidate = rank(
                        SearchState(nextCells, next, state.current, nextPath),
                        board.columns,
                        board.rows,
                        options.goal,
                    )
                    val key = candidate.state.signature()
                    val existing = candidates[key]
                    if (existing == null || candidate.state.path.size < existing.state.path.size) {
                        candidates[key] = candidate
                    }
                }
            }
            if (depth == options.maxOrbMoves || candidates.isEmpty()) break

            beam = candidates.values
                .sortedWith(::compareRanked)
                .take(options.beamWidth)
        }

        return BoardSolution(
            path = best.state.path.map(board::positionOf),
            predictedMatches = best.analysis,
            goalSatisfied = best.goal.satisfied,
            preferredColor = options.goal.preferredColor,
        )
    }

    private fun rank(state: SearchState, columns: Int, rows: Int, goal: SolveGoal): RankedState {
        val analysis = analyze(state.cells, columns, rows)
        return RankedState(state, analysis, goal.evaluate(analysis), nearMatches(state.cells, columns, rows))
    }

    private fun compareRanked(left: RankedState, right: RankedState): Int {
        if (left.goal.satisfied != right.goal.satisfied) return right.goal.satisfied.compareTo(left.goal.satisfied)
        if (left.goal.primaryScore != right.goal.primaryScore) {
            return right.goal.primaryScore.compareTo(left.goal.primaryScore)
        }
        if (left.analysis.comboCount != right.analysis.comboCount) {
            return right.analysis.comboCount.compareTo(left.analysis.comboCount)
        }
        if (left.nearMatchCount != right.nearMatchCount) {
            return right.nearMatchCount.compareTo(left.nearMatchCount)
        }
        return left.state.path.size.compareTo(right.state.path.size)
    }

    private fun isBetter(candidate: RankedState, best: RankedState): Boolean {
        if (candidate.goal.satisfied != best.goal.satisfied) return candidate.goal.satisfied
        if (candidate.goal.primaryScore != best.goal.primaryScore) {
            return candidate.goal.primaryScore > best.goal.primaryScore
        }
        if (candidate.analysis.comboCount != best.analysis.comboCount) {
            return candidate.analysis.comboCount > best.analysis.comboCount
        }
        return candidate.state.path.size < best.state.path.size
    }

    private fun adjacent(index: Int, columns: Int, rows: Int): IntArray {
        val row = index / columns
        val column = index % columns
        return buildList(4) {
            if (row > 0) add(index - columns)
            if (row + 1 < rows) add(index + columns)
            if (column > 0) add(index - 1)
            if (column + 1 < columns) add(index + 1)
        }.toIntArray()
    }

    private fun nearMatches(cells: IntArray, columns: Int, rows: Int): Int {
        var pairs = 0
        for (row in 0 until rows) {
            var column = 0
            while (column < columns - 1) {
                val color = colorOf(cells[row * columns + column])
                var end = column + 1
                while (end < columns && color != OrbColor.UNKNOWN &&
                    colorOf(cells[row * columns + end]) == color
                ) end++
                if (end - column == 2) pairs++
                column = end
            }
        }
        for (column in 0 until columns) {
            var row = 0
            while (row < rows - 1) {
                val color = colorOf(cells[row * columns + column])
                var end = row + 1
                while (end < rows && color != OrbColor.UNKNOWN &&
                    colorOf(cells[end * columns + column]) == color
                ) end++
                if (end - row == 2) pairs++
                row = end
            }
        }
        return pairs
    }

    private fun analyze(cells: IntArray, columns: Int, rows: Int): BoardAnalysis =
        BoardAnalyzer.resolveCascades(
            Board(
                columns = columns,
                rows = rows,
                orbs = cells.map { code -> Orb(colorOf(code), modifiersOf(code)) },
            ),
        )

    private fun encodeOrb(orb: Orb): Int {
        var code = orb.color.ordinal
        if (OrbModifier.LOCKED in orb.modifiers) code = code or LOCKED_BIT
        if (OrbModifier.ENHANCED in orb.modifiers) code = code or ENHANCED_BIT
        return code
    }

    private fun colorOf(code: Int): OrbColor = OrbColor.entries[code and COLOR_MASK]

    private fun modifiersOf(code: Int): Set<OrbModifier> = buildSet {
        if (code and LOCKED_BIT != 0) add(OrbModifier.LOCKED)
        if (code and ENHANCED_BIT != 0) add(OrbModifier.ENHANCED)
    }

    private data class SearchState(
        val cells: IntArray,
        val current: Int,
        val previous: Int,
        val path: IntArray,
    ) {
        fun signature(): String = buildString(cells.size + 1) {
            cells.forEach { append(('A'.code + it).toChar()) }
            append(('A'.code + current).toChar())
            append(('A'.code + previous + 1).toChar())
        }
    }

    private data class RankedState(
        val state: SearchState,
        val analysis: BoardAnalysis,
        val goal: GoalEvaluation,
        val nearMatchCount: Int,
    )

    private companion object {
        const val COLOR_MASK = 0b111
        const val LOCKED_BIT = 1 shl 3
        const val ENHANCED_BIT = 1 shl 4
    }
}
