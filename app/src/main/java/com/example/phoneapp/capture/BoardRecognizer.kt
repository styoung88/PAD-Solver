package com.example.phoneapp.capture

import android.graphics.Bitmap
import com.example.phoneapp.game.GameVariant

fun interface BoardRecognizer {
    fun recognize(bitmap: Bitmap): BoardRecognition

    companion object {
        fun forGame(game: GameVariant): BoardRecognizer = when (game) {
            GameVariant.PAD -> PadBoardRecognizer()
            GameVariant.PAD_ZERO -> PadZeroBoardRecognizer()
        }
    }
}
