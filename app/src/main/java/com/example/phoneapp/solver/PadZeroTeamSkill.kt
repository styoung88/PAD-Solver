package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.OrbColor

private val allAttackingAttributes = OrbColor.attackingColors.toSet()

/** The fixed team skills shown by PAD 0. Move-time, HP and recovery effects are omitted. */
enum class PadZeroTeamSkill(
    val displayName: String,
    val description: String,
    val comboTiers: List<Pair<Int, Int>> = emptyList(),
    val linkedOrbTiers: List<Pair<Int, Int>> = emptyList(),
    val attributeTiers: List<Pair<Int, Int>> = emptyList(),
    val reductionPercent: Int = 0,
    val reductionCondition: TeamSkillCondition? = null,
    val hasIndependentDamageReduction: Boolean = false,
    val bonusAttackAttributes: Int? = null,
) {
    NONE("None", "Use the board's color damage or combo count."),
    STARTING_TEAM("Starting team", "3 / 4 / 5 / 6+ combos: ATK ×5 / ×8 / ×11 / ×14.",
        comboTiers = listOf(3 to 5, 4 to 8, 5 to 11, 6 to 14)),
    COMBO("Combo", "7 / 8+ combos: ATK ×12 / ×15.",
        comboTiers = listOf(7 to 12, 8 to 15)),
    LINKED_ORB_ERASURE("Linked Orb Erasure", "6 / 7+ linked orbs: ATK ×10 / ×15. 5+ combos: 45% damage reduction.",
        linkedOrbTiers = listOf(6 to 10, 7 to 15), reductionPercent = 45,
        reductionCondition = TeamSkillCondition.Combos(5), hasIndependentDamageReduction = true),
    MULTICOLOR("Multicolor", "4 / 5 attacking attributes: ATK ×14 / ×16. 5+ combos: 45% damage reduction.",
        attributeTiers = listOf(4 to 14, 5 to 16), reductionPercent = 45,
        reductionCondition = TeamSkillCondition.Combos(5), hasIndependentDamageReduction = true),
    FIVE_COLOR_ERASURE("5 Color Erasure", "5 attacking attributes: ATK ×25, 50% damage reduction and a 10-damage bonus attack.",
        attributeTiers = listOf(5 to 25), reductionPercent = 50,
        reductionCondition = TeamSkillCondition.AttackingAttributes(5), bonusAttackAttributes = 5),
    LINK_AND_COMBO("Link & Combo", "4+ linked orbs: ATK ×9. 6+ combos: another ATK ×2 and 50% damage reduction.",
        comboTiers = listOf(6 to 2), linkedOrbTiers = listOf(4 to 9), reductionPercent = 50,
        reductionCondition = TeamSkillCondition.Combos(6), hasIndependentDamageReduction = true),
    EXPERT_COMBO("Expert Combo", "6+ combos: ATK ×10 and 50% damage reduction.",
        comboTiers = listOf(6 to 10), reductionPercent = 50,
        reductionCondition = TeamSkillCondition.Combos(6)),
    EXPERT_MULTICOLOR("Expert Multicolor", "5 attacking attributes: ATK ×10 and a 10-damage bonus attack. 6+ combos: 50% damage reduction.",
        attributeTiers = listOf(5 to 10), reductionPercent = 50,
        reductionCondition = TeamSkillCondition.Combos(6), hasIndependentDamageReduction = true,
        bonusAttackAttributes = 5);

    val usesTeamAttributeSelection: Boolean get() = this == MULTICOLOR

    fun evaluate(analysis: BoardAnalysis, teamAttributes: Set<OrbColor>): TeamSkillEvaluation {
        // Five-color presets always require all five attack colors. Attribute
        // selections retained from Multicolor must not change their activation.
        val effectiveTeamAttributes = if (usesTeamAttributeSelection) teamAttributes else allAttackingAttributes
        val attributes = analysis.attackingAttributeCount(effectiveTeamAttributes)
        val largestMatch = analysis.allMatches.maxOfOrNull { it.size } ?: 0
        fun List<Pair<Int, Int>>.multiplier(actual: Int): Int =
            lastOrNull { actual >= it.first }?.second ?: 1
        val multiplier = comboTiers.multiplier(analysis.comboCount) *
            linkedOrbTiers.multiplier(largestMatch) * attributeTiers.multiplier(attributes)
        return TeamSkillEvaluation(
            skill = this,
            attackMultiplier = multiplier,
            damageReductionPercent = if (reductionCondition?.evaluate(analysis, effectiveTeamAttributes)?.satisfied == true)
                reductionPercent else 0,
            bonusAttackDamage = if (bonusAttackAttributes?.let { attributes >= it } == true) 10 else 0,
        )
    }
}

data class TeamSkillEvaluation(
    val skill: PadZeroTeamSkill,
    val attackMultiplier: Int,
    val damageReductionPercent: Int,
    /** Fixed follow-up damage; never added to the relative damage score or combo count. */
    val bonusAttackDamage: Int,
) {
    val attackSummary: String get() = "Team ATK ×$attackMultiplier"
    val damageReductionSummary: String? get() = if (skill.reductionPercent == 0) null
        else if (damageReductionPercent > 0) "Damage reduction: $damageReductionPercent%"
        else "Damage reduction: inactive"
}

/** Attributes across the resolved turn, including cascades, intersected with team coverage. */
private fun BoardAnalysis.attackingAttributeCount(teamAttributes: Set<OrbColor>): Int =
    allMatches.asSequence().map { it.color }
        .filter { it.isAttacking && it in teamAttributes }.distinct().count()

sealed interface TeamSkillCondition {
    fun evaluate(analysis: BoardAnalysis, teamAttributes: Set<OrbColor>): RequirementEvaluation
    fun isPossible(board: Board, teamAttributes: Set<OrbColor>): Boolean

    data class Combos(val minimum: Int) : TeamSkillCondition {
        override fun evaluate(analysis: BoardAnalysis, teamAttributes: Set<OrbColor>) =
            RequirementEvaluation(analysis.comboCount, minimum)
        override fun isPossible(board: Board, teamAttributes: Set<OrbColor>) = board.comboCapacity >= minimum
    }

    data class AttackingAttributes(val minimum: Int) : TeamSkillCondition {
        override fun evaluate(analysis: BoardAnalysis, teamAttributes: Set<OrbColor>) =
            RequirementEvaluation(analysis.attackingAttributeCount(teamAttributes), minimum)
        override fun isPossible(board: Board, teamAttributes: Set<OrbColor>): Boolean =
            teamAttributes.count { color -> color.isAttacking && board.orbs.count { it.color == color } >= 3 } >= minimum
    }
}

data class DamageReductionRequirement(
    val condition: TeamSkillCondition,
    val teamAttributes: Set<OrbColor>,
) : SolveRequirement {
    override fun evaluate(analysis: BoardAnalysis) = condition.evaluate(analysis, teamAttributes)
    override fun isPossible(board: Board) = condition.isPossible(board, teamAttributes)
}
