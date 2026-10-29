package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListState

/** Opt-in, aggregate-only desktop frame pacing probe; no content identifiers. */
internal expect fun Modifier.nuvioScrollFrameProbe(state: LazyListState): Modifier
