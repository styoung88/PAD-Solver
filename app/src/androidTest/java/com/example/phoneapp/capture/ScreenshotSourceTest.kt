package com.example.phoneapp.capture

import android.database.MatrixCursor
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScreenshotSourceTest {
    @Test fun unrelatedNewPhotoDoesNotHideANewScreenshotAndExistingImagesAreNotReplayed() {
        var request = 0
        val source = ScreenshotSource({ columns, selection, args ->
            MatrixCursor(columns).apply {
                when (request++) {
                    0 -> { assertNull(selection); addRow(arrayOf(100L)) }
                    1 -> {
                        assertEquals("100", args!!.single())
                        assertTrue(selection!!.contains("is_pending = 0"))
                        addRow(arrayOf(103L, "Camera.jpg", "DCIM/Camera/"))
                        addRow(arrayOf(102L, "Screenshot_PAD.png", "Pictures/Screenshots/"))
                    }
                    else -> assertEquals("102", args!!.single())
                }
            }
        }, apiLevel = 34)
        source.startSession()
        assertEquals("content://media/external/images/media/102", source.next().toString())
        assertNull(source.next())
    }

    @Test fun screenshotFolderSupportsDifferentFileNamesAndOldAndroidNeedsNoFolderColumn() {
        var started = false
        val source = ScreenshotSource({ columns, _, _ ->
            MatrixCursor(columns).apply {
                if (!started) { addRow(arrayOf(5L)); started = true }
                else addRow(arrayOf(6L, "screen_001.png", "Pictures/Screenshots/"))
            }
        }, apiLevel = 34)
        source.startSession()
        assertEquals("content://media/external/images/media/6", source.next().toString())

        val legacy = ScreenshotSource({ columns, selection, _ ->
            MatrixCursor(columns).apply {
                if (selection == null) addRow(arrayOf(5L))
                else {
                    assertFalse(selection.contains("is_pending"))
                    assertFalse(columns.contains("relative_path"))
                    addRow(arrayOf(6L, "Screenshot_PAD.png"))
                }
            }
        }, apiLevel = 26)
        legacy.startSession()
        assertEquals("content://media/external/images/media/6", legacy.next().toString())
    }

    @Test fun savedImageIsReadAndRecognizedWithoutStartingScreenCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = File(instrumentation.targetContext.cacheDir, "saved-board-loader-test.png")
        instrumentation.context.assets.open("pad-board-reference.png").use { input ->
            image.outputStream().use { input.copyTo(it) }
        }
        try {
            assertFalse(ScreenCaptureService.active)
            val bitmap = checkNotNull(BoardImageLoader.read(instrumentation.targetContext, Uri.fromFile(image)))
            try {
                val result = PadBoardRecognizer().recognize(bitmap)
                assertTrue(result is BoardRecognition.Recognized)
                assertEquals(30, (result as BoardRecognition.Recognized).board.orbs.size)
            } finally { bitmap.recycle() }
            assertFalse(ScreenCaptureService.active)
        } finally { image.delete() }
    }
}
