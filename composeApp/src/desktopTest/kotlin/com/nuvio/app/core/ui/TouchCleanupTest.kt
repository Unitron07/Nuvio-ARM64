package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.settings.NavBarStyle
import org.junit.Rule
import kotlin.test.*

class TouchCleanupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedGridDragsWithoutClickAndTapStillWorks() {
        val grid = LazyGridState()
        var clicks = 0
        compose.setContent {
            LazyVerticalGrid(columns = GridCells.Fixed(3), state = grid,
                modifier = Modifier.size(300.dp).nuvioDesktopAxisDragScroll(grid, Orientation.Vertical).testTag("grid")) {
                items(200) { index -> Box(Modifier.size(100.dp).testTag("tile-$index").clickable { clicks++ }) }
            }
        }
        compose.onNodeWithTag("grid").performMouseInput {
            moveTo(Offset(150f,250f)); press()
            moveTo(Offset(150f,180f), delayMillis=20)
            moveTo(Offset(150f,50f), delayMillis=20); release()
        }
        compose.runOnIdle { assertTrue(grid.firstVisibleItemIndex > 0); assertEquals(0,clicks) }
        compose.onNodeWithTag("grid").performScrollToIndex(0)
        compose.onNodeWithTag("tile-0").performMouseInput { click() }
        compose.runOnIdle { assertEquals(1,clicks) }
    }

    @Test fun genrePopupDragsWithoutSelectingThenSelects() {
        var selected: String? = null
        compose.setContent {
            Box(Modifier.size(600.dp).testTag("host")) {
                NuvioDropdownChip(title="Genre", label="Choose genre", selectedKey=null,
                    options=(0..99).map { NuvioDropdownOption("$it","Genre $it") }, onSelected={selected=it.key})
            }
        }
        compose.onNodeWithText("Choose genre").performClick()
        compose.onNode(hasScrollAction()).performMouseInput {
            moveTo(Offset(60f,200f)); press()
            moveTo(Offset(60f,140f),delayMillis=20)
            moveTo(Offset(60f,40f),delayMillis=20); release()
        }
        compose.runOnIdle { assertNull(selected) }
        assertTrue(compose.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.onNodeWithText("Genre 70").performScrollTo().performMouseInput { click() }
        compose.runOnIdle { assertEquals("70",selected) }
        compose.onNodeWithText("Genre 70").assertDoesNotExist()
        compose.onNodeWithText("Choose genre").performClick()
        // Content-level keyboard activation. Native popup-layer Escape is an owner check.
        compose.onNodeWithText("Genre 71").performScrollTo()
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Genre 71").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("71", selected) }
        compose.onNodeWithText("Genre 71").assertDoesNotExist()
    }

    @Test fun sidebarEnterClickAndRouteReturnDoNotExpandButMouseIntentDoes() {
        val intent = SidebarHoverIntent()
        val active = mutableStateOf(true)
        compose.setContent { Box(Modifier.size(120.dp,300.dp).deliberateSidebarHover(active.value,intent).testTag("rail")) }
        compose.onNodeWithTag("rail").performMouseInput { enter(Offset(20f,50f)); click(Offset(20f,50f)) }
        compose.runOnIdle { assertFalse(intent.expanded) }
        compose.onNodeWithTag("rail").performMouseInput {
            press(); moveTo(Offset(90f,50f),delayMillis=20); release()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertFalse(intent.expanded) }
        compose.onNodeWithTag("rail").performMouseInput { moveTo(Offset(20f,50f),delayMillis=20) }
        compose.waitUntil(2_000) { intent.expanded }
        compose.runOnIdle { active.value=false }
        compose.runOnIdle { assertFalse(intent.expanded); active.value=true }
        compose.runOnIdle { assertFalse(intent.expanded) }
    }

    @Test fun expansionPolicyPreservesExplicitKeyboardTouchAndStyleChoices() {
        assertFalse(sidebarShouldExpand(NavBarStyle.ADAPTIVE,true,false,false,false))
        assertTrue(sidebarShouldExpand(NavBarStyle.ADAPTIVE,true,true,false,false))
        assertTrue(sidebarShouldExpand(NavBarStyle.ADAPTIVE,true,false,true,false))
        assertFalse(sidebarShouldExpand(NavBarStyle.ADAPTIVE,false,true,true,true))
        assertFalse(sidebarShouldExpand(NavBarStyle.COMPACT,true,true,false,false))
        assertTrue(sidebarShouldExpand(NavBarStyle.COMPACT,true,false,false,true))
        assertTrue(sidebarShouldExpand(NavBarStyle.EXPANDED,true,false,false,false))
    }
}
