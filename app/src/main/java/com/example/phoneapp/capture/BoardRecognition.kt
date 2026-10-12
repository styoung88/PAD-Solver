package com.example.phoneapp.capture

import android.graphics.RectF
import com.example.phoneapp.board.Board
import com.example.phoneapp.game.GameVariant

sealed interface BoardRecognition {
    data class Recognized(
        val board: Board,
        /** Bounds are in screenshot pixels and describe the outside of the grid. */
        val boardBounds: RectF,
        val captureWidth: Int,
        val captureHeight: Int,
    ) : BoardRecognition

    data class Uncertain(val recognizedCells: Int) : BoardRecognition
    data class Unsupported(val game: GameVariant) : BoardRecognition
}
