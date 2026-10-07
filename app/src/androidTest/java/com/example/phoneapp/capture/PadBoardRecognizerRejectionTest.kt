package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class PadBoardRecognizerRejectionTest {
    @Test
    fun croppedBoardWithMissingRowsRequiresAnotherCapture() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val image = checkNotNull(context.assets.open("pad-cropped-board-reference.png").use(BitmapFactory::decodeStream))
        try {
            assertTrue(PadBoardRecognizer().recognize(image) is BoardRecognition.Uncertain)
        } finally { image.recycle() }
    }

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
