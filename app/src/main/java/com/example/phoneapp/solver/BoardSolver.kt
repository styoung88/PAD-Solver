package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class SolveOptions(
    /** Search bound in grid transitions, independent of the game's move timer. */
    val maxOrbMoves: Int = 40,
    val beamWidth: Int = SearchComplexity.DEFAULT_BEAM_WIDTH,
    val goal: SolveGoal = MaximizeCombos,
) {
    init {
        require(maxOrbMoves >= 0)
        require(beamWidth > 0)
    }

    companion object {
        fun forBoard(board: Board, goal: SolveGoal = MaximizeCombos): SolveOptions = SolveOptions(
            maxOrbMoves = BoardSize.of(board.columns, board.rows)?.maxOrbMoves ?: 40,
            goal = goal,
        )
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
    /** False only when orb inventory or board dimensions prove a requirement impossible. */
    val requirementsPossible: Boolean = true,
    val teamSkillEvaluation: TeamSkillEvaluation? = null,
    val damageReductionRequired: Boolean = false,
) {
    val comboCount: Int get() = predictedMatches.comboCount
    val orbMoves: Int get() = (path.size - 1).coerceAtLeast(0)
    val routeSummary: String get() = buildList {
        add("${comboCount}c")
        teamSkillEvaluation?.let { team ->
            add("ATK ×${team.attackMultiplier}")
            if (team.skill.reductionPercent > 0) add("${team.damageReductionPercent}% DR")
        }
    }.joinToString(" · ")
    val routeDetails: String get() = buildList {
        teamSkillEvaluation?.let { team ->
            if (team.bonusAttackDamage > 0) add("Bonus attack: ${team.bonusAttackDamage} damage")
        }
    }.joinToString("\n")
}

/** Bounded drag-path search with separate candidate heuristics and final route ranking. */
class BoardSolver(private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    suspend fun solve(board: Board, options: SolveOptions = SolveOptions.forBoard(board)): BoardSolution =
        withContext(dispatcher) { search(board, options) }

    private suspend fun search(board: Board, options: SolveOptions): BoardSolution {
        coroutineContext.ensureActive()
        // Explore the same paths regardless of the user's combo cutoff. One pass
        // favors damage; another favors damage after reaching half the inventory's
        // combo capacity, to escape low-combo local optima. Final requirements stay exact.
        val searchGoal = (options.goal as? ConfiguredGoal)?.takeIf { goal ->
            goal.requirements.any { it is ComboMinimum }
        }?.let { goal ->
            ConfiguredGoal(goal.preferredColor, goal.requirements.filterNot { it is ComboMinimum }, goal.damageEvaluator)
        } ?: options.goal
        val comboCapacity = board.comboCapacity
        val explorationGoals = if (searchGoal is ConfiguredGoal && searchGoal.usesDamageObjective && comboCapacity > 1) {
            listOf(searchGoal, ConfiguredGoal(searchGoal.preferredColor,
                listOf(ComboMinimum((comboCapacity + 1) / 2)) + searchGoal.requirements, searchGoal.damageEvaluator))
        } else listOf(searchGoal)
        // A demanding user minimum may need its own pass after the shared exploration.
        val searchGoals = if (searchGoal !== options.goal) explorationGoals + options.goal else explorationGoals
        val evaluations = object : LinkedHashMap<String, BoardEvaluation>(EVALUATION_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BoardEvaluation>): Boolean =
                size > EVALUATION_CACHE_SIZE
        }
        fun rank(state: SearchState): RankedState {
            val evaluation = synchronized(evaluations) { evaluations[state.layoutKey] } ?: run {
                val candidateBoard = board.copy(orbs = state.cells.map { Orb(colorOf(it), modifiersOf(it)) })
                val analysis = BoardAnalyzer.resolveCascades(candidateBoard)
                val goal = options.goal.evaluate(analysis)
                val searchScores = searchGoals.map { if (it === options.goal) goal else it.evaluate(analysis) }
                val computed = BoardEvaluation(
                    analysis, goal, searchScores,
                    if (searchScores.all { it.satisfied }) 0 else searchGoal.patternPotential(candidateBoard),
                    nearMatches(state.cells, board.columns, board.rows),
                )
                // Cascade analysis runs outside the lock; concurrent misses may compute
                // the same pure evaluation, but only one immutable result is cached.
                synchronized(evaluations) { evaluations.getOrPut(state.layoutKey) { computed } }
            }
            return RankedState(state, evaluation)
        }

        val startCells = board.orbs.map(::encodeOrb).toIntArray()
        val requirementsPossible = options.goal.requirements.all { it.isPossible(board) }
        var best = rank(SearchState(startCells, 0, -1, intArrayOf(0)))
        val initialBeam = if (requirementsPossible) board.orbs.indices.map { start ->
            rank(SearchState(startCells, start, -1, intArrayOf(start)))
        } else emptyList()
        // An impossible request has no useful search frontier. Return the captured board for adjustment.
        val maxDepth = if (requirementsPossible) options.maxOrbMoves else 0

        suspend fun advance(beam: List<RankedState>, strategy: Int): Pair<List<RankedState>, RankedState?> {
            coroutineContext.ensureActive()
            val candidates = LinkedHashMap<String, SearchState>()
            for (ranked in beam) {
                coroutineContext.ensureActive()
                val state = ranked.state

                for (next in adjacent(state.current, board.columns, board.rows)) {
                    if (next == state.previous) continue
                    val nextCells = state.cells.copyOf()
                    val moved = nextCells[state.current]
                    nextCells[state.current] = nextCells[next]
                    nextCells[next] = moved
                    val nextPath = state.path.copyOf(state.path.size + 1)
                    nextPath[nextPath.lastIndex] = next
                    val turned = state.previous >= 0 &&
                        next - state.current != state.current - state.previous
                    val candidate = SearchState(
                        nextCells, next, state.current, nextPath, state.turns + if (turned) 1 else 0,
                    )
                    val key = candidate.signature()
                    val existing = candidates[key]
                    if (existing == null || compareRoute(candidate, existing) < 0) candidates[key] = candidate
                }
            }
            if (candidates.isEmpty()) return emptyList<RankedState>() to null
            // Discard equivalent drag states before performing cascade analysis.
            val evaluated = candidates.values.map { candidate ->
                coroutineContext.ensureActive()
                rank(candidate)
            }
            return selectBeam(evaluated, options.beamWidth, strategy) to evaluated.minWithOrNull(::compareResult)
        }

        val initialBest = best
        suspend fun explore(strategy: Int): RankedState {
            var beam = initialBeam
            var passBest = initialBest
            for (depth in 0 until maxDepth) {
                if (beam.isEmpty()) break
                val (advanced, candidate) = advance(beam, strategy)
                beam = advanced
                if (candidate != null && compareResult(candidate, passBest) < 0) passBest = candidate
            }
            return passBest
        }
        // Each pass owns its frontier and best candidate. Merge in fixed strategy
        // order after both complete, so thread timing cannot change route selection.
        val explored = if (explorationGoals.size == 1) listOf(explore(0)) else coroutineScope {
            explorationGoals.indices.map { strategy -> async { explore(strategy) } }.awaitAll()
        }
        for (candidate in explored) if (compareResult(candidate, best) < 0) best = candidate
        if (searchGoals.size > explorationGoals.size && !best.evaluation.goal.satisfied) {
            val candidate = explore(explorationGoals.size)
            if (compareResult(candidate, best) < 0) best = candidate
        }

        coroutineContext.ensureActive()
        val analysis = best.evaluation.analysis
        return BoardSolution(
            path = best.state.path.map(board::positionOf),
            predictedMatches = analysis,
            goalSatisfied = best.evaluation.goal.satisfied,
            preferredColor = options.goal.preferredColor,
            matchPatternFocus = options.goal.matchPatternFocus,
            focusedPatternMatchCount = options.goal.matchPatternFocus?.countMatches(analysis, options.goal.preferredColor),
            targetComboCount = options.goal.targetComboCount,
            comboMinimumSatisfied = options.goal.targetComboCount?.let { analysis.comboCount >= it },
            matchPatternSatisfied = options.goal.matchPatternFocus?.let {
                it.countMatches(analysis, options.goal.preferredColor) > 0
            },
            requirementsPossible = requirementsPossible,
            teamSkillEvaluation = options.goal.teamSkillEvaluation(analysis),
            damageReductionRequired = options.goal.damageReductionRequired,
        )
    }

    private fun compareResult(left: RankedState, right: RankedState): Int {
        val score = right.evaluation.goal.compareTo(left.evaluation.goal)
        if (score != 0) return score
        val combos = right.evaluation.analysis.comboCount.compareTo(left.evaluation.analysis.comboCount)
        return if (combos != 0) combos else compareRoute(left.state, right.state)
    }

    private fun compareSearch(left: RankedState, right: RankedState, strategy: Int): Int {
        val leftScore = left.evaluation.searchScores[strategy]
        val rightScore = right.evaluation.searchScores[strategy]
        val fulfillment = rightScore.compareFulfillment(leftScore)
        if (fulfillment != 0) return fulfillment
        if (!leftScore.satisfied) {
            val potential = right.evaluation.patternPotential.compareTo(left.evaluation.patternPotential)
            if (potential != 0) return potential
        }
        val score = rightScore.compareTo(leftScore)
        if (score != 0) return score
        val pairs = right.evaluation.nearMatchCount.compareTo(left.evaluation.nearMatchCount)
        return if (pairs != 0) pairs else compareRoute(left.state, right.state)
    }

    /** Keep different starting orbs and unmet requirements from disappearing behind early matches. */
    private fun selectBeam(candidates: Collection<RankedState>, beamWidth: Int, strategy: Int): List<RankedState> {
        val ranked = candidates.sortedWith { left, right -> compareSearch(left, right, strategy) }
        if (ranked.size <= beamWidth || beamWidth < MIN_DIVERSE_BEAM_WIDTH) return ranked.take(beamWidth)
        val starts = ranked.groupBy { it.state.path[0] }.values.map { it.iterator() }
        val frontiers = ranked.filterNot { it.evaluation.searchScores[strategy].satisfied }
            .groupBy { it.evaluation.searchScores[strategy].requirements.map(RequirementEvaluation::satisfied) }
            .values.map { it.iterator() } + starts

        val selected = LinkedHashSet<RankedState>(beamWidth)
        // Allocate slots across starting orbs, retaining goal order within each group.
        // A single early match must not consume the beam before other starts can develop.
        while (selected.size < beamWidth) {
            var added = false
            for (frontier in frontiers) {
                while (frontier.hasNext()) {
                    if (selected.add(frontier.next())) {
                        added = true
                        break
                    }
                }
                if (selected.size == beamWidth) break
            }
            if (!added) break
        }
        if (selected.size < beamWidth) {
            for (candidate in ranked) {
                selected += candidate
                if (selected.size == beamWidth) break
            }
        }
        return selected.toList()
    }

    private fun compareRoute(left: SearchState, right: SearchState): Int {
        val moves = left.path.size.compareTo(right.path.size)
        return if (moves != 0) moves else left.turns.compareTo(right.turns)
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
                while (end < columns && color != OrbColor.UNKNOWN && colorOf(cells[row * columns + end]) == color) end++
                if (end - column == 2) pairs++
                column = end
            }
        }
        for (column in 0 until columns) {
            var row = 0
            while (row < rows - 1) {
                val color = colorOf(cells[row * columns + column])
                var end = row + 1
                while (end < rows && color != OrbColor.UNKNOWN && colorOf(cells[end * columns + column]) == color) end++
                if (end - row == 2) pairs++
                row = end
            }
        }
        return pairs
    }

    private fun encodeOrb(orb: Orb): Int {
        var code = orb.color.ordinal
        if (OrbModifier.ENHANCED in orb.modifiers) code = code or ENHANCED_BIT
        if (OrbModifier.WEAKENED in orb.modifiers) code = code or WEAKENED_BIT
        if (OrbModifier.COMBO_BONUS in orb.modifiers) code = code or COMBO_BONUS_BIT
        return code
    }

    private fun colorOf(code: Int): OrbColor = OrbColor.entries[code and COLOR_MASK]
    private fun modifiersOf(code: Int): Set<OrbModifier> = buildSet {
        if (code and ENHANCED_BIT != 0) add(OrbModifier.ENHANCED)
        if (code and WEAKENED_BIT != 0) add(OrbModifier.WEAKENED)
        if (code and COMBO_BONUS_BIT != 0) add(OrbModifier.COMBO_BONUS)
    }

    private data class SearchState(
        val cells: IntArray,
        val current: Int,
        val previous: Int,
        val path: IntArray,
        val turns: Int = 0,
    ) {
        val layoutKey: String = buildString(cells.size) { cells.forEach { append(('A'.code + it).toChar()) } }
        fun signature(): String = layoutKey + ('A'.code + current).toChar() + ('A'.code + previous + 1).toChar()
    }

    private data class BoardEvaluation(
        val analysis: BoardAnalysis,
        val goal: GoalEvaluation,
        val searchScores: List<GoalEvaluation>,
        val patternPotential: Int,
        val nearMatchCount: Int,
    )
    private data class RankedState(val state: SearchState, val evaluation: BoardEvaluation)

    private companion object {
        const val COLOR_MASK = 0b1111
        const val ENHANCED_BIT = 1 shl 5
        const val WEAKENED_BIT = 1 shl 6
        const val COMBO_BONUS_BIT = 1 shl 7
        const val MIN_DIVERSE_BEAM_WIDTH = 4
        const val EVALUATION_CACHE_SIZE = 1024
    }
}
