/*
 * Copyright 2026 The KmpRemoteCompose Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tneff.kmpremotecompose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.compose.ComposePaintContext
import com.tneff.kmpremotecompose.remote.player.compose.GeometryPaintDelegate
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext

/**
 * REM-8 — the "touchable app" render vehicle. Loads one bundled `.rc`, decodes it through the Layer-1
 * reader, renders it through the Layer-2 player (S1 walk + S2 geometry adapter), and reports
 * success/failure through the frozen Maestro testTag hooks (`docs/rem8-app-shell-requirement.md` §1).
 *
 * One route, one canvas, the hook nodes. No chrome, no picker (anti-scope §3).
 *
 * **Honest-render gate (test-2 contract §1):** `rc-rendered` appears only after the first frame is
 * actually drawn **and** the player executed ≥ 1 paint primitive ([RemoteContext.drawCount] > 0). A
 * decode-OK pass that draws nothing surfaces `rc-error "rendered empty"` instead of false-greening. The
 * `rc-draw-count` node carries the primitive count for Maestro's independent `^[1-9][0-9]*$` check.
 *
 * @param loadRc platform byte source for the bundled fixture (Android assets / iOS bundle) — injected
 *   by the entry point so `commonMain` stays free of platform IO. May throw → surfaced as `rc-error`.
 * @param modifier applied to the root; the Android entry passes `semantics { testTagsAsResourceId =
 *   true }` (Android-only API) so Maestro can address the hooks by `id`. iOS maps testTag → a11y id.
 */
@Composable
fun RemoteComposeApp(loadRc: () -> ByteArray, modifier: Modifier = Modifier) {
    var doc by remember { mutableStateOf<RemoteComposeDocument?>(null) }
    var decodeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            Builtins.register()
            doc = DocumentReader.inflate(loadRc())
        } catch (t: Throwable) {
            decodeError = t.message ?: "decode failed"
        }
    }

    val density = LocalDensity.current.density
    var committed by remember { mutableStateOf(false) }
    var drawCount by remember { mutableStateOf(0) }
    var renderError by remember { mutableStateOf<String?>(null) }

    val d = doc
    // Fixed dp box on the doc dimension (density-normalized) so Android/iOS screenshots are coincident
    // (contract §1 — no fillMaxSize/wrap drift). Falls back to the corpus default until decode lands.
    val widthDp = if (d != null && d.width > 0) (d.width / density).dp else 500.dp
    val heightDp = if (d != null && d.height > 0) (d.height / density).dp else 500.dp

    Column(modifier.safeContentPadding()) {
        // rc-canvas = the render surface (this is what render_smoke crops for parity).
        Box(Modifier.size(widthDp, heightDp).testTag("rc-canvas")) {
            if (d != null && decodeError == null) {
                Canvas(Modifier.size(widthDp, heightDp)) {
                    val canvas = drawContext.canvas
                    try {
                        val ctx = RemoteContext().also { it.setDensity(density) }
                        val paintContext =
                            ComposePaintContext(ctx, canvas, GeometryPaintDelegate(ctx, canvas))
                        RemoteComposePlayer(ctx).paint(d, paintContext)
                        // Draw-phase writes: read only outside this lambda → one settling recompose.
                        if (drawCount != ctx.drawCount) drawCount = ctx.drawCount
                        if (!committed) committed = true
                    } catch (t: Throwable) {
                        if (renderError == null) renderError = t.message ?: "render failed"
                    }
                }
            }
        }

        // Hook nodes below the canvas (own visible bounds, outside the cropped render area). They carry
        // text so Maestro sees them as visible; an empty Box would report visible=false.
        val error =
            decodeError
                ?: renderError
                ?: if (committed && drawCount == 0) "rendered empty" else null
        when {
            error != null -> BasicText("error: $error", Modifier.testTag("rc-error"))
            committed && drawCount > 0 -> {
                BasicText("rendered", Modifier.testTag("rc-rendered"))
                BasicText(drawCount.toString(), Modifier.testTag("rc-draw-count"))
            }
        }
    }
}
