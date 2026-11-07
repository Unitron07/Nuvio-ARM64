package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosterMouseHoverTest {
    @get:Rule val compose = createComposeRule()
    private val hover = PosterMouseHoverState()
    private var clicks = 0

    private fun setContent(acceptEnter: Boolean = false) {
        compose.setContent {
            Box(Modifier.size(200.dp).posterMouseHover(hover, acceptEnter).testTag("poster").clickable { clicks++ })
        }
    }

    @Test fun entryAndRepeatedPromotedTapsNeverArmHover() {
        setContent()
        val poster = compose.onNodeWithTag("poster")
        poster.performMouseInput { enter(Offset(20f, 20f)) }
        compose.runOnIdle { assertFalse(hover.hovered) }
        repeat(3) { poster.performMouseInput { click(Offset(100f, 100f)) } }
        compose.runOnIdle { assertFalse(hover.hovered); assertEquals(3, clicks) }
    }

    @Test fun mouseHoverClearsOnPressAndRequiresFreshUnpressedMovement() {
        setContent()
        val poster = compose.onNodeWithTag("poster")
        poster.performMouseInput { enter(Offset(20f, 20f)); moveTo(Offset(100f, 20f)) }
        compose.runOnIdle { assertTrue(hover.hovered) }
        poster.performMouseInput { press(); moveTo(Offset(100f, 150f)); release() }
        compose.runOnIdle { assertFalse(hover.hovered); assertFalse(hover.pressed) }
        poster.performMouseInput { moveTo(Offset(30f, 150f)) }
        compose.runOnIdle { assertTrue(hover.hovered) }
        poster.performMouseInput { exit() }
        compose.runOnIdle { assertFalse(hover.hovered) }
    }

    @Test fun popupTransferEntryCannotRearmAfterDirectInput() {
        setContent(acceptEnter = true)
        val poster = compose.onNodeWithTag("poster")
        poster.performMouseInput { enter(Offset(20f, 20f)) }
        compose.runOnIdle { assertTrue(hover.hovered) }
        poster.performMouseInput { click(); exit(); enter(Offset(20f, 20f)) }
        compose.runOnIdle { assertFalse(hover.hovered) }
        poster.performMouseInput { moveTo(Offset(100f, 20f)) }
        compose.runOnIdle { assertTrue(hover.hovered) }
    }

    @Test fun realTouchClearsMouseHoverWithoutBlockingTap() {
        setContent()
        val poster = compose.onNodeWithTag("poster")
        poster.performMouseInput { enter(Offset(20f, 20f)); moveTo(Offset(100f, 20f)) }
        compose.runOnIdle { assertTrue(hover.hovered) }
        poster.performTouchInput { click() }
        compose.runOnIdle { assertFalse(hover.hovered); assertEquals(1, clicks) }
    }

    @Test fun wheelDismissesAndKeyboardActivationDoesNotArmHover() {
        setContent()
        val poster = compose.onNodeWithTag("poster")
        poster.performMouseInput { enter(Offset(20f, 20f)); moveTo(Offset(100f, 20f)); scroll(100f) }
        compose.runOnIdle { assertFalse(hover.hovered) }
        poster.performSemanticsAction(SemanticsActions.RequestFocus)
        poster.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, clicks); assertFalse(hover.hovered) }
    }

    @Test fun promotedVerticalDragScrollsWithoutHoverOrSelection() = checkScroll(Orientation.Vertical)
    @Test fun promotedHorizontalDragScrollsWithoutHoverOrSelection() = checkScroll(Orientation.Horizontal)
    @Test fun touchVerticalDragScrollsWithoutHoverOrSelection() = checkScroll(Orientation.Vertical, touch = true)
    @Test fun touchHorizontalDragScrollsWithoutHoverOrSelection() = checkScroll(Orientation.Horizontal, touch = true)
    @Test fun wheelStillScrollsListWithoutHoverOrSelection() = checkScroll(Orientation.Vertical, wheel = true)

    private fun checkScroll(axis: Orientation, touch: Boolean = false, wheel: Boolean = false) {
        val list = LazyListState()
        val states = List(30) { PosterMouseHoverState() }
        compose.setContent {
            val modifier = Modifier.size(300.dp).nuvioDesktopAxisDragScroll(list, axis).testTag("list")
            if (axis == Orientation.Vertical) {
                LazyColumn(state = list, modifier = modifier) {
                    items(30) { index ->
                        Box(Modifier.size(300.dp, 80.dp).posterMouseHover(states[index]).clickable { clicks++ })
                    }
                }
            } else {
                LazyRow(state = list, modifier = modifier) {
                    items(30) { index ->
                        Box(Modifier.size(80.dp, 300.dp).posterMouseHover(states[index]).clickable { clicks++ })
                    }
                }
            }
        }
        val end = if (axis == Orientation.Vertical) Offset(240f, 40f) else Offset(40f, 240f)
        if (wheel) {
            compose.onNodeWithTag("list").performMouseInput { enter(Offset(240f, 240f)); scroll(200f) }
        } else if (touch) {
            compose.onNodeWithTag("list").performTouchInput { swipe(Offset(240f, 240f), end, 300) }
        } else {
            compose.onNodeWithTag("list").performMouseInput {
                moveTo(Offset(240f, 240f)); press()
                moveTo(if (axis == Orientation.Vertical) Offset(240f, 160f) else Offset(160f, 240f), delayMillis = 20)
                moveTo(end, delayMillis = 20); release()
            }
        }
        compose.runOnIdle {
            assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
            assertEquals(0, clicks)
            assertTrue(states.none { it.hovered })
        }
    }
}
