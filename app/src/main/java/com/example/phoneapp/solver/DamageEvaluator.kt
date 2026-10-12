package com.example.phoneapp.solver

import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.OrbColor

/** Shared board contribution; subclasses apply game-specific attack rules. */
abstract class DamageEvaluator {
    open val usesTeamSkill: Boolean get() = false
    open fun teamSkillEvaluation(analysis: BoardAnalysis): TeamSkillEvaluation? = null

    open fun score(analysis: BoardAnalysis, color: OrbColor?): Long =
        color?.let { scoreRelativeDamage(analysis, it) } ?: analysis.comboCount.toLong()

    /** Board contribution scaled by 1600: match size × enhanced-orb factor × total combo factor. */
    protected fun scoreRelativeDamage(analysis: BoardAnalysis, color: OrbColor): Long {
        require(color.isAttacking)
        val comboFactorInQuarters = analysis.comboCount.toLong() + 3
        return analysis.allMatches.asSequence().filter { it.color == color }.sumOf { match ->
            // One remaining minus halves damage; two or more reduce it to zero.
            val netEnhanced = match.enhancedOrbCount - match.weakenedOrbCount
            val weakenedFactor = when {
                netEnhanced >= 0 -> 2
                netEnhanced == -1 -> 1
                else -> 0
            }
            comboFactorInQuarters * (match.size + 1) *
                (100 + 6L * netEnhanced.coerceAtLeast(0)) * weakenedFactor / 2
        }
    }
}

class PadDamageEvaluator : DamageEvaluator()

class PadZeroDamageEvaluator(
    private val skill: PadZeroTeamSkill,
    private val teamAttributes: Set<OrbColor> = OrbColor.attackingColors.toSet(),
) : DamageEvaluator() {
    override val usesTeamSkill: Boolean get() = skill != PadZeroTeamSkill.NONE
    override fun teamSkillEvaluation(analysis: BoardAnalysis): TeamSkillEvaluation? =
        if (usesTeamSkill) skill.evaluate(analysis, teamAttributes) else null

    override fun score(analysis: BoardAnalysis, color: OrbColor?): Long {
        if (!usesTeamSkill) return super.score(analysis, color)
        // Without creature stats, give each attacking color equal weight. A color
        // focus keeps the existing per-color objective and applies the team ATK.
        val colors = if (color != null) listOf(color).filter { !skill.usesTeamAttributeSelection || it in teamAttributes }
            else if (skill.usesTeamAttributeSelection) teamAttributes else OrbColor.attackingColors
        val boardDamage = colors.sumOf { scoreRelativeDamage(analysis, it) }
        return boardDamage * skill.evaluate(analysis, teamAttributes).attackMultiplier
    }
}

/** Compatibility entry point for PAD's established per-color estimate. */
object ColorDamageEstimate {
    private val evaluator = PadDamageEvaluator()
    fun scoreRelativeDamage(analysis: BoardAnalysis, color: OrbColor): Long = evaluator.score(analysis, color)
}
