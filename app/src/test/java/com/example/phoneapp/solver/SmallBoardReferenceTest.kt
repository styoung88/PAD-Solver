package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SmallBoardReferenceTest {
    private data class Score(val objective: Long, val combos: Int, val moves: Int, val turns: Int)

    @Test
    fun sufficientlyWideSearchAgreesWithAnIndependentReferenceOnTinyBoards() = runBlocking {
        // On a 3x2 board only full horizontal rows can match. Clearing either
        // row drops the other intact, so no new cascade can form. This oracle
        // deliberately does not use BoardAnalyzer or the solver's comparator.
        for (mask in 0 until 64) {
            if (Integer.bitCount(mask) != 3) continue
            val cells = IntArray(6) { if (mask and (1 shl it) != 0) 1 else 0 }
            val board = Board(3, 2, cells.map { Orb(if (it == 1) OrbColor.FIRE else OrbColor.WATER) })
            for (focus in listOf(null, OrbColor.FIRE)) {
                var expected = score(cells, focus, 0, 0)
                fun visit(current: Int, previous: Int, state: IntArray, depth: Int, turns: Int) {
                    val candidate = score(state, focus, depth, turns)
                    if (better(candidate, expected)) expected = candidate
                    if (depth == 4) return
                    for (next in cells.indices) {
                        if (next == previous || distance(current, next) != 1) continue
                        val moved = state.copyOf()
                        val held = moved[current]
                        moved[current] = moved[next]
                        moved[next] = held
                        val turn = previous >= 0 && next - current != current - previous
                        visit(next, current, moved, depth + 1, turns + if (turn) 1 else 0)
                    }
                }
                cells.indices.forEach { visit(it, -1, cells, 0, 0) }
                // This width retains the complete frontier for these small fixtures.
                val result = BoardSolver().solve(board, SolveOptions(4, 1024, SolveSettings(focus).toGoal()))
                val replay = cells.copyOf()
                result.path.zipWithNext().forEach { (from, to) ->
                    val first = board.indexOf(from)
                    val second = board.indexOf(to)
                    assertEquals(1, distance(first, second))
                    val held = replay[first]
                    replay[first] = replay[second]
                    replay[second] = held
                }
                val turns = result.path.windowed(3).count { (first, middle, last) ->
                    direction(first, middle) != direction(middle, last)
                }
                assertEquals("mask=$mask, focus=$focus", expected, score(replay, focus, result.orbMoves, turns))
                assertEquals(expected.combos, result.comboCount)
            }
        }
    }

    private fun distance(first: Int, second: Int): Int =
        kotlin.math.abs(first / 3 - second / 3) + kotlin.math.abs(first % 3 - second % 3)

    private fun direction(first: BoardPosition, second: BoardPosition): Pair<Int, Int> =
        second.row - first.row to second.column - first.column

    private fun score(cells: IntArray, focus: OrbColor?, moves: Int, turns: Int): Score {
        val matches = (0..1).filter { row ->
            val offset = row * 3
            cells[offset] == cells[offset + 1] && cells[offset] == cells[offset + 2]
        }
        val fireMatches = matches.count { cells[it * 3] == 1 }
        val objective = if (focus == null) matches.size.toLong() else fireMatches * 400L * (matches.size + 3)
        return Score(objective, matches.size, moves, turns)
    }

    private fun better(candidate: Score, best: Score): Boolean = when {
        candidate.objective != best.objective -> candidate.objective > best.objective
        candidate.combos != best.combos -> candidate.combos > best.combos
        candidate.moves != best.moves -> candidate.moves < best.moves
        else -> candidate.turns < best.turns
    }
}
