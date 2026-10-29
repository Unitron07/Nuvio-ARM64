package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable

/** Opt-in, aggregate-only desktop frame pacing probe; no content identifiers. */
internal expect fun Modifier.nuvioScrollFrameProbe(state: LazyListState): Modifier
@Composable internal expect fun ObserveShelfScroll(state: LazyListState)
