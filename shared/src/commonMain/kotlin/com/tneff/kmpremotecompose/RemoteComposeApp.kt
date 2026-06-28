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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.compose.ComposePaintContext
import com.tneff.kmpremotecompose.remote.player.compose.GeometryPaintDelegate
import com.tneff.kmpremotecompose.remote.player.core.NoOpSensorSource
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.renderOpaque
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.SensorSource
import com.tneff.kmpremotecompose.remote.player.core.systemAccentPalette
import com.tneff.kmpremotecompose.remote.player.core.TouchState
import kmpremotecompose.shared.generated.resources.Res

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
 * @param loadRc **suspend** byte source for a bundled fixture **by name**. Default = the unified
 *   Compose-Multiplatform resources loader ([Res.readBytes] from `composeResources/files/rc/`), which
 *   serves the corpus on **every** target — Android/iOS/Desktop read it synchronously under the hood,
 *   wasmJs fetches it async from the web host (no okio FileSystem in the browser) (REM-82/C5). Suspend
 *   because the web read is genuinely async; the sole call site is already inside a `LaunchedEffect`
 *   (below), so no state-machine change is needed. Throws on an unknown name (404 / missing) → surfaced
 *   as `rc-error` (contract §2B; fail-closed — never returns empty bytes). The name comes from
 *   [RcRouter] (default or deep-link / web `?rc=`). An entry may still inject a custom loader (tests).
 * @param modifier applied to the root; the Android entry passes `semantics { testTagsAsResourceId =
 *   true }` (Android-only API) so Maestro can address the hooks by `id`. iOS maps testTag → a11y id.
 */
@Composable
fun RemoteComposeApp(
    loadRc: suspend (String) -> ByteArray = { name -> Res.readBytes("files/rc/$name.rc") },
    sensorSource: SensorSource = NoOpSensorSource,
    modifier: Modifier = Modifier,
) {
    // The selected bundled doc (REM-34): default, or a deep-link `kmprc://render?rc=<name>` via RcRouter.
    val docName = RcRouter.docName
    // REM-37 E-D1: live-animation flag (deep-link `&live=1`). Default false ⇒ static t=0 (deterministic golden).
    val live = RcRouter.live
    // REM-62: static-mode frame pin (deep-link `&t=N`). Read at composition level so a change recomposes →
    // the Canvas redraws at the new pinned frame. Only consulted in static mode; 0f ⇒ original t=0 path.
    val staticTimeSeconds = RcRouter.staticTimeSeconds
    var frameTime by remember { mutableStateOf(0f) }
    var doc by remember { mutableStateOf<RemoteComposeDocument?>(null) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    var committed by remember { mutableStateOf(false) }
    var drawCount by remember { mutableStateOf(0) }
    var renderError by remember { mutableStateOf<String?>(null) }
    // The name of the doc that ACTUALLY produced the committed frame — set at the commit point (below),
    // not read from the live RcRouter. Decouples rc-doc from the router so rc-rendered + rc-doc always
    // describe the same frame (test-2 rc-doc race fix): the live docName can change a composition before
    // the new doc loads/commits, which would briefly show rc-rendered (old) alongside rc-doc (new).
    var renderedDocName by remember { mutableStateOf("") }

    // Re-runs when the selected doc changes (deep-link) — reset per-doc render state, then load by name.
    LaunchedEffect(docName) {
        doc = null
        decodeError = null
        renderError = null
        committed = false
        drawCount = 0
        try {
            Builtins.register()
            doc = DocumentReader.inflate(loadRc(docName))
        } catch (t: Throwable) {
            decodeError = "doc '$docName': ${t.message ?: "not found"}"
            // REM-37 regression diagnosis: the swallowed throw is otherwise invisible — surface the stack.
            println("RC-THROW decode '$docName': ${t.stackTraceToString()}")
        }
    }

    // REM-37 E-D1 render loop: in live mode advance frameTime every frame so time-driven docs (clocks,
    // cube3d spin) animate; static/golden mode pins t=0 (deterministic). The flag gates ONLY the
    // time-advance — the render path is identical. MVP advances continuously; the player's wakeInSeconds
    // return (-1 static / 0 continuous) is the seam for a future wake-precise loop.
    LaunchedEffect(docName, live) {
        if (!live) {
            frameTime = 0f
            return@LaunchedEffect
        }
        // REM-114: drive via the frame-pacing seam, not withFrameNanos directly — on wasm a pure
        // withFrameNanos loop deadlocks the idle ComposeViewport frame clock (frozen at t=0). The seam
        // keeps vsync on Android/Desktop/iOS and a timer tick on wasm. See [awaitAnimationFrameNanos].
        val startNanos = awaitAnimationFrameNanos()
        while (true) {
            val nowNanos = awaitAnimationFrameNanos()
            frameTime = (nowNanos - startNanos) / 1_000_000_000f
        }
    }

    // REM-101 (D5) S2: drive the host [sensorSource] lifecycle. Start exactly the sensors the live doc
    // reads ([RemoteComposePlayer.sensorIdsUsed]) when it goes live; stop on leaving live / doc change /
    // disposal. Static mode never starts a sensor (determinism). NoOp default ⇒ no-op (S1 unchanged).
    // The render itself re-evaluates each frame: the live loop advances frameTime → recomposition → the
    // Canvas's paint() re-reads the source's latest values (full per-frame re-eval; no dirty-tracking).
    DisposableEffect(doc, live) {
        val usedIds = doc?.let { RemoteComposePlayer.sensorIdsUsed(it) } ?: emptySet()
        if (live && usedIds.isNotEmpty()) sensorSource.start(usedIds)
        onDispose { sensorSource.stop() }
    }

    // Read frameTime at COMPOSITION level (live only) so each advance recomposes → the Canvas redraws.
    val renderTime = if (live) frameTime else 0f
    // REM-91: a deep-link `&density=<f>` overrides the platform density for cross-target parity
    // (iOS-Sim is fixed @3x); absent ⇒ the real platform density (untouched behavior).
    val density = RcRouter.forcedDensity ?: LocalDensity.current.density
    // The CMP font resolver for the text half (REM-37 c_text / all text docs): without it the text
    // renderer is null and getTextBounds/drawTextRun no-op → text never renders. Supplied from composition.
    val fontResolver = LocalFontFamilyResolver.current
    // REM-110: bundled symbol-fallback family (♥/❤/⚡/⬩/▲/↑/↓), provided ONLY on wasm (the bug is web-only;
    // Android/iOS/Desktop render these via the system font → [symbolFallbackFamily] returns null there, so
    // their goldens don't shift). Resolved at composition scope (the resource Font is @Composable; the
    // render lambda below is not) and handed to the text half; the renderer applies it per-run only to
    // symbol-carrying text.
    val symbolFallback = symbolFallbackFamily()
    // REM-68: the host system-accent / Material-You palette (name → ARGB), resolved once per composition
    // (Android reads real device colors; iOS/desktop mirror the baseline). Seeded into each render's
    // context below so a `NamedVariable`-bound theme color (e.g. color.system_accent1_100) overrides the
    // doc's debug `ColorConstant` fallback (REM-61/67) — clock/digital_clock1/color_table get real tones.
    val themePalette = remember { systemAccentPalette() }
    // REM-108 S2b: persistent pointer-gesture state (survives the per-frame-fresh RemoteContext). Written
    // by the live pointerInput below, consumed one transition per frame by the player.
    val touchState = remember { TouchState() }
    val d = doc
    // REM-51: size the canvas in EXACT pixels (the doc dims are px). The old `(d.width/density).dp` round-trips
    // px→dp→px; at density 3 (iOS) the dp→px reconversion rounds down 1–2px (e.g. 400→399) → a ±2px A↔iOS
    // resize in the parity harness. A fixed-px layout (Constraints.fixed) pins the surface to exactly
    // d.width×d.height px on both platforms → pixel-coincident, no resize.
    val pxW = if (d != null && d.width > 0) d.width else 500
    val pxH = if (d != null && d.height > 0) d.height else 500

    // REM-108 S2b on-device fix: build the LIVE pointer-gesture modifier ONCE per (d, live) via remember,
    // so the per-frame `frameTime` recomposition (which rebuilds the rc-canvas modifier chain at ~60fps —
    // `pxSize` is a `Modifier.layout{}` whose fresh lambda diffs as changed every frame) can NOT recreate
    // the pointerInput node / restart its gesture coroutine mid-drag. That restart was the root cause of
    // the on-device "drag never crosses touch-slop → touchState stays empty" (test-1's 4-method probe;
    // headless passed only because those tests populate touchState directly). The detector captures the
    // remembered [touchState]; CMP detectDragGestures unifies touch/mouse/pointer across targets. Static
    // (non-live) → no pointer input → render stays deterministic. The doc-px Box (REM-51) makes the local
    // offset ≈ doc-space (the SIZING_SCALE-doc inverse is a deferred note).
    val gestureModifier: Modifier = remember(d, live) {
        if (live && d != null) {
            Modifier.pointerInput(d) {
                detectDragGestures(
                    onDragStart = { off -> touchState.down(off.x, off.y) },
                    onDrag = { change, _ -> touchState.move(change.position.x, change.position.y) },
                    onDragEnd = { touchState.up(touchState.x, touchState.y) },
                    onDragCancel = { touchState.cancel() },
                )
            }
        } else {
            Modifier
        }
    }

    Column(modifier.safeContentPadding()) {
        // rc-canvas = the render surface (this is what render_smoke crops for parity).
        Box(Modifier.pxSize(pxW, pxH).testTag("rc-canvas").then(gestureModifier)) {
            if (d != null && decodeError == null) {
                Canvas(Modifier.pxSize(pxW, pxH)) {
                    val canvas = drawContext.canvas
                    try {
                        val ctx = RemoteContext().also {
                            it.setDensity(density)
                            it.animationEnabled = live
                            // REM-68 wiring: seed the host theme palette BEFORE paint (Phase A). Stored as
                            // pending name→ARGB overrides; as NamedVariable.apply registers each name during
                            // Phase A, the override binds to its colorId and wins over the ColorConstant fallback.
                            it.setThemePaletteByName(themePalette)
                        }
                        // REM-56: render through an opaque surface (iOS only — Android renders direct) so a
                        // SRC_OUT/CLEAR draw composites to black, matching upstream's opaque Android View
                        // canvas. clearColor = white (the app bg behind the canvas). Surface-only; the doc
                        // render inside the block is unchanged.
                        renderOpaque(canvas, size.width.toInt(), size.height.toInt(), 0xFFFFFFFF.toInt()) { target ->
                            val paintContext = ComposePaintContext(
                                ctx, target,
                                fontFamilyResolver = fontResolver,
                                symbolFallbackFamily = symbolFallback,
                            )
                            // Geometry shares the context's one PlayerPaintState (REM-32) with the text half.
                            paintContext.geometry = GeometryPaintDelegate(ctx, target, paintContext.paintState)
                            RemoteComposePlayer(ctx).paint(
                                d, paintContext,
                                frameTimeSeconds = renderTime,
                                // REM-62: static-mode frame pin (deep-link `&t=N`); the player reads it
                                // only when animation is off, so the live loop is untouched. 0f ⇒ t=0 path.
                                staticTimeSeconds = staticTimeSeconds,
                                // REM-101 (D5): the player seeds the doc's sensor ids from this LIVE-only.
                                sensorSource = sensorSource,
                                // REM-108 (S2b): live pointer gesture (consumed LIVE-only; null ⇒ no touch).
                                touchState = if (live) touchState else null,
                            )
                        }
                        // Draw-phase writes: read only outside this lambda → one settling recompose.
                        if (drawCount != ctx.drawCount) drawCount = ctx.drawCount
                        // rc-doc binds to the name captured WITH this committed frame (`docName` here
                        // matches `d`, since the Canvas only composes after the load for this name).
                        if (renderedDocName != docName) renderedDocName = docName
                        if (!committed) committed = true
                    } catch (t: Throwable) {
                        if (renderError == null) renderError = t.message ?: "render failed"
                        // REM-37 regression diagnosis: surface the swallowed render throw (stack).
                        println("RC-THROW render '$docName': ${t.stackTraceToString()}")
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
                // rc-doc = the actually-rendered fixture name (REM-34): the robust "chosen doc" gate —
                // test-2 asserts rc-doc == ${RC}, directly proving identity (not the count≠1 proxy).
                // Bound to renderedDocName (the committed frame's name) so it never drifts from rc-rendered.
                BasicText(renderedDocName, Modifier.testTag("rc-doc"))
            }
        }
        // REM-83 (W2): mirror the hook state to DOM `data-*` on the wasm <canvas> so DOM web-drivers
        // (Maestro-chromium / Playwright) can read it (the testTags live inside the Skiko canvas). Uses
        // the SAME honest-render gate (`committed && drawCount > 0`) as the rc-rendered testTag — never
        // greens ahead of a paint. No-op on non-web targets. Fires once per committed composition.
        SideEffect {
            mirrorRenderMarkersToDom(
                rendered = committed && drawCount > 0,
                error = error,
                docName = renderedDocName,
                drawCount = drawCount,
            )
        }
    }
}

/**
 * REM-51: pin a node to exactly [width]×[height] **pixels** via [Constraints.fixed], bypassing the
 * `dp`→px reconversion that rounds 1–2px differently per density. Keeps the rc-canvas pixel-coincident
 * across Android/iOS so the parity harness needs no resize.
 */
private fun Modifier.pxSize(width: Int, height: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}
