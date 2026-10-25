package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.nuvio.app.isDesktop
import kotlin.math.abs

/**
 * AWT promotes Windows touchscreen input to mouse events. Compose's normal touch
 * scrolling therefore never starts. Match the shelf drag fallback on shared
 * screens, locking to the vertical axis so horizontal shelves keep their input.
 * Real touch events keep Compose's built-in scrolling and fling handling.
 */
internal fun Modifier.nuvioDesktopVerticalDragScroll(state: LazyListState): Modifier {
    if (!isDesktop) return this
    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) {
                return@awaitEachGesture
            }
            var dx = 0f
            var dy = 0f
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || !event.buttons.isPrimaryPressed) break
                if (!change.pressed) break
                val delta = change.position - change.previousPosition
                dx += delta.x
                dy += delta.y
                if (!dragging) {
                    if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) break
                    if (abs(dy) <= viewConfiguration.touchSlop || abs(dy) <= abs(dx)) continue
                    dragging = true
                }
                state.dispatchRawDelta(-delta.y)
                change.consume()
            }
        }
    }
}
