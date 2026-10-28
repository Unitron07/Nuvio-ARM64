package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.nuvio.app.isDesktop
import kotlin.math.abs

/**
 * AWT promotes Windows touchscreen input to mouse events. Compose's normal touch
 * scrolling may not start. Share axis locking, velocity tracking and framework
 * fling decay across opted-in lists; never reinterpret real touch as mouse.
 * Real touch events keep Compose's built-in scrolling and fling handling.
 */
internal fun Modifier.nuvioDesktopVerticalDragScroll(state: LazyListState): Modifier {
    return nuvioDesktopAxisDragScroll(state, Orientation.Vertical)
}

internal fun Modifier.nuvioDesktopAxisDragScroll(state: ScrollableState, axis: Orientation): Modifier {
    if (!isDesktop) return this
    return composed {
      val fling = ScrollableDefaults.flingBehavior()
      val dispatcher = remember { NestedScrollDispatcher() }
      val connection = remember { object : NestedScrollConnection {} }
      fun vector(value: Float) = if (axis == Orientation.Vertical) Offset(0f, value) else Offset(value, 0f)
      fun component(value: Offset) = if (axis == Orientation.Vertical) value.y else value.x
      Modifier.nestedScroll(connection, dispatcher).pointerInput(state, axis, fling) {
       coroutineScope {
        var momentum: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) {
                return@awaitEachGesture
            }
            momentum?.cancel()
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var released = false
            var dx = 0f
            var dy = 0f
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                velocity.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) { released = true; break }
                if (!event.buttons.isPrimaryPressed) break
                val delta = change.position - change.previousPosition
                dx += delta.x
                dy += delta.y
                val along = if (axis == Orientation.Vertical) dy else dx
                val across = if (axis == Orientation.Vertical) dx else dy
                if (!dragging) {
                    if (abs(across) > viewConfiguration.touchSlop && abs(across) > abs(along)) break
                    if (abs(along) <= viewConfiguration.touchSlop || abs(along) <= abs(across)) continue
                    dragging = true
                }
                val available = vector(if (axis == Orientation.Vertical) delta.y else delta.x)
                val remaining = available - dispatcher.dispatchPreScroll(available, NestedScrollSource.UserInput)
                val consumed = vector(-state.dispatchRawDelta(-component(remaining)))
                dispatcher.dispatchPostScroll(consumed, remaining - consumed, NestedScrollSource.UserInput)
                change.consume()
            }
            if (dragging && released) {
                val speed = velocity.calculateVelocity()
                momentum = launch {
                    val initial = if (axis == Orientation.Vertical) Velocity(0f, speed.y) else Velocity(speed.x, 0f)
                    val available = initial - dispatcher.dispatchPreFling(initial)
                    var left = 0f
                    state.scroll {
                        left = -with(fling) { performFling(-(if (axis == Orientation.Vertical) available.y else available.x)) }
                    }
                    val remaining = if (axis == Orientation.Vertical) Velocity(0f, left) else Velocity(left, 0f)
                    dispatcher.dispatchPostFling(available - remaining, remaining)
                }
            }
        }
       }
      }
    }
}
