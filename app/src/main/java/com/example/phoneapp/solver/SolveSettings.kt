package com.example.phoneapp.solver

import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.Board
import com.example.phoneapp.game.GameVariant
import kotlin.math.roundToInt

enum class OrbMovement(val displayName: String, private val multiplier: Double) {
    LOW("Low", 0.75), MEDIUM("Medium", 1.0), HIGH("High", 1.25);

    fun moveLimit(baseline: Int): Int = (baseline * multiplier).roundToInt()
}

enum class SearchComplexity(val displayName: String, private val multiplier: Double) {
    LOW("Low", 0.75), MEDIUM("Medium", 1.0), HIGH("High", 1.25), VERY_HIGH("Very high", 1.5);

    val beamWidth: Int get() = (DEFAULT_BEAM_WIDTH * multiplier).roundToInt()

    companion object {
        const val DEFAULT_BEAM_WIDTH = 200
    }
}

/** User-selected objectives shared by the app and the automatic overlay solver. */
data class SolveSettings(
    val preferredColor: OrbColor? = null,
    val targetComboCount: Int? = null,
    val matchPatternFocus: MatchPattern? = null,
    val orbMovement: OrbMovement = OrbMovement.MEDIUM,
    val searchComplexity: SearchComplexity = SearchComplexity.MEDIUM,
    val game: GameVariant = GameVariant.PAD,
    val padZeroTeamSkill: PadZeroTeamSkill = PadZeroTeamSkill.NONE,
    val requireDamageReduction: Boolean = false,
    val teamAttackingAttributes: Set<OrbColor> = OrbColor.attackingColors.toSet(),
) {
    init {
        require(preferredColor == null || preferredColor.isAttacking)
        require(targetComboCount == null || targetComboCount > 0)
        require(teamAttackingAttributes.all { it.isAttacking })
    }

    fun toGoal(): SolveGoal = ConfiguredGoal(preferredColor, buildList {
        targetComboCount?.let { add(ComboMinimum(it)) }
        matchPatternFocus?.let { add(RequiredPattern(it, preferredColor)) }
        if (game == GameVariant.PAD_ZERO && requireDamageReduction && padZeroTeamSkill.hasIndependentDamageReduction) {
            padZeroTeamSkill.reductionCondition?.let { add(DamageReductionRequirement(it, teamAttackingAttributes)) }
        }
    }, if (game == GameVariant.PAD_ZERO) PadZeroDamageEvaluator(padZeroTeamSkill, teamAttackingAttributes)
        else PadDamageEvaluator())

    fun toOptions(board: Board): SolveOptions {
        val baseline = SolveOptions.forBoard(board, toGoal())
        return baseline.copy(
            maxOrbMoves = orbMovement.moveLimit(baseline.maxOrbMoves),
            beamWidth = searchComplexity.beamWidth,
        )
    }
}
