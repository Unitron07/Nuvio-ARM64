package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopVerticalDragScrollTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vertical: LazyListState
    private lateinit var horizontal: LazyListState
    private var clicks = 0

    private fun setContent() {
        compose.setContent {
            vertical = rememberLazyListState()
            horizontal = remember { LazyListState() }
            LazyColumn(
                state = vertical,
                modifier = Modifier.size(300.dp).nuvioDesktopVerticalDragScroll(vertical).testTag("page"),
            ) {
                item {
                    LazyRow(
                        state = horizontal,
                        modifier = Modifier.nuvioDesktopDragScroll(horizontal).testTag("shelf"),
                    ) {
                        items(30) { index ->
                            Box(Modifier.size(100.dp).testTag("card-$index").clickable { clicks++ })
                        }
                    }
                }
                items(30) { Box(Modifier.size(300.dp, 80.dp).clickable { clicks++ }) }
            }
        }
    }

    @Test fun promotedTouchDragScrollsPageWithoutClicking() {
        setContent()
        compose.onNodeWithTag("page").performMouseInput {
            moveTo(Offset(150f, 240f)); press()
            moveTo(Offset(150f, 180f), delayMillis = 20)
            moveTo(Offset(150f, 60f), delayMillis = 20); release()
        }
        compose.runOnIdle {
            assertTrue(vertical.firstVisibleItemIndex > 0 || vertical.firstVisibleItemScrollOffset > 0)
            assertEquals(0, clicks)
        }
    }

    @Test fun tapStillActivatesCard() {
        setContent()
        compose.onNodeWithTag("card-0").performMouseInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun releaseContinuesScrollingWithFrameworkFling() {
        setContent()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("page").performMouseInput {
            moveTo(Offset(150f, 250f)); press()
            moveTo(Offset(150f, 210f), delayMillis = 20)
            moveTo(Offset(150f, 150f), delayMillis = 20)
            moveTo(Offset(150f, 80f), delayMillis = 20); release()
        }
        var before = 0
        compose.runOnIdle { before = vertical.firstVisibleItemIndex * 1000 + vertical.firstVisibleItemScrollOffset }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue(vertical.firstVisibleItemIndex * 1000 + vertical.firstVisibleItemScrollOffset > before)
            assertEquals(0, clicks)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun horizontalShelfDragDoesNotMovePage() {
        setContent()
        compose.onNodeWithTag("shelf").performMouseInput {
            moveTo(Offset(240f, 40f)); press()
            moveTo(Offset(180f, 40f), delayMillis = 20)
            moveTo(Offset(50f, 40f), delayMillis = 20); release()
        }
        compose.runOnIdle {
            assertEquals(0, vertical.firstVisibleItemIndex)
            assertEquals(0, vertical.firstVisibleItemScrollOffset)
            assertTrue(horizontal.firstVisibleItemIndex > 0 || horizontal.firstVisibleItemScrollOffset > 0)
            assertEquals(0, clicks)
        }
    }

    @Test fun shortFlickContinuesAndNewPressStopsMomentum() {
        setContent()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("page").performMouseInput {
            moveTo(Offset(150f, 240f)); press()
            moveTo(Offset(150f, 230f), delayMillis = 10)
            moveTo(Offset(150f, 215f), delayMillis = 10)
            moveTo(Offset(150f, 195f), delayMillis = 10); release()
        }
        var before = 0
        fun position() = vertical.firstVisibleItemIndex * 1000 + vertical.firstVisibleItemScrollOffset
        compose.runOnIdle { before = position() }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { assertTrue(position() > before); assertEquals(0, clicks) }
        compose.onNodeWithTag("page").performMouseInput { moveTo(Offset(150f, 240f)); press() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { before = position() }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals(before, position()) }
        compose.onNodeWithTag("page").performMouseInput { release() }
        compose.mainClock.autoAdvance = true
    }

    @Test fun secondaryButtonDragDoesNotScrollPage() {
        setContent()
        compose.onNodeWithTag("page").performMouseInput {
            moveTo(Offset(150f, 240f)); press(MouseButton.Secondary)
            moveTo(Offset(150f, 60f), delayMillis = 20); release(MouseButton.Secondary)
        }
        compose.runOnIdle {
            assertEquals(0, vertical.firstVisibleItemIndex)
            assertEquals(0, vertical.firstVisibleItemScrollOffset)
        }
    }
}
