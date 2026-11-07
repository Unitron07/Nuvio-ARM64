package com.nuvio.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

internal class PosterMouseHoverState {
    var hovered by mutableStateOf(false)
    var pressed by mutableStateOf(false)
    var coordinates: LayoutCoordinates? = null
}

/**
 * Desktop touch can arrive as Mouse. Entry alone is therefore insufficient: require
 * unpressed movement beyond touch slop. Never consume events used by clicks/scrolling.
 * A mounted preview may accept entry so the mouse can move from its poster into it.
 */
internal fun Modifier.posterMouseHover(
    state: PosterMouseHoverState,
    acceptEnter: Boolean = false,
    onDirectInput: (PointerEvent) -> Unit = {},
): Modifier = onGloballyPositioned { state.coordinates = it }.pointerInput(state, acceptEnter) {
    var origin: Offset? = null
    var enterArmed = acceptEnter
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull()
                val position = change?.let {
                    state.coordinates?.takeIf { coordinates -> coordinates.isAttached }
                        ?.localToRoot(it.position) ?: it.position
                }
                state.pressed = event.changes.any { it.pressed }
                when {
                    event.type == PointerEventType.Scroll ||
                        event.type == PointerEventType.Press ||
                        event.type == PointerEventType.Release ||
                        event.changes.any { it.type != PointerType.Mouse || it.pressed } -> {
                        origin = position
                        enterArmed = false
                        state.hovered = false
                        onDirectInput(event)
                    }
                    event.type == PointerEventType.Exit -> {
                        origin = null
                        state.hovered = false
                    }
                    event.type == PointerEventType.Enter -> {
                        origin = position
                        state.hovered = enterArmed && change?.type == PointerType.Mouse
                    }
                    event.type == PointerEventType.Move && change?.type == PointerType.Mouse && position != null -> {
                        // Hover events may not retain previousPosition. Track our
                        // own root position so shelf relayout cannot count as motion.
                        val start = origin ?: position.also { origin = it }
                        if ((position - start).getDistance() > viewConfiguration.touchSlop) {
                            state.hovered = true
                            enterArmed = acceptEnter
                        }
                    }
                }
            }
        }
    } finally {
        state.hovered = false
        state.pressed = false
        state.coordinates = null
    }
}
