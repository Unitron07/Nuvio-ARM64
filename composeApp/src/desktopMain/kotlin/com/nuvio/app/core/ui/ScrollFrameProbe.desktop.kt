package com.nuvio.app.core.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal actual fun Modifier.nuvioScrollFrameProbe(): Modifier {
    val target = System.getenv("NUVIO_SCROLL_METRICS_FILE")?.takeIf { it.isNotBlank() } ?: return this
    return composed {
        LaunchedEffect(target) {
            val path = runCatching { Path.of(target).takeIf { it.isAbsolute } }.getOrNull() ?: return@LaunchedEffect
            var previous = withFrameNanos { it }
            var count = 0
            var total = 0L
            var maximum = 0L
            var over16 = 0
            var over33 = 0
            while (true) {
                val now = withFrameNanos { it }
                val elapsed = now - previous
                previous = now
                count++
                total += elapsed
                maximum = maxOf(maximum, elapsed)
                if (elapsed > 16_666_667) over16++
                if (elapsed > 33_333_334) over33++
                if (count == 300) {
                    val record = "{\"schema\":\"nuvio-frame-v1\",\"frames\":$count,\"totalNs\":$total,\"maxNs\":$maximum,\"over16ms\":$over16,\"over33ms\":$over33}\n"
                    withContext(Dispatchers.IO) {
                        runCatching { Files.writeString(path, record, StandardOpenOption.CREATE, StandardOpenOption.APPEND) }
                    }
                    count = 0; total = 0; maximum = 0; over16 = 0; over33 = 0
                    previous = withFrameNanos { it }
                }
            }
        }
        Modifier
    }
}
