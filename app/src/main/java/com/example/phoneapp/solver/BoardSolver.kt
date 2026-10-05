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
    /** Used to preserve the user's declared priority when multiple hard criteria are unmet. */
    val primaryCriterionSatisfied: Boolean = satisfied,
    val primaryScore: Int,
    val secondaryScore: Int = 0,
)

/**
 * A goal seam for later conditions such as match-five and shape requirements.
 * The first version uses [MaximizeCombos].
 */
fun interface SolveGoal {
    fun evaluate(analysis: BoardAnalysis): GoalEvaluation

    /** Lightweight heuristic for retaining incomplete required patterns during beam search. */
    fun patternPotential(board: Board): Int = 0

    val preferredColor: OrbColor? get() = null
    val matchPatternFocus: MatchPattern? get() = null
    val targetComboCount: Int? get() = null
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
        GoalEvaluation(
            satisfied = true,
            primaryScore = ColorDamageEstimate.scoreRelativeDamage(analysis, color),
            secondaryScore = analysis.comboCount,
        )

    private companion object {
        val ATTACK_COLORS = setOf(OrbColor.FIRE, OrbColor.WATER, OrbColor.WOOD, OrbColor.LIGHT, OrbColor.DARK)
    }
}

/** Prefer routes that activate the selected match formation. */
data class MaximizeMatchPattern(
    val pattern: MatchPattern,
    val color: OrbColor? = null,
) : SolveGoal {
    override val preferredColor: OrbColor? get() = color
    override val matchPatternFocus: MatchPattern get() = pattern

    override fun evaluate(analysis: BoardAnalysis): GoalEvaluation {
        val patternCount = pattern.countMatches(analysis, color)
        val patternSatisfied = patternCount > 0
        return GoalEvaluation(
            satisfied = patternSatisfied,
            primaryCriterionSatisfied = patternSatisfied,
            primaryScore = patternCount,
            secondaryScore = color?.let { ColorDamageEstimate.scoreRelativeDamage(analysis, it) }
                ?: analysis.comboCount,
        )
    }

    override fun patternPotential(board: Board): Int = pattern.potentialScore(board, color)
}

/** First reach a combo threshold, then rank those routes by the chosen color objective. */
data class TargetComboCount(
    val target: Int,
    val color: OrbColor? = null,
    val pattern: MatchPattern? = null,
) : SolveGoal {
    init {
        require(target > 0)
        require(color == null || color in ATTACK_COLORS) {
            "A damage focus must use an attacking orb color."
        }
    }

    override val preferredColor: OrbColor? get() = color
    override val matchPatternFocus: MatchPattern? get() = pattern
    override val targetComboCount: Int get() = target

    override fun evaluate(analysis: BoardAnalysis): GoalEvaluation {
        val reachedTarget = analysis.comboCount >= target
        val colorScore = color?.let { ColorDamageEstimate.scoreRelativeDamage(analysis, it) }
        val patternCount = pattern?.countMatches(analysis, color) ?: 0
        val patternSatisfied = pattern == null || patternCount > 0
        val objectiveScore = when {
            !reachedTarget -> analysis.comboCount
            pattern != null -> patternCount
            colorScore != null -> colorScore
            else -> analysis.comboCount
        }
        val secondaryScore = when {
            !reachedTarget -> analysis.comboCount
            pattern != null && colorScore != null -> colorScore
            else -> analysis.comboCount
        }
        return GoalEvaluation(
            satisfied = reachedTarget && patternSatisfied,
            primaryCriterionSatisfied = reachedTarget,
            primaryScore = objectiveScore,
            secondaryScore = secondaryScore,
        )
    }

    override fun patternPotential(board: Board): Int = pattern?.potentialScore(board, color) ?: 0

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
    val matchPatternFocus: MatchPattern? = null,
    val focusedPatternMatchCount: Int? = null,
    val targetComboCount: Int? = null,
    val comboMinimumSatisfied: Boolean? = null,
    val matchPatternSatisfied: Boolean? = null,
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

            beam = selectBeam(candidates.values, options.beamWidth, options.goal)
        }

        return BoardSolution(
            path = best.state.path.map(board::positionOf),
            predictedMatches = best.analysis,
            goalSatisfied = best.goal.satisfied,
            preferredColor = options.goal.preferredColor,
            matchPatternFocus = options.goal.matchPatternFocus,
            focusedPatternMatchCount = options.goal.matchPatternFocus?.countMatches(
                best.analysis,
                options.goal.preferredColor,
            ),
            targetComboCount = options.goal.targetComboCount,
            comboMinimumSatisfied = options.goal.targetComboCount?.let { best.analysis.comboCount >= it },
            matchPatternSatisfied = options.goal.matchPatternFocus?.let {
                it.countMatches(best.analysis, options.goal.preferredColor) > 0
            },
        )
    }

    private fun rank(state: SearchState, columns: Int, rows: Int, goal: SolveGoal): RankedState {
        val board = Board(
            columns = columns,
            rows = rows,
            orbs = state.cells.map { code -> Orb(colorOf(code), modifiersOf(code)) },
        )
        val analysis = BoardAnalyzer.resolveCascades(board)
        return RankedState(
            state = state,
            analysis = analysis,
            goal = goal.evaluate(analysis),
            patternPotential = goal.patternPotential(board),
            nearMatchCount = nearMatches(state.cells, columns, rows),
        )
    }

    private fun compareRanked(left: RankedState, right: RankedState): Int {
        if (left.goal.satisfied != right.goal.satisfied) return right.goal.satisfied.compareTo(left.goal.satisfied)
        if (left.goal.primaryCriterionSatisfied != right.goal.primaryCriterionSatisfied) {
            return right.goal.primaryCriterionSatisfied.compareTo(left.goal.primaryCriterionSatisfied)
        }
        if (left.goal.primaryScore != right.goal.primaryScore) {
            return right.goal.primaryScore.compareTo(left.goal.primaryScore)
        }
        if (left.patternPotential != right.patternPotential) {
            return right.patternPotential.compareTo(left.patternPotential)
        }
        if (left.goal.secondaryScore != right.goal.secondaryScore) {
            return right.goal.secondaryScore.compareTo(left.goal.secondaryScore)
        }
        if (left.analysis.comboCount != right.analysis.comboCount) {
            return right.analysis.comboCount.compareTo(left.analysis.comboCount)
        }
        if (left.nearMatchCount != right.nearMatchCount) {
            return right.nearMatchCount.compareTo(left.nearMatchCount)
        }
        return left.state.path.size.compareTo(right.state.path.size)
    }

    /**
     * A combo threshold can be reached before a required pattern is assembled.
     * Keep a small frontier below the threshold so routes that temporarily trade
     * combo count for pattern progress are not all pruned at once.
     */
    private fun selectBeam(
        candidates: Collection<RankedState>,
        beamWidth: Int,
        goal: SolveGoal,
    ): List<RankedState> {
        val ranked = candidates.sortedWith(::compareRanked)
        if (goal.targetComboCount == null || goal.matchPatternFocus == null || ranked.size <= beamWidth) {
            return ranked.take(beamWidth)
        }

        val belowTarget = ranked.filterNot { it.goal.primaryCriterionSatisfied }
        if (belowTarget.isEmpty()) return ranked.take(beamWidth)

        val reserve = minOf(beamWidth / BELOW_TARGET_BEAM_DIVISOR, belowTarget.size)
        val selected = ArrayList<RankedState>(beamWidth)
        selected += ranked.asSequence()
            .filter { it.goal.primaryCriterionSatisfied }
            .take(beamWidth - reserve)
        val selectedStates = selected.toHashSet()
        val patternFrontier = belowTarget.sortedWith { left, right ->
            val potentialOrder = right.patternPotential.compareTo(left.patternPotential)
            if (potentialOrder != 0) potentialOrder else compareRanked(left, right)
        }
        for (candidate in patternFrontier) {
            if (selected.size >= beamWidth) break
            if (selectedStates.add(candidate)) selected += candidate
        }
        if (selected.size < beamWidth) {
            for (candidate in ranked) {
                if (selected.size >= beamWidth) break
                if (selectedStates.add(candidate)) selected += candidate
            }
        }
        return selected
    }

    private fun isBetter(candidate: RankedState, best: RankedState): Boolean {
        if (candidate.goal.satisfied != best.goal.satisfied) return candidate.goal.satisfied
        if (candidate.goal.primaryCriterionSatisfied != best.goal.primaryCriterionSatisfied) {
            return candidate.goal.primaryCriterionSatisfied
        }
        if (candidate.goal.primaryScore != best.goal.primaryScore) {
            return candidate.goal.primaryScore > best.goal.primaryScore
        }
        if (candidate.patternPotential != best.patternPotential) {
            return candidate.patternPotential > best.patternPotential
        }
        if (candidate.goal.secondaryScore != best.goal.secondaryScore) {
            return candidate.goal.secondaryScore > best.goal.secondaryScore
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
        val patternPotential: Int,
        val nearMatchCount: Int,
    )

    private companion object {
        const val COLOR_MASK = 0b111
        const val LOCKED_BIT = 1 shl 3
        const val ENHANCED_BIT = 1 shl 4
        const val BELOW_TARGET_BEAM_DIVISOR = 4
    }
}
