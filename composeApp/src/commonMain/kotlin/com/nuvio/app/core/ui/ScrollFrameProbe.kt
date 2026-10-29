package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

/** Opt-in, aggregate-only desktop frame pacing probe; no content identifiers. */
internal expect fun Modifier.nuvioScrollFrameProbe(): Modifier
