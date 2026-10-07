package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.OrbColor

data class RequirementEvaluation(val actual: Int, val minimum: Int) {
    val satisfied: Boolean get() = actual >= minimum
    val progress: Int get() = minOf(actual, minimum)
}

/** Requirements are ordered by user priority; extra matches do not improve fulfillment. */
data class GoalEvaluation(
    val requirements: List<RequirementEvaluation> = emptyList(),
    val primaryScore: Long,
    val secondaryScore: Int = 0,
) : Comparable<GoalEvaluation> {
    val satisfied: Boolean get() = requirements.all { it.satisfied }

    internal fun compareFulfillment(other: GoalEvaluation): Int {
        val overall = satisfied.compareTo(other.satisfied)
        if (overall != 0) return overall
        for (index in requirements.indices) {
            val order = requirements[index].satisfied.compareTo(other.requirements[index].satisfied)
            if (order != 0) return order
        }
        return 0
    }

    override fun compareTo(other: GoalEvaluation): Int {
        val overall = satisfied.compareTo(other.satisfied)
        if (overall != 0) return overall
        for (index in requirements.indices) {
            val progress = requirements[index].progress.compareTo(other.requirements[index].progress)
            if (progress != 0) return progress
        }
        val primary = primaryScore.compareTo(other.primaryScore)
        return if (primary != 0) primary else secondaryScore.compareTo(other.secondaryScore)
    }
}

sealed interface SolveRequirement {
    fun evaluate(analysis: BoardAnalysis): RequirementEvaluation
    fun isPossible(board: Board): Boolean
    fun potential(board: Board): Int = 0
}

data class ComboMinimum(val count: Int) : SolveRequirement {
    init { require(count > 0) }

    override fun evaluate(analysis: BoardAnalysis) = RequirementEvaluation(analysis.comboCount, count)

    override fun isPossible(board: Board): Boolean = count <= board.comboCapacity
}

/** Inventory upper bound without skyfall; a route attaining it need not exist. */
internal val Board.comboCapacity: Int get() = orbs.filter { it.color != OrbColor.UNKNOWN }
    .groupingBy { it.color }.eachCount().values.sumOf { it / 3 }

data class RequiredPattern(val pattern: MatchPattern, val color: OrbColor? = null) : SolveRequirement {
    init { require(color == null || color.isAttacking) }

    override fun evaluate(analysis: BoardAnalysis) =
        RequirementEvaluation(pattern.countMatches(analysis, color), 1)

    override fun isPossible(board: Board): Boolean = pattern.canForm(board, color)
    override fun potential(board: Board): Int = pattern.potentialScore(board, color)
}

/** Board-only evaluation; drag position and path history do not affect its cached score. */
fun interface SolveGoal {
    fun evaluate(analysis: BoardAnalysis): GoalEvaluation

    val requirements: List<SolveRequirement> get() = emptyList()
    val preferredColor: OrbColor? get() = null
    val matchPatternFocus: MatchPattern? get() = requirements.filterIsInstance<RequiredPattern>()
        .singleOrNull()?.pattern
    val targetComboCount: Int? get() = requirements.filterIsInstance<ComboMinimum>().singleOrNull()?.count

    fun patternPotential(board: Board): Int = requirements.sumOf { it.potential(board) }
}

object MaximizeCombos : SolveGoal {
    override fun evaluate(analysis: BoardAnalysis) = GoalEvaluation(primaryScore = analysis.comboCount.toLong())
}

/** Compose the current requirements independently of the damage or combo objective. */
internal class ConfiguredGoal(
    override val preferredColor: OrbColor?,
    override val requirements: List<SolveRequirement>,
) : SolveGoal {
    override fun evaluate(analysis: BoardAnalysis) = GoalEvaluation(
        requirements = requirements.map { it.evaluate(analysis) },
        primaryScore = preferredColor?.let { ColorDamageEstimate.scoreRelativeDamage(analysis, it) }
            ?: analysis.comboCount.toLong(),
        secondaryScore = analysis.comboCount,
    )
}

/** Board contribution scaled by 1600: match size × enhanced-orb factor × total combo factor. */
object ColorDamageEstimate {
    fun scoreRelativeDamage(analysis: BoardAnalysis, color: OrbColor): Long {
        require(color.isAttacking)
        val comboFactorInQuarters = analysis.comboCount.toLong() + 3
        return analysis.allMatches.asSequence().filter { it.color == color }.sumOf { match ->
            comboFactorInQuarters * (match.size + 1) * (100 + 6L * match.enhancedOrbCount)
        }
    }
}
