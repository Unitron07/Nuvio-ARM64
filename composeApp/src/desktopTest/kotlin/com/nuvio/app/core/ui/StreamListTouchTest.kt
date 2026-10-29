package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import com.nuvio.app.features.streams.*
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamListTouchTest {
    @get:Rule val compose = createComposeRule()
    @Test fun resultCardsDragWithoutSelectionAndTapStillSelects() {
        var selected = 0
        compose.setContent {
            StreamList(
                uiState = StreamsUiState(groups = listOf(AddonStreamGroup("Fixture", "fixture", (0..79).map {
                    StreamItem(name = "Fixture source $it", title = "Local source metadata $it", url = "http://127.0.0.1/video/$it", addonName = "Fixture", addonId = "fixture")
                }))),
                debridEnabled = false, appendInstantServiceToDefaultName = false,
                onStreamSelected = { _, _, _ -> selected++ }, onStreamLongPress = {},
                onStreamSecondaryClick = { _, _ -> }, resumePositionMs = null, resumeProgressFraction = null,
                modifier = Modifier.size(600.dp, 500.dp),
            )
        }
        compose.onNodeWithTag("stream-results-list").performMouseInput {
            moveTo(Offset(200f, 400f)); press()
            moveTo(Offset(200f, 300f), delayMillis = 20)
            moveTo(Offset(200f, 100f), delayMillis = 20); release()
        }
        compose.runOnIdle { assertEquals(0, selected) }
        assertTrue(compose.onNodeWithTag("stream-results-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.onNodeWithTag("stream-results-list").performScrollToIndex(0)
        compose.onNodeWithText("Fixture source 0", substring = true).performMouseInput { click() }
        compose.runOnIdle { assertEquals(1, selected) }
    }
}
