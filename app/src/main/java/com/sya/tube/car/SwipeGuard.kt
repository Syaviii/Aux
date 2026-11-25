package com.sya.tube.car

import android.view.MotionEvent

/**
 * The car reports a finger that slides off the edge of Aux's area as a sudden jump to the far side of the screen, which a
 * page reads as a hard swipe the other way and scrolls back up. A swipe that jumps further than a finger can move between
 * two touch reports, or leaves the area, is ended at its last real point instead
 */
class SwipeGuard {
    private var lastX = 0f
    private var lastY = 0f
    private var ended = false

    /**
     * Sends the event on, or the swipe's end in its place. Returns false when the event is swallowed because the swipe
     * already ended
     */
    fun filter(event: MotionEvent, width: Int, height: Int, send: (MotionEvent) -> Boolean): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { ended = false; lastX = event.x; lastY = event.y; return send(event) }
            MotionEvent.ACTION_MOVE -> {
                if (ended) return true
                if (event.pointerCount == 1 && jumped(event, width, height)) {
                    ended = true
                    val up = MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_UP, lastX, lastY, 0)
                    up.source = event.source
                    send(up); up.recycle()
                    return true
                }
                lastX = event.x; lastY = event.y
                return send(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (ended) { ended = false; return true }
                // The jump can come on the lift itself, the page would read it as a hard swipe back and fling the
                // other way, the swipe ends where the finger last really was instead
                if (event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount == 1 && jumped(event, width, height)) {
                    val up = MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_UP, lastX, lastY, 0)
                    up.source = event.source
                    val handled = send(up); up.recycle()
                    return handled
                }
                return send(event)
            }
            else -> return if (ended) true else send(event)
        }
    }

    private fun jumped(event: MotionEvent, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val outside = event.x < -1 || event.y < -1 || event.x > width + 1 || event.y > height + 1
        // A real finger moves a few dozen pixels between reports, half the screen at once is the edge glitch
        val far = Math.abs(event.y - lastY) > height * 0.45f || Math.abs(event.x - lastX) > width * 0.45f
        return outside || far
    }
}
