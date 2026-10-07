package com.example.phoneapp.solver

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class BoardSolverPerformanceTest {
    @Test fun darkTpa() = compare("dark_tpa", darkBoard(), SolveSettings(OrbColor.DARK, matchPatternFocus = MatchPattern.TPA))
    @Test fun darkTpaMinimum() = compare("dark_tpa_minimum", darkBoard(), SolveSettings(OrbColor.DARK, 5, MatchPattern.TPA))
    @Test fun woodTpaMinimum() = compare("wood_tpa_minimum", woodBoard(), SolveSettings(OrbColor.WOOD, 5, MatchPattern.TPA))
    @Test fun fireLStrictMinimum() = compare("fire_l_strict", twoColorBoard(), SolveSettings(OrbColor.FIRE, 8, MatchPattern.L_SHAPE))
    @Test fun fireVdp() = compare("fire_vdp", twoColorBoard(), SolveSettings(OrbColor.FIRE, matchPatternFocus = MatchPattern.VDP))
    @Test fun waterDamage() = compare("water_damage", woodBoard(), SolveSettings(OrbColor.WATER))
    @Test fun combosOnlyControl() = compare("combos_control", woodBoard(), SolveSettings())
    @Test fun smallBoard() = compare("5x4_30_moves", sizedBoard(BoardSize.SMALL), SolveSettings(OrbColor.FIRE))
    @Test fun largeBoard() = compare("7x6_50_moves", sizedBoard(BoardSize.LARGE), SolveSettings(OrbColor.FIRE))

    private fun sizedBoard(size: BoardSize): Board = Board(size.columns, size.rows,
        List(size.cellCount) { Orb(OrbColor.entries[it % 6]) }.shuffled(Random(37)))

    private fun compare(name: String, board: Board, settings: SolveSettings): Unit = runBlocking {
        // The same algorithm and cache run on one worker or the default CPU pool.
        // This isolates parallel execution without a benchmark switch in product settings.
        val solvers = listOf(BoardSolver(Dispatchers.Default.limitedParallelism(1)), BoardSolver())
        val options = SolveOptions.forBoard(board, settings.toGoal())
        val expected = solvers[0].solve(board, options)
        assertEquals(expected, solvers[1].solve(board, options))
        val samples = List(2) { mutableListOf<Long>() }
        repeat(5) { iteration ->
            val order = if (iteration % 2 == 0) listOf(0, 1) else listOf(1, 0)
            for (mode in order) {
                val started = SystemClock.elapsedRealtimeNanos()
                val result = solvers[mode].solve(board, options)
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
                samples[mode] += elapsedMs
                assertEquals("$name mode=$mode iteration=$iteration", expected, result)
                Log.i("SolverParallelTiming", "variant=whole_pass case=$name mode=${if (mode == 0) "serial" else "parallel"} " +
                    "iteration=$iteration elapsedMs=$elapsedMs")
            }
        }
        Log.i("SolverParallelTiming", "SUMMARY variant=whole_pass case=$name serialMedianMs=${samples[0].sorted()[2]} " +
            "parallelMedianMs=${samples[1].sorted()[2]} serialSamples=${samples[0]} parallelSamples=${samples[1]}")
    }

    private fun darkBoard(): Board {
        val enhanced = setOf(0, 3, 4, 5, 6, 8, 9, 10, 11, 12, 15, 17, 20, 21, 29)
        return Board(orbs = ("DDDFFF" + "FFDFFF" + "DFFFDD" + "DFFFFD" + "FDFFFD")
            .mapIndexed { index, color -> Orb(if (color == 'D') OrbColor.DARK else OrbColor.FIRE,
                if (index in enhanced) setOf(OrbModifier.ENHANCED) else emptySet()) })
    }

    private fun woodBoard(): Board = Board(orbs = ("FHDGDB" + "LFBFBG" + "HBHLLG" + "FFGHDL" + "DDFLGL")
        .mapIndexed { index, color -> Orb(when (color) {
            'F' -> OrbColor.FIRE
            'H' -> OrbColor.HEART
            'D' -> OrbColor.DARK
            'G' -> OrbColor.WOOD
            'B' -> OrbColor.WATER
            'L' -> OrbColor.LIGHT
            else -> error("Unknown fixture color")
        }, if (index in setOf(1, 26)) setOf(OrbModifier.ENHANCED) else emptySet()) })

    private fun twoColorBoard(): Board = Board(orbs =
        (List(15) { Orb(OrbColor.FIRE) } + List(15) { Orb(OrbColor.WATER) }).shuffled(Random(37)))
}
