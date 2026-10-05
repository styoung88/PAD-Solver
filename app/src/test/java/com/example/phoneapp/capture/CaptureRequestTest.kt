package com.example.phoneapp.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureRequestTest {
    @Test
    fun repeatedRequestIsIgnoredAndOnlyOneFreshFrameIsAccepted() {
        val capture = CaptureRequest()
        val request = capture.begin()!!
        assertNull(capture.begin())
        assertFalse(capture.takeFrame())
        assertTrue(capture.markReady(request))
        assertTrue(capture.takeFrame())
        assertFalse(capture.takeFrame())
        assertFalse(capture.fail(request))
        assertNotNull(capture.begin())
    }

    @Test
    fun timeoutReportsOnceAndAllowsRetryButRejectsLateFrames() {
        val capture = CaptureRequest()
        val expired = capture.begin()!!
        assertTrue(capture.fail(expired))
        assertFalse(capture.fail(expired))
        assertFalse(capture.markReady(expired))
        assertFalse(capture.takeFrame())
        val retry = capture.begin()!!
        assertFalse(capture.fail(expired))
        assertTrue(capture.isCurrent(retry))
        assertTrue(capture.markReady(retry))
        assertTrue(capture.takeFrame())
    }

    @Test
    fun oldTimeoutCannotExpireNewRequestAfterSuccessfulCapture() {
        val capture = CaptureRequest()
        val previous = capture.begin()!!
        capture.markReady(previous)
        capture.takeFrame()
        val current = capture.begin()!!
        assertFalse(capture.isCurrent(previous))
        assertFalse(capture.markReady(previous))
        assertFalse(capture.fail(previous))
        assertTrue(capture.markReady(current))
        assertTrue(capture.takeFrame())
    }

    @Test
    fun stoppingSessionInvalidatesCallbacksAndAllowsNextSession() {
        val capture = CaptureRequest()
        val request = capture.begin()!!
        capture.markReady(request)
        capture.cancel()
        assertFalse(capture.isCurrent(request))
        assertFalse(capture.takeFrame())
        assertFalse(capture.fail(request))
        val nextSession = capture.begin()!!
        assertNotEquals(request, nextSession)
        assertTrue(capture.markReady(nextSession))
        assertTrue(capture.takeFrame())
    }

    @Test
    fun concurrentFramesCannotBothCompleteOneRequest() {
        val capture = CaptureRequest()
        capture.markReady(capture.begin()!!)
        val start = java.util.concurrent.CountDownLatch(1)
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val threads = List(8) { Thread {
            start.await()
            if (capture.takeFrame()) accepted.incrementAndGet()
        }.apply { start() } }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, accepted.get())
    }
}
