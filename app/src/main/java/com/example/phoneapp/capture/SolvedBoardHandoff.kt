package com.example.phoneapp.capture

import android.content.Intent
import com.example.phoneapp.game.GameVariant
import com.example.phoneapp.solver.BoardSolution
import java.util.UUID

internal data class SolvedBoard(
    val game: GameVariant,
    val recognition: BoardRecognition.Recognized,
    val solution: BoardSolution,
)

/** Transfers the exact result between the activity and service in the same process. */
internal object SolvedBoardHandoff {
    private const val EXTRA_TOKEN = "com.example.phoneapp.extra.SOLVED_BOARD_TOKEN"
    private var pending: Pair<String, SolvedBoard>? = null

    fun attachTo(intent: Intent, board: SolvedBoard) {
        val token = UUID.randomUUID().toString()
        pending = token to board
        intent.putExtra(EXTRA_TOKEN, token)
    }

    fun takeFrom(intent: Intent): SolvedBoard? {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return null
        val (expected, board) = pending ?: return null
        if (token != expected) return null
        pending = null
        intent.removeExtra(EXTRA_TOKEN)
        return board
    }
}
