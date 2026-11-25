package com.sya.tube.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent

/**
 * The car sends a swipe's touch points in bursts, sometimes a whole swipe at once, each point stamped with when the
 * finger was there. Delivered as they arrive, a page sees one jump instead of a moving finger and can miss the swipe
 * (Twitch's rows) or fling the wrong way. The points are replayed here at the pace the finger moved, a burst costs the
 * delay it arrived with and no more
 */
class TouchPacer(private val send: (MotionEvent) -> Boolean) {
    private val main = Handler(Looper.getMainLooper())
    // Car clock to this phone's clock, moved forward whenever points arrive later than that
    private var offset = 0L
    private var downTime = 0L
    private var lastAt = 0L

    fun dispatch(event: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            offset = now - event.eventTime
            downTime = event.downTime + offset
            lastAt = 0L
        }
        var at = event.eventTime + offset
        // Late points shift the replay so none is sent in the past, the ones after it keep their spacing
        if (at < now) { offset += now - at; at = now }
        if (at < lastAt) at = lastAt
        lastAt = at
        val copy = restamp(event, at)
        if (at <= now) { val handled = send(copy); copy.recycle(); return handled }
        main.postAtTime({ send(copy); copy.recycle() }, at)
        return true
    }

    private fun restamp(event: MotionEvent, at: Long): MotionEvent {
        val count = event.pointerCount
        val props = Array(count) { MotionEvent.PointerProperties().also { p -> event.getPointerProperties(it, p) } }
        val coords = Array(count) { MotionEvent.PointerCoords().also { c -> event.getPointerCoords(it, c) } }
        val copy = MotionEvent.obtain(downTime, at, event.action, count, props, coords, event.metaState, event.buttonState,
            event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags)
        return copy
    }
}
