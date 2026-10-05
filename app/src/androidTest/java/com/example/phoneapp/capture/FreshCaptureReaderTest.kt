package com.example.phoneapp.capture

import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.ImageWriter
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreshCaptureReaderTest {
    @Test
    fun replacementDiscardsQueuedOverlayFrameAndReceivesNewFrameWithUnrelatedTimestamp() {
        val previous = ImageReader.newInstance(8, 8, PixelFormat.RGBA_8888, 2)
        val oldWriter = ImageWriter.newInstance(previous.surface, 2)
        oldWriter.dequeueInputImage().also { image ->
            image.timestamp = 123L
            oldWriter.queueInputImage(image)
        }
        val fresh = ScreenCaptureService.replaceCaptureReader(previous)
        oldWriter.close()
        val writer = ImageWriter.newInstance(fresh.surface, 2)
        try {
            assertEquals(8, fresh.width)
            assertEquals(8, fresh.height)
            writer.dequeueInputImage().also { image ->
                image.timestamp = 456L
                image.planes[0].buffer.put(0, 42.toByte())
                writer.queueInputImage(image)
            }
            val deadline = SystemClock.uptimeMillis() + 2000
            var frame = fresh.acquireLatestImage()
            while (frame == null && SystemClock.uptimeMillis() < deadline) {
                Thread.sleep(10)
                frame = fresh.acquireLatestImage()
            }
            assertNotNull("Fresh buffer should deliver a frame without a wall-clock timestamp comparison", frame)
            frame!!.use {
                assertEquals(456L, it.timestamp)
                assertEquals(42.toByte(), it.planes[0].buffer.get(0))
            }
        } finally {
            writer.close()
            fresh.close()
        }
    }
}
