package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs

/** Replay the returned route rather than trusting the solver's reported score. */
internal fun assertValidSolution(board: Board, options: SolveOptions, solution: BoardSolution) {
    assertTrue("A route must include its starting cell", solution.path.isNotEmpty())
    assertTrue("The route exceeds its move limit", solution.orbMoves <= options.maxOrbMoves)
    solution.path.forEach { board.indexOf(it) }
    val replay = board.orbs.toMutableList()
    solution.path.zipWithNext().forEach { (from, to) ->
        assertEquals("Route moves must be adjacent", 1,
            abs(from.row - to.row) + abs(from.column - to.column))
        val first = board.indexOf(from)
        val second = board.indexOf(to)
        val held = replay[first]
        replay[first] = replay[second]
        replay[second] = held
    }
    val analysis = BoardAnalyzer.resolveCascades(board.copy(orbs = replay))
    val goal = options.goal
    assertEquals(analysis, solution.predictedMatches)
    assertEquals(goal.evaluate(analysis).satisfied, solution.goalSatisfied)
    assertEquals(goal.preferredColor, solution.preferredColor)
    assertEquals(goal.teamSkillEvaluation(analysis), solution.teamSkillEvaluation)
    assertEquals(goal.damageReductionRequired, solution.damageReductionRequired)
    assertEquals(goal.matchPatternFocus, solution.matchPatternFocus)
    assertEquals(goal.targetComboCount, solution.targetComboCount)
    assertEquals(goal.targetComboCount?.let { analysis.comboCount >= it }, solution.comboMinimumSatisfied)
    assertEquals(goal.matchPatternFocus?.countMatches(analysis, goal.preferredColor), solution.focusedPatternMatchCount)
    assertEquals(goal.matchPatternFocus?.let { it.countMatches(analysis, goal.preferredColor) > 0 },
        solution.matchPatternSatisfied)
}
