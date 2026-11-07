package com.nuvio.app.features.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.PosterZoomOverlayCoordinator
import com.nuvio.app.core.ui.desktopPosterHoverScale
import com.nuvio.app.features.home.MetaPreview
import org.junit.After
import org.junit.Before
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class HomePosterHoverPreviewTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private var oldEnabled = true
    private var oldDelay = 2000
    private var oldTrailer = false

    @Before fun prepare() {
        PosterCardStyleRepository.ensureLoaded()
        val old = PosterCardStyleRepository.uiState.value
        oldEnabled = old.hoverPreviewEnabled
        oldDelay = old.hoverPreviewOpenDelayMillis
        oldTrailer = old.hoverPreviewTrailerEnabled
        PosterCardStyleRepository.setHoverPreviewEnabled(true)
        PosterCardStyleRepository.setHoverPreviewOpenDelayMillis(500)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(false)
        PosterZoomOverlayCoordinator.hide()
        compose.setContent {
            NuvioTheme {
                HomePosterHoverPreview(
                    item = MetaPreview("hover-fixture", "movie", "Hover fixture"),
                    isWatched = false,
                    onClick = { clicks++ },
                    onLongClick = null,
                ) { hover ->
                    Box(Modifier.size(200.dp).desktopPosterHoverScale().then(hover).testTag("poster").clickable { clicks++ })
                }
            }
        }
    }

    @After fun restore() {
        PosterCardStyleRepository.setHoverPreviewEnabled(oldEnabled)
        PosterCardStyleRepository.setHoverPreviewOpenDelayMillis(oldDelay)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(oldTrailer)
        PosterZoomOverlayCoordinator.hide()
    }

    private fun hoverWithMouse() {
        compose.onNodeWithTag("poster").performMouseInput {
            enter(Offset(20f, 20f)); moveTo(Offset(100f, 20f))
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
    }

    @Test fun touchAndPromotedTapsNeverOpenDelayedPreview() {
        val poster = compose.onNodeWithTag("poster")
        poster.performMouseInput { enter(Offset(20f, 20f)) }
        repeat(3) { poster.performMouseInput { click() } }
        poster.performMouseInput { exit() }
        repeat(3) { poster.performTouchInput { click() } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Hover fixture").assertDoesNotExist()
        compose.runOnIdle { assertEquals(6, clicks) }
    }

    @Test fun pressCancelsPendingPreviewAndDoesNotReopenOnRelease() {
        compose.mainClock.autoAdvance = false
        val poster = compose.onNodeWithTag("poster")
        poster.performMouseInput { enter(Offset(20f, 20f)); moveTo(Offset(100f, 20f)) }
        compose.mainClock.advanceTimeBy(200)
        poster.performMouseInput { press(); moveTo(Offset(100f, 150f)); release() }
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Hover fixture").assertDoesNotExist()
    }

    @Test fun mousePreviewAllowsTransferAndHeldClickOnDetails() {
        hoverWithMouse()
        val details = compose.onNodeWithText("View Details", substring = true)
        details.assertExists()
        compose.onNodeWithTag("poster").performMouseInput { exit() }
        details.performMouseInput { enter(center); press() }
        compose.mainClock.advanceTimeBy(600)
        details.assertExists()
        details.performMouseInput { release() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun scrollOnPreviewDismissesIt() {
        hoverWithMouse()
        compose.onNodeWithText("View Details", substring = true).performMouseInput { enter(center); scroll(100f) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Hover fixture").assertDoesNotExist()
    }

    @Test fun touchOnOpenPreviewDismissesIt() {
        hoverWithMouse()
        compose.onNodeWithText("View Details", substring = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Hover fixture").assertDoesNotExist()
    }
}
