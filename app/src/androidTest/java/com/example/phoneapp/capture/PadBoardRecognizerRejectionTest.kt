package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class PadBoardRecognizerRejectionTest {
    @Test
    fun blankAndNonBoardImagesDoNotProduceUsableBoards() {
        for (color in listOf(Color.BLACK, Color.WHITE, Color.GRAY)) {
            val image = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(color)
                assertTrue("A blank capture must require retry", PadBoardRecognizer().recognize(image) is BoardRecognition.Uncertain)
            } finally {
                image.recycle()
            }
        }
    }
}
