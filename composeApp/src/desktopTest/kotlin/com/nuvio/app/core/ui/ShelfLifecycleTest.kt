package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShelfLifecycleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun horizontalPositionSurvivesOuterDisposalAndKeyedRefresh() {
        val entries = mutableStateOf((0..49).toList())
        compose.setContent {
            LazyColumn(Modifier.size(360.dp).testTag("outer")) {
                item(key = "catalog", contentType = "catalog") {
                    NuvioShelfSection(
                        title = "Synthetic shelf", entries = entries.value,
                        rowModifier = Modifier.testTag("shelf"), key = { it },
                    ) { item -> Box(Modifier.size(100.dp).testTag("card-$item")) }
                }
                items(50) { Box(Modifier.size(360.dp, 200.dp)) }
            }
        }
        fun position() = compose.onNodeWithTag("shelf").fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()
        compose.onNodeWithTag("shelf").performScrollToIndex(10)
        val before = position()
        assertTrue(before > 0)
        compose.onNodeWithTag("outer").performScrollToIndex(40)
        compose.onNodeWithTag("shelf").assertDoesNotExist()
        compose.onNodeWithTag("outer").performScrollToIndex(0)
        assertEquals(before, position(), 0.001f)
        compose.onNodeWithTag("card-10").assertIsDisplayed()
        compose.runOnIdle { entries.value = listOf(-1) + entries.value }
        compose.onNodeWithTag("card-10").assertIsDisplayed()
    }
}
