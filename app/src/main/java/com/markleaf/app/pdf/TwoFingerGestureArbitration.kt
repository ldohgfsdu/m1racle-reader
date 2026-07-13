package com.markleaf.app.pdf

/**
 * Ownership state for the stable continuous-reading viewport detector.
 * It deliberately knows nothing about zoom math or ZoomSession lifecycle.
 */
enum class TwoFingerGesturePhase {
    IDLE,
    POSSIBLE_SINGLE_POINTER,
    TWO_FINGER_CAPTURED,
    ZOOM_SESSION_ACTIVE,
}

object TwoFingerGestureArbitration {
    /** Pointer count, not distance/slop/timing, is the capture decision. */
    fun shouldCapture(activePointerCount: Int): Boolean = activePointerCount >= 2

    /** LazyColumn remains enabled only while neither capture nor a session owns input. */
    fun listScrollEnabled(twoFingerCaptured: Boolean, sessionActive: Boolean): Boolean =
        !twoFingerCaptured && !sessionActive

    /** One finger remains native LazyColumn input until a second finger arrives. */
    fun onSinglePointer(phase: TwoFingerGesturePhase): TwoFingerGesturePhase = when (phase) {
        TwoFingerGesturePhase.IDLE -> TwoFingerGesturePhase.POSSIBLE_SINGLE_POINTER
        else -> phase
    }

    /** The second finger takes ownership immediately; no transform threshold is involved. */
    fun onSecondPointer(): TwoFingerGesturePhase = TwoFingerGesturePhase.TWO_FINGER_CAPTURED

    /** A captured gesture consumes changes even when hit testing/session creation fails. */
    fun consumesChanges(phase: TwoFingerGesturePhase): Boolean =
        phase == TwoFingerGesturePhase.TWO_FINGER_CAPTURED ||
            phase == TwoFingerGesturePhase.ZOOM_SESSION_ACTIVE

    /** First bounds snapshot plus one retry during one captured gesture. */
    fun canRetryHit(attempts: Int): Boolean = attempts < 2
}
