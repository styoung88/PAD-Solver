package com.example.phoneapp.capture

import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.media.ImageReader
import android.media.ImageWriter
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreshCaptureReaderTest {
    @Test
    fun replacementDiscardsQueuedOverlayFrameAndReceivesNewFrameWithUnrelatedTimestamp() {
        val previous = ImageReader.newInstance(8, 8, PixelFormat.RGBA_8888, 2)
        // Exercise the Android 8-compatible producer on newer phones too.
        writeCanvasFrame(previous, red = 0)
        val previousSurface = previous.surface
        val fresh = ScreenCaptureService.replaceCaptureReader(previous)
        assertFalse("Replacement must release the old reader's surface", previousSurface.isValid)
        try {
            assertEquals(8, fresh.width)
            assertEquals(8, fresh.height)
            writeFrame(fresh, timestamp = 456L, red = 42)
            val deadline = SystemClock.uptimeMillis() + 2000
            var frame = fresh.acquireLatestImage()
            while (frame == null && SystemClock.uptimeMillis() < deadline) {
                Thread.sleep(10)
                frame = fresh.acquireLatestImage()
            }
            assertNotNull("Fresh buffer should deliver a frame without a wall-clock timestamp comparison", frame)
            frame!!.use {
                if (Build.VERSION.SDK_INT >= 29) assertEquals(456L, it.timestamp)
                assertEquals(42.toByte(), it.planes[0].buffer.get(0))
            }
        } finally {
            fresh.close()
        }
    }

    private fun writeFrame(reader: ImageReader, timestamp: Long, red: Int) {
        if (Build.VERSION.SDK_INT >= 29) {
            // Older ImageWriter APIs infer PRIVATE for this surface, so their
            // buffers cannot be dequeued and filled by application code.
            ImageWriter.newInstance(reader.surface, 2, PixelFormat.RGBA_8888).use { writer ->
                writer.dequeueInputImage().also { image ->
                    image.timestamp = timestamp
                    image.planes[0].buffer.put(0, red.toByte())
                    writer.queueInputImage(image)
                }
            }
        } else {
            writeCanvasFrame(reader, red)
        }
    }

    private fun writeCanvasFrame(reader: ImageReader, red: Int) {
        val canvas = reader.surface.lockCanvas(null)
        try { canvas.drawColor(Color.rgb(red, 0, 0)) }
        finally { reader.surface.unlockCanvasAndPost(canvas) }
    }
}
