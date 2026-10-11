package com.dock.scanner.core

/**
 * Smooths frame-by-frame detections for the live camera overlay so the outline glides
 * instead of jittering, and tolerates a few missed frames before hiding it.
 */
class QuadSmoother(
    private val smoothing: Float = 0.45f,
    private val jumpThreshold: Float = 0.12f,
    private val maxMisses: Int = 4,
) {
    private var current: Quad? = null
    private var misses = 0

    /** Consecutive frames whose detection stayed close to the previous one. */
    var stableFrames: Int = 0
        private set

    fun update(detected: Quad?): Quad? {
        if (detected == null) {
            misses++
            stableFrames = 0
            if (misses > maxMisses) current = null
            return current
        }
        misses = 0
        val previous = current
        current = if (previous == null || previous.maxCornerDistance(detected) > jumpThreshold) {
            stableFrames = 0
            detected
        } else {
            if (previous.maxCornerDistance(detected) < STABLE_DISTANCE) stableFrames++ else stableFrames = 0
            previous.lerp(detected, smoothing)
        }
        return current
    }

    fun reset() {
        current = null
        misses = 0
        stableFrames = 0
    }

    private companion object {
        const val STABLE_DISTANCE = 0.015f
    }
}
