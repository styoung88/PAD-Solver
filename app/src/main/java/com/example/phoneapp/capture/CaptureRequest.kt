package com.example.phoneapp.capture

/** Coordinates capture callbacks from the main and image-reader threads. */
internal class CaptureRequest {
    private var sequence = 0
    private var pending = false
    private var ready = false

    @Synchronized
    fun begin(): Int? {
        if (pending) return null
        pending = true
        ready = false
        return ++sequence
    }

    @Synchronized
    fun isCurrent(request: Int): Boolean = pending && request == sequence

    @Synchronized
    fun markReady(request: Int): Boolean {
        if (!isCurrent(request)) return false
        ready = true
        return true
    }

    @Synchronized
    fun takeFrame(): Boolean {
        if (!pending || !ready) return false
        pending = false
        ready = false
        return true
    }

    /** Returns true only for the first failure/timeout of the current request. */
    @Synchronized
    fun fail(request: Int): Boolean {
        if (!isCurrent(request)) return false
        pending = false
        ready = false
        return true
    }

    @Synchronized
    fun cancel() {
        sequence++
        pending = false
        ready = false
    }
}
