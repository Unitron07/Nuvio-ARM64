package com.nuvio.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nuvio.app.features.settings.NavBarStyle

internal fun sidebarShouldExpand(style: NavBarStyle, routeActive: Boolean, hoverIntent: Boolean,
    explicit: Boolean, profileOpen: Boolean): Boolean = routeActive && when (style) {
    NavBarStyle.EXPANDED -> true
    NavBarStyle.COMPACT -> explicit || profileOpen
    else -> explicit || hoverIntent || profileOpen
}

internal class SidebarHoverIntent {
    var expanded by mutableStateOf(false)
}

/** Enter/focus/click alone is not expansion intent, including promoted touch. */
internal fun Modifier.deliberateSidebarHover(enabled: Boolean, intent: SidebarHoverIntent): Modifier =
    pointerInput(enabled, intent) {
        intent.expanded = false
        if (!enabled) return@pointerInput
        coroutineScope {
            var pending: Job? = null
            var anchor: Offset? = null
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull()
                        when {
                            event.type == PointerEventType.Exit -> {
                                pending?.cancel(); intent.expanded = false; anchor = null
                            }
                            event.type == PointerEventType.Enter -> {
                                pending?.cancel(); anchor = change?.position
                            }
                            event.changes.any { it.pressed } -> {
                                pending?.cancel(); anchor = change?.position
                            }
                            event.type == PointerEventType.Move && change?.type == PointerType.Mouse -> {
                                val origin = anchor ?: change.previousPosition.also { anchor = it }
                                if (!intent.expanded && (change.position - origin).getDistance() > viewConfiguration.touchSlop && pending?.isActive != true) {
                                    pending = launch { delay(250); intent.expanded = true }
                                }
                            }
                        }
                    }
                }
            } finally {
                pending?.cancel(); intent.expanded = false
            }
        }
    }
