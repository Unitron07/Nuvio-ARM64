package com.nuvio.app.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.IdentityHashMap

/** UI-thread registry of only currently composed shelves; never contains names. */
internal object ShelfScrollDiagnostics {
    private class Position(var index: Int, var offset: Int)
    private val shelves = IdentityHashMap<LazyListState, Position>()
    fun enter(state: LazyListState) {
        shelves[state] = Position(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        DesktopScrollDiagnostics.counters.shelfEntered()
    }
    fun leave(state: LazyListState) {
        shelves.remove(state)
        DesktopScrollDiagnostics.counters.shelfDisposed()
    }
    fun sampleHorizontal(): Boolean {
        var moving = false
        for ((state, previous) in shelves) {
            if (state.isScrollInProgress || state.firstVisibleItemIndex != previous.index ||
                state.firstVisibleItemScrollOffset != previous.offset) moving = true
            previous.index = state.firstVisibleItemIndex
            previous.offset = state.firstVisibleItemScrollOffset
        }
        return moving
    }
}

@Composable internal actual fun ObserveShelfScroll(state: LazyListState) {
    if (!DesktopScrollDiagnostics.enabled || !LocalScreenActive.current) return
    DisposableEffect(state) {
        ShelfScrollDiagnostics.enter(state)
        onDispose { ShelfScrollDiagnostics.leave(state) }
    }
}
