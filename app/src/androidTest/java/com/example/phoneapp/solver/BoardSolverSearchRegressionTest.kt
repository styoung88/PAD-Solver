package com.example.phoneapp.solver

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardSolverSearchRegressionTest {
    @Test
    fun nonbindingMinimumRetainsTheBetterDarkTpaResultOnDevice() = runBlocking {
        val enhanced = setOf(0, 3, 4, 5, 6, 8, 9, 10, 11, 12, 15, 17, 20, 21, 29)
        val board = Board(orbs = ("DDDFFF" + "FFDFFF" + "DFFFDD" + "DFFFFD" + "FDFFFD")
            .mapIndexed { index, color -> Orb(
                if (color == 'D') OrbColor.DARK else OrbColor.FIRE,
                if (index in enhanced) setOf(OrbModifier.ENHANCED) else emptySet(),
            ) })
        val settings = SolveSettings(OrbColor.DARK, matchPatternFocus = MatchPattern.TPA)
        val results = (0..2).map { iteration ->
            listOf(settings, settings.copy(targetComboCount = 5)).map { selected ->
                val started = SystemClock.elapsedRealtime()
                BoardSolver().solve(board, SolveOptions(goal = selected.toGoal())).also { solution ->
                    Log.i("SolverSearchTiming", "iteration=$iteration minimum=${selected.targetComboCount} " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - started} moves=${solution.orbMoves} " +
                        "damage=${ColorDamageEstimate.scoreRelativeDamage(solution.predictedMatches, OrbColor.DARK)}")
                }
            }
        }
        val goal = settings.copy(targetComboCount = 5).toGoal()
        for ((baseline, constrained) in results) {
            assertTrue(goal.evaluate(baseline.predictedMatches).satisfied)
            assertTrue(goal.evaluate(baseline.predictedMatches).primaryScore >= 12888L)
            assertEquals(goal.evaluate(baseline.predictedMatches), goal.evaluate(constrained.predictedMatches))
            assertEquals(baseline.orbMoves, constrained.orbMoves)
        }
    }
}
