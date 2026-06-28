package com.tneff.kmpremotecompose

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.tneff.kmpremotecompose.remote.player.core.WebSensorSource

/**
 * REM-82/C5 + W1 — the wasmJs entry. Two jobs, symmetric to MainActivity / MainViewController:
 *
 *  1. **W1 (doc selection):** read the page URL query (`?rc=<name>&live=1&t=<sec>`) **once at start**
 *     and drive the SHARED [RcRouter] via [applyWebQueryToRouter] (commonMain, JVM-tested) — exactly the
 *     mobile parsers' sequence (`resetForLaunch` → `select` → `live` → `setStaticTime`), reusing their
 *     fail-safes. This makes the browser sweep (REM-80) addressable — each Maestro-web run loads
 *     `?rc=<name>`; without it the page is stuck on the default doc. Only the `window.location.search`
 *     read is web-specific (the parse + router-drive is pure shared code).
 *
 *  2. **Render:** [RemoteComposeApp] with its default loader, which fetches the selected `.rc` async via
 *     Compose-Multiplatform resources (`composeResources/files/rc/`) — no okio FileSystem in the browser.
 *
 * Scope: one parse at page-load is enough for the sweep (one URL per doc); SPA hash-change re-nav is
 * out of scope. The W2 DOM-marker mirror is REM-83 (separate), already in [RemoteComposeApp].
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    applyWebQueryToRouter(webLocationSearch())
    ComposeViewport {
        // REM-101 (D5) S4: inject the browser DeviceMotion sensor source (App-Shell injection, TechSpec
        // §5). Live + sensor-driven docs start it; static/no-motion → null → static frame (capability-floor).
        val sensorSource = remember { WebSensorSource() }
        RemoteComposeApp(sensorSource = sensorSource)
    }
}

/** The browser's `window.location.search` (e.g. `?rc=clock&t=3`), or "" outside a browser. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun webLocationSearch(): String =
    js("(typeof window !== 'undefined' && window.location) ? window.location.search : ''")
