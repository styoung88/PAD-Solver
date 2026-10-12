package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbMatch
import com.example.phoneapp.board.OrbModifier
import com.example.phoneapp.game.GameVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PadZeroTeamSkillTest {
    private val allAttributes = OrbColor.attackingColors.toSet()

    @Test fun comboTiersUseTheHighestTierWithoutStacking() {
        val expected = mapOf(
            PadZeroTeamSkill.STARTING_TEAM to listOf(1, 1, 1, 5, 8, 11, 14, 14, 14, 14),
            PadZeroTeamSkill.COMBO to listOf(1, 1, 1, 1, 1, 1, 1, 12, 15, 15),
            PadZeroTeamSkill.EXPERT_COMBO to listOf(1, 1, 1, 1, 1, 1, 10, 10, 10, 10),
        )
        for ((skill, multipliers) in expected) for ((combos, multiplier) in multipliers.withIndex()) {
            assertEquals("$skill at $combos combos", multiplier, evaluate(skill, comboAnalysis(combos)).attackMultiplier)
        }
    }

    @Test fun linkedTiersUseOneConnectedMatchIncludingNonAttackingOrbs() {
        val skill = PadZeroTeamSkill.LINKED_ORB_ERASURE
        assertEquals(1, evaluate(skill, BoardAnalysis(listOf(match(OrbColor.FIRE), match(OrbColor.FIRE)))).attackMultiplier)
        for ((size, multiplier) in listOf(5 to 1, 6 to 10, 7 to 15, 9 to 15)) {
            assertEquals(multiplier, evaluate(skill, BoardAnalysis(listOf(match(OrbColor.HEART, size)))).attackMultiplier)
        }
        assertEquals(10, evaluate(skill, BoardAnalysis(listOf(match(OrbColor.JAMMER, 6)))).attackMultiplier)
    }

    @Test fun independentLinkAndComboAttackClausesMultiply() {
        val skill = PadZeroTeamSkill.LINK_AND_COMBO
        fun analysis(combos: Int, size: Int) = BoardAnalysis(
            listOf(match(OrbColor.FIRE, size)) + List(combos - 1) { match(OrbColor.HEART) })
        assertEquals(1, evaluate(skill, analysis(5, 3)).attackMultiplier)
        assertEquals(9, evaluate(skill, analysis(5, 4)).attackMultiplier)
        assertEquals(2, evaluate(skill, analysis(6, 3)).attackMultiplier)
        assertEquals(18, evaluate(skill, analysis(6, 4)).attackMultiplier)
    }

    @Test fun multicolorConditionsCountTeamAttackAttributesAcrossCascades() {
        val firstWave = allAttributes.take(3).map { match(it) }
        val secondWave = allAttributes.drop(3).map { match(it) }
        val fiveColors = BoardAnalysis(firstWave, listOf(firstWave, secondWave))
        assertEquals(16, evaluate(PadZeroTeamSkill.MULTICOLOR, fiveColors).attackMultiplier)
        val fourAttributes = allAttributes - OrbColor.DARK
        assertEquals(14, PadZeroTeamSkill.MULTICOLOR.evaluate(fiveColors, fourAttributes).attackMultiplier)
        assertEquals(1, PadZeroTeamSkill.MULTICOLOR.evaluate(fiveColors, fourAttributes - OrbColor.LIGHT).attackMultiplier)
        val fourColorsAndHeart = BoardAnalysis(fourAttributes.map { match(it) } + match(OrbColor.HEART))
        assertEquals(1, evaluate(PadZeroTeamSkill.FIVE_COLOR_ERASURE, fourColorsAndHeart).attackMultiplier)
        assertEquals(1, evaluate(PadZeroTeamSkill.EXPERT_MULTICOLOR, fourColorsAndHeart).attackMultiplier)
        assertEquals(25, evaluate(PadZeroTeamSkill.FIVE_COLOR_ERASURE, fiveColors).attackMultiplier)
        assertEquals(10, evaluate(PadZeroTeamSkill.EXPERT_MULTICOLOR, fiveColors).attackMultiplier)
        for (coverage in listOf(emptySet(), fourAttributes)) {
            val expert = PadZeroTeamSkill.EXPERT_MULTICOLOR.evaluate(fiveColors, coverage)
            assertEquals(10, expert.attackMultiplier)
            assertEquals(10, expert.bonusAttackDamage)
            val missingColor = PadZeroTeamSkill.EXPERT_MULTICOLOR.evaluate(fourColorsAndHeart, coverage)
            assertEquals(1, missingColor.attackMultiplier)
            assertEquals(0, missingColor.bonusAttackDamage)
            val fiveColor = PadZeroTeamSkill.FIVE_COLOR_ERASURE.evaluate(fiveColors, coverage)
            assertEquals(25, fiveColor.attackMultiplier)
            assertEquals(10, fiveColor.bonusAttackDamage)
            assertEquals(50, fiveColor.damageReductionPercent)
            val missingFiveColor = PadZeroTeamSkill.FIVE_COLOR_ERASURE.evaluate(fourColorsAndHeart, coverage)
            assertEquals(1, missingFiveColor.attackMultiplier)
            assertEquals(0, missingFiveColor.bonusAttackDamage)
            assertEquals(0, missingFiveColor.damageReductionPercent)
        }
    }

    @Test fun bonusCombosActivateComboRulesButDoNotCreateAttributesOrLinkedOrbs() {
        val analysis = BoardAnalysis(listOf(match(OrbColor.FIRE, bonus = 3), match(OrbColor.HEART, bonus = 1)))
        assertEquals(2, analysis.physicalComboCount)
        assertEquals(6, analysis.comboCount)
        assertEquals(10, evaluate(PadZeroTeamSkill.EXPERT_COMBO, analysis).attackMultiplier)
        assertEquals(50, evaluate(PadZeroTeamSkill.EXPERT_COMBO, analysis).damageReductionPercent)
        assertEquals(14, evaluate(PadZeroTeamSkill.STARTING_TEAM, analysis).attackMultiplier)
        assertEquals(1, evaluate(PadZeroTeamSkill.LINKED_ORB_ERASURE, analysis).attackMultiplier)
        assertEquals(1, evaluate(PadZeroTeamSkill.MULTICOLOR, analysis).attackMultiplier)
    }

    @Test fun damageReductionIsEvaluatedSeparatelyFromAttackAndFixedFollowUp() {
        val rainbow = BoardAnalysis(allAttributes.map { match(it) })
        val expert = evaluate(PadZeroTeamSkill.EXPERT_MULTICOLOR, rainbow)
        assertEquals(10, expert.attackMultiplier)
        assertEquals(10, expert.bonusAttackDamage)
        assertEquals(0, expert.damageReductionPercent)
        val fiveColor = evaluate(PadZeroTeamSkill.FIVE_COLOR_ERASURE, rainbow)
        assertEquals(50, fiveColor.damageReductionPercent)
        assertEquals(10, fiveColor.bonusAttackDamage)
        for (skill in PadZeroTeamSkill.entries.filter { it.reductionCondition is TeamSkillCondition.Combos }) {
            val minimum = (skill.reductionCondition as TeamSkillCondition.Combos).minimum
            assertEquals(0, evaluate(skill, comboAnalysis(minimum - 1)).damageReductionPercent)
            assertEquals(skill.reductionPercent, evaluate(skill, comboAnalysis(minimum)).damageReductionPercent)
        }
        // Appearance chance never adds a speculative combo.
        assertEquals(5, rainbow.comboCount)
        assertEquals(1, evaluate(PadZeroTeamSkill.COMBO, rainbow).attackMultiplier)
    }

    @Test fun gameSpecificChildrenPreservePadAndNoneScoresAndApplyTeamAttack() {
        val analysis = BoardAnalysis(listOf(match(OrbColor.WOOD, 4, bonus = 1), match(OrbColor.FIRE, bonus = 3)))
        val pad = PadDamageEvaluator()
        val none = PadZeroDamageEvaluator(PadZeroTeamSkill.NONE)
        assertEquals(pad.score(analysis, null), none.score(analysis, null))
        assertEquals(pad.score(analysis, OrbColor.WOOD), none.score(analysis, OrbColor.WOOD))
        val skill = PadZeroDamageEvaluator(PadZeroTeamSkill.EXPERT_COMBO)
        assertEquals(pad.score(analysis, OrbColor.WOOD) * 10, skill.score(analysis, OrbColor.WOOD))
        assertEquals(allAttributes.sumOf { pad.score(analysis, it) } * 10, skill.score(analysis, null))
        val padSettings = settings(PadZeroTeamSkill.EXPERT_COMBO, required = true).copy(game = GameVariant.PAD)
        assertEquals(SolveSettings().toGoal().evaluate(analysis), padSettings.toGoal().evaluate(analysis))
        assertNull(padSettings.toGoal().teamSkillEvaluation(analysis))
        assertTrue(padSettings.toGoal().requirements.isEmpty())
    }

    @Test fun teamAttackChangesRankingAndRequiredReductionTakesPriority() {
        val analysis = BoardAnalysis(allAttributes.map { match(it) })
        val extraHeart = BoardAnalysis(analysis.matches + match(OrbColor.HEART))
        val reducedCoverage = allAttributes - OrbColor.DARK
        val goal = settings(PadZeroTeamSkill.EXPERT_MULTICOLOR).copy(preferredColor = OrbColor.WOOD).toGoal()
        val missingColor = BoardAnalysis(reducedCoverage.map { match(it) } + List(3) { match(OrbColor.HEART) })
        assertTrue(goal.evaluate(analysis) > goal.evaluate(missingColor))
        val required = settings(PadZeroTeamSkill.EXPERT_MULTICOLOR, required = true).toGoal()
        assertFalse(required.evaluate(analysis).satisfied)
        assertTrue(required.evaluate(extraHeart).satisfied)
        assertTrue(required.evaluate(extraHeart) > required.evaluate(analysis))
        assertTrue(settings(PadZeroTeamSkill.EXPERT_MULTICOLOR).toGoal().evaluate(analysis).satisfied)
        assertFalse(settings(PadZeroTeamSkill.EXPERT_COMBO, required = true).toGoal().damageReductionRequired)
        assertFalse(settings(PadZeroTeamSkill.FIVE_COLOR_ERASURE, required = true).toGoal().damageReductionRequired)
    }

    @Test fun multicolorDamageExcludesAttributesTheTeamCannotAttackWith() {
        val analysis = BoardAnalysis(allAttributes.map { match(it) })
        val evaluator = PadZeroDamageEvaluator(PadZeroTeamSkill.MULTICOLOR, allAttributes - OrbColor.DARK)
        assertEquals(0L, evaluator.score(analysis, OrbColor.DARK))
        assertEquals((allAttributes - OrbColor.DARK).sumOf { PadDamageEvaluator().score(analysis, it) } * 14,
            evaluator.score(analysis, null))
        assertEquals(0L, PadZeroDamageEvaluator(PadZeroTeamSkill.MULTICOLOR, emptySet()).score(analysis, null))
        val expert = PadZeroDamageEvaluator(PadZeroTeamSkill.EXPERT_MULTICOLOR, emptySet())
        assertEquals(allAttributes.sumOf { PadDamageEvaluator().score(analysis, it) } * 10, expert.score(analysis, null))
        assertEquals(PadDamageEvaluator().score(analysis, OrbColor.DARK) * 10, expert.score(analysis, OrbColor.DARK))
        val fiveColor = PadZeroDamageEvaluator(PadZeroTeamSkill.FIVE_COLOR_ERASURE, emptySet())
        assertEquals(allAttributes.sumOf { PadDamageEvaluator().score(analysis, it) } * 25, fiveColor.score(analysis, null))
        assertEquals(PadDamageEvaluator().score(analysis, OrbColor.DARK) * 25, fiveColor.score(analysis, OrbColor.DARK))
        assertThrows(IllegalArgumentException::class.java) {
            SolveSettings(teamAttackingAttributes = setOf(OrbColor.HEART))
        }
    }

    @Test fun inventoryBoundsIncludeBonusesAndAttributeCoverage() {
        val board = Board(3, 2, List(6) { index -> Orb(if (index < 3) OrbColor.FIRE else OrbColor.WATER,
            if (index < 4) setOf(OrbModifier.COMBO_BONUS) else emptySet()) })
        val comboReduction = DamageReductionRequirement(TeamSkillCondition.Combos(6), allAttributes)
        assertTrue(comboReduction.isPossible(board))
        assertFalse(comboReduction.isPossible(board.copy(orbs = board.orbs.map { it.copy(modifiers = emptySet()) })))
        val attributes = DamageReductionRequirement(TeamSkillCondition.AttackingAttributes(2), setOf(OrbColor.FIRE))
        assertFalse(attributes.isPossible(board))
        assertTrue(attributes.copy(teamAttributes = setOf(OrbColor.FIRE, OrbColor.WATER)).isPossible(board))
    }

    @Test fun solverReportsTheChosenSkillAndDoesNotDropReductionDuringSearch() = runBlocking {
        // Five separate color matches are attainable by undoing one swap.
        val orbs = OrbColor.attackingColors.flatMap { color -> List(3) { Orb(color) } }.toMutableList()
        val held = orbs[0]
        orbs[0] = orbs[3]
        orbs[3] = held
        val board = Board(3, 5, orbs)
        val settings = settings(PadZeroTeamSkill.MULTICOLOR, required = true).copy(preferredColor = OrbColor.WOOD, targetComboCount = 5)
        val options = settings.toOptions(board).copy(maxOrbMoves = 4, beamWidth = 60)
        val solution = BoardSolver().solve(board, options)
        assertValidSolution(board, options, solution)
        assertTrue(solution.goalSatisfied)
        assertTrue(solution.orbMoves > 0)
        assertTrue(solution.damageReductionRequired)
        assertEquals(45, solution.teamSkillEvaluation?.damageReductionPercent)
        assertEquals(evaluate(PadZeroTeamSkill.MULTICOLOR, solution.predictedMatches), solution.teamSkillEvaluation)
    }

    @Test fun impossibleReductionReturnsForAdjustment() = runBlocking {
        val board = Board(3, 1, List(3) { Orb(OrbColor.FIRE) })
        val options = settings(PadZeroTeamSkill.EXPERT_MULTICOLOR, required = true).toOptions(board)
        val solution = BoardSolver().solve(board, options)
        assertFalse(solution.requirementsPossible)
        assertFalse(solution.goalSatisfied)
        assertEquals(0, solution.orbMoves)
        assertEquals(0, solution.teamSkillEvaluation?.damageReductionPercent)
    }

    private fun settings(skill: PadZeroTeamSkill, required: Boolean = false) =
        SolveSettings(game = GameVariant.PAD_ZERO, padZeroTeamSkill = skill, requireDamageReduction = required)
    private fun evaluate(skill: PadZeroTeamSkill, analysis: BoardAnalysis) = skill.evaluate(analysis, allAttributes)
    private fun comboAnalysis(combos: Int) = BoardAnalysis(List(combos) { match(OrbColor.FIRE) })
    private fun match(color: OrbColor, size: Int = 3, bonus: Int = 0) =
        OrbMatch(color, (0 until size).map { BoardPosition(0, it) }.toSet(), comboBonusOrbCount = bonus)
}
