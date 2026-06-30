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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import kotlin.math.abs
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
import com.tneff.kmpremotecompose.remote.player.core.HapticActuator
import com.tneff.kmpremotecompose.remote.player.core.NoOpHapticActuator
import com.tneff.kmpremotecompose.remote.player.core.NoOpSensorSource
import com.tneff.kmpremotecompose.remote.player.core.RcClickEvent
import com.tneff.kmpremotecompose.remote.player.core.RcInteractionCallbacks
import com.tneff.kmpremotecompose.remote.player.core.RcScrollEvent
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.renderOpaque
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.SensorSource
import com.tneff.kmpremotecompose.remote.player.core.TapState
import com.tneff.kmpremotecompose.remote.player.core.baselineHostPalette
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
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
    hapticActuator: HapticActuator = NoOpHapticActuator,
    // REM-108 (Epic-F) S0: the public interaction-callback sink (default NoOp = §0 floor). Additive; the
    // emitting paths land in S1 (onScroll) / S2 (onClick). NoOp default ⇒ render-/behaviour-identical today.
    callbacks: RcInteractionCallbacks = RcInteractionCallbacks.NoOp,
    modifier: Modifier = Modifier,
) {
    // The selected bundled doc (REM-34): default, or a deep-link `kmprc://render?rc=<name>` via RcRouter.
    val docName = RcRouter.docName
    // REM-37 E-D1: live-animation flag (deep-link `&live=1`). Default false ⇒ static t=0 (deterministic golden).
    val live = RcRouter.live
    // REM-62: static-mode frame pin (deep-link `&t=N`). Read at composition level so a change recomposes →
    // the Canvas redraws at the new pinned frame. Only consulted in static mode; 0f ⇒ original t=0 path.
    val staticTimeSeconds = RcRouter.staticTimeSeconds
    // REM-178-S2: epoch seed for ID_EPOCH_SECOND (REM-176) → derived REM-177 calendar vars (year, month,
    // day-of-year, day-of-week). Deep-link `&epoch=<sec>` override wins; otherwise the live App-Shell
    // reads the platform wall clock via [epochNow] so a live launch of an epoch-driven doc shows TODAY,
    // not 1970. **No test-pin fallback in the Library** (REM-178-S2 architecture cleanup, PO Option-a):
    // `RenderTimePins.epochFor` is consulted only by the desktop sweep harness; Maestro mobile static
    // captures of the 2 epoch-pinned docs deep-link `&epoch=1751529600` explicitly. Read at composition
    // level so a deep-link epoch change recomposes → the Canvas redraws against the new epoch.
    val paintEpoch: Long = RcRouter.epochSeconds.takeIf { it > 0L } ?: epochNow()
    var frameTime by remember { mutableStateOf(0f) }
    var doc by remember { mutableStateOf<RemoteComposeDocument?>(null) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    var committed by remember { mutableStateOf(false) }
    var drawCount by remember { mutableStateOf(0) }
    var renderError by remember { mutableStateOf<String?>(null) }
    // REM-143 S3-prep: a monotonic committed-FRAME counter (live-only) exposed as `rc-frame-count` so a
    // Maestro flow can prove "frames advanced after a touch" (f1 > f0) WITHOUT a flaky pixel-diff (W9).
    // App-side debug hook ONLY — no `.rc` wire/format touch, no static-render effect (§2 untouched; static
    // mode never bumps it, see the gate below). `lastCountedFrame` dedups so it bumps once per distinct
    // committed [renderTime] (the same one-settling-recompose idiom as `drawCount`), never self-storms.
    var frameCount by remember { mutableStateOf(0) }
    var lastCountedFrame by remember { mutableStateOf(Float.NaN) }
    // The name of the doc that ACTUALLY produced the committed frame — set at the commit point (below),
    // not read from the live RcRouter. Decouples rc-doc from the router so rc-rendered + rc-doc always
    // describe the same frame (test-2 rc-doc race fix): the live docName can change a composition before
    // the new doc loads/commits, which would briefly show rc-rendered (old) alongside rc-doc (new).
    var renderedDocName by remember { mutableStateOf("") }
    // REM-108 (Epic-F) S0: the two interaction echo hooks (test-1's conformance surface), wired NoOp-until-fed.
    // CONTRACT (PO lock 2026-06-30 — test-1's Maestro gate pins these formats exactly):
    //  • `rc-action-echo`  — fed in S2 from the click-action dispatch as **"<valueId>=<value>"** (e.g. a
    //    VALUE_INTEGER_CHANGE_ACTION on DATA_INT id42→2 emits "42=2", NOT bare "2"). S0 NoOp sentinel "none"
    //    (cannot collide with a real "id=value").
    //  • `rc-scroll-offset` — fed in S1 (dev-2) from the already-computed `ScrollModifier.scrollOffset` as the
    //    offset value. S0 NoOp sentinel "0".
    // Present so Maestro can address them, but render-invariant (hook nodes below the canvas, outside the
    // cropped render area; the canvas renders independently at pxSize → zero pixel shift). §2-safe: render-only
    // state, no `.rc` bytes. The producing slices flip these via their own state writes.
    // S2 feeds this from the player's lastActionEcho (below) in "<valueId>=<value>" form; S0 sentinel "none".
    var actionEcho by remember { mutableStateOf("none") }
    // S1 feeds this from the player's onScroll (below); S0 left it at the "0" sentinel.
    var scrollOffset by remember { mutableStateOf(0) }
    // REM-108 S1: a stable per-frame capture cell the observing sink writes during paint (the draw phase),
    // settled into [scrollOffset] once after the render lambda — the same write-during-draw → settle-after
    // idiom as `drawCount`/`frameCount` (avoids a recompose storm). intArrayOf is a GC-light mutable holder.
    val scrollCapture = remember { intArrayOf(0) }
    // REM-108 S2: per-frame capture cell for the click-action echo ("<valueId>=<value>"), settled into
    // [actionEcho] after the render lambda. Null ⇒ no action fired this frame (the state keeps its last value).
    val actionEchoCapture = remember { arrayOfNulls<String>(1) }
    // REM-108 S2: persistent tap/click gesture state (down-span + click int-overrides survive across frames),
    // fed by the detectTapGestures detector below, drained by the player's click dispatch each live frame.
    val tapState = remember { TapState() }
    // REM-108 S1: the player's interaction sink, wrapping the app-supplied [callbacks]. It forwards every
    // event to the consumer's sink (the public contract) AND captures the scroll offset for the
    // `rc-scroll-offset` test hook. onClick is pass-through here (wired in S2). Remembered on [callbacks] so
    // it stays stable across the per-frame recompositions but re-wraps if the consumer swaps its sink.
    val observingCallbacks = remember(callbacks) {
        object : RcInteractionCallbacks {
            override fun onClick(event: RcClickEvent) { callbacks.onClick(event) }
            override fun onScroll(event: RcScrollEvent) {
                scrollCapture[0] = event.offset.toInt()
                callbacks.onScroll(event)
            }
        }
    }

    // Re-runs when the selected doc changes (deep-link) — reset per-doc render state, then load by name.
    LaunchedEffect(docName) {
        doc = null
        decodeError = null
        renderError = null
        committed = false
        drawCount = 0
        frameCount = 0
        lastCountedFrame = Float.NaN
        // REM-108 (assist S1 nit + S2): reset the interaction hooks + persistent gesture state on a doc
        // switch, so a previous doc's scroll offset / click override / down-span never bleeds into the new one.
        scrollCapture[0] = 0; scrollOffset = 0
        actionEchoCapture[0] = null; actionEcho = "none"
        tapState.intOverrides.clear(); tapState.activeSpanId = TapState.NO_SPAN
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
    // REM-158: the cross-target named-font resolver for the TYPEFACE op, with the bundled DancingScript
    // (cursive/script) + RobotoFlex defaults built from composeResources at composition scope (like the
    // symbol fallback above). Handed to the text half so a doc that sets a named/enum font renders it
    // instead of the silent default-sans (the missing name→FontFamily lookup the audit flagged).
    val namedFonts = rememberNamedFontResolver()
    // REM-68: the host system-accent / Material-You palette (name → ARGB), resolved once per composition
    // (Android reads real device colors; iOS/desktop mirror the baseline). Seeded into each render's
    // context below so a `NamedVariable`-bound theme color (e.g. color.system_accent1_100) overrides the
    // doc's debug `ColorConstant` fallback (REM-61/67) — clock/digital_clock1/color_table get real tones.
    // REM-135: a deep-link `&palette=baseline` forces the deterministic baseline palette (capture pin,
    // analog `&density=1.0`) — keyed on the flag so the choice re-resolves if a deep-link flips it. Absent ⇒
    // `systemAccentPalette()` = the real device Material-You accent (untouched live-app behavior).
    val themePalette = remember(RcRouter.forceBaselinePalette) {
        if (RcRouter.forceBaselinePalette) baselineHostPalette() else systemAccentPalette()
    }
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
            // REM-163: ONE unified gesture loop feeding BOTH the drag path (touchState → TouchExpression /
            // slider / scroll POS id13/14) and the tap path (tapState → click modifiers). The previous S2
            // design chained two competing detectors — `detectDragGestures` + a second `detectTapGestures`
            // pointerInput. On mobile their arbitration coexists; on **wasm/Skiko** the tap detector's
            // `tryAwaitRelease()` starved the drag detector's `onDrag`, so `touchState.move` never fired → the
            // POS vars were never fed → the touch1-slider stuck at its default (REM-163, test-2). A single
            // `awaitEachGesture` loop has no inter-detector arbitration: the drag POS-feed and the tap
            // dispatch both run on every target identically. The drag press-edge is still consumed by
            // `dispatchTouch` (DOWN→DRAG; TouchState.move never collapses DOWN→DRAG itself, REM-108 S2b).
            Modifier.pointerInput(d) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = down.position.x; val y0 = down.position.y
                    touchState.down(x0, y0) // drag/slider press edge
                    tapState.down(x0, y0)   // click press edge (MODIFIER_TOUCH_DOWN)
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val p = change.position
                            if (abs(p.x - x0) > slop || abs(p.y - y0) > slop) moved = true
                            touchState.move(p.x, p.y) // continuous POS feed for the TouchExpression
                        } else {
                            val p = change.position
                            touchState.up(p.x, p.y)
                            // a clean tap (no slop-crossing) → click release (MODIFIER_TOUCH_UP/CLICK); a
                            // drag-release → cancel the click (the player routes cancel to the down-span) —
                            // the same semantics the two-detector design produced.
                            if (moved) tapState.cancel() else tapState.up(p.x, p.y)
                            break
                        }
                    }
                }
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
                            // REM-68/REM-135 wiring: seed the host theme palette BEFORE paint (Phase A) via
                            // the shared seedHostPalette seam (the same one every render/golden harness must
                            // use, so they don't drift). Stored as pending name→ARGB overrides; as
                            // NamedVariable.apply registers each name during Phase A, the override binds to its
                            // colorId and wins over the ColorConstant fallback. Passes the remembered palette so
                            // the per-frame paint does not re-resolve the device colors each frame.
                            it.seedHostPalette(themePalette)
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
                                namedFontResolver = namedFonts,
                            )
                            // Geometry shares the context's one PlayerPaintState (REM-32) with the text half.
                            paintContext.geometry = GeometryPaintDelegate(ctx, target, paintContext.paintState)
                            // REM-101 S4 note (intentional — do NOT "fix" by wiring surfaceWidth/Height):
                            // surfaceWidth/Height are omitted on purpose, so the player's RootContentBehavior
                            // SIZING_SCALE block is skipped. The canvas is pinned to the doc's EXACT pixel dims
                            // (REM-51 pxSize), so the surface always equals doc-space → that scale would be the
                            // identity (scale=1, translate=0) and skipping it is a pure no-op (pixel-verified via
                            // the wasm full-pipeline render probe). Wiring them only matters if this app ever
                            // renders at a NON-native surface size (e.g. responsive web fit-to-viewport) — then
                            // pass size.width/height here so doc-space scales into the surface.
                            val player = RemoteComposePlayer(ctx)
                            player.paint(
                                d, paintContext,
                                frameTimeSeconds = renderTime,
                                // REM-62: static-mode frame pin (deep-link `&t=N`); the player reads it
                                // only when animation is off, so the live loop is untouched. 0f ⇒ t=0 path.
                                staticTimeSeconds = staticTimeSeconds,
                                // REM-178-S2: epoch seed for ID_EPOCH_SECOND → REM-177 calendar vars.
                                // Either the deep-link `&epoch=<sec>` override (RcRouter.epochSeconds)
                                // or the live wall-clock fallback (epochNow); composed above.
                                epochSeconds = paintEpoch,
                                // REM-101 (D5): the player seeds the doc's sensor ids from this LIVE-only.
                                sensorSource = sensorSource,
                                // REM-108 (S2b): live pointer gesture (consumed LIVE-only; null ⇒ no touch).
                                touchState = if (live) touchState else null,
                                // REM-143 (S3b): host haptic actuator (live-only; impulse fires on its
                                // initial pass). NoOp on desktop/web (capability-floor) → no buzz.
                                hapticActuator = hapticActuator,
                                // REM-108 (S0/S1): the interaction sink. The observing wrapper forwards to the
                                // app-supplied callbacks AND captures the scroll offset for rc-scroll-offset.
                                callbacks = observingCallbacks,
                                // REM-108 (S2): live tap/click gesture state (consumed LIVE-only; null ⇒ static).
                                tapState = if (live) tapState else null,
                            )
                            // REM-108 S2: capture the click-action echo this frame (draw-phase write; settled
                            // outside the lambda like scrollCapture). Null ⇒ no action fired this frame.
                            actionEchoCapture[0] = player.lastActionEcho
                        }
                        // Draw-phase writes: read only outside this lambda → one settling recompose.
                        if (drawCount != ctx.drawCount) drawCount = ctx.drawCount
                        // REM-108 S1: settle the scroll offset the observing sink captured this frame (live
                        // only; static never emits → stays at the 0 sentinel). Same one-settling-recompose
                        // discipline as drawCount → rc-scroll-offset reflects the latest live offset.
                        if (scrollOffset != scrollCapture[0]) scrollOffset = scrollCapture[0]
                        // REM-108 S2: settle the action echo if an action fired this frame (null ⇒ keep last).
                        actionEchoCapture[0]?.let { if (actionEcho != it) actionEcho = it }
                        // REM-143 S3-prep: bump the committed-frame counter once per distinct LIVE frame
                        // (renderTime advances every frame in live mode; static mode keeps renderTime=0f so
                        // the `live` gate + the dedup leave it at its reset 0 → deterministic, no storm).
                        if (live && lastCountedFrame != renderTime) {
                            lastCountedFrame = renderTime
                            frameCount += 1
                        }
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
                // REM-143 S3-prep: rc-frame-count = monotonic committed-frame counter (0 in static mode;
                // climbs every live frame). A Maestro touch flow reads it pre/post-swipe → f1 > f0 proves
                // the animation advanced, no pixel-diff needed. NB (causation): it climbs for ANY live
                // animation, so a touch→animation flow must use a doc that quiesces without touch (or
                // compare growth-rate) to isolate touch causation — flagged to the PO for test-1's flow.
                BasicText(frameCount.toString(), Modifier.testTag("rc-frame-count"))
                // rc-touch-echo = the last live pointer position the app accepted (doc-space px, "x,y").
                // 0,0 before any touch; a swipe moves it → an independent "the touch reached the app" signal
                // (decouples "input arrived" from "animation responded"). Live-only; static shows 0,0.
                val tx = if (live) touchState.x.toInt() else 0
                val ty = if (live) touchState.y.toInt() else 0
                BasicText("$tx,$ty", Modifier.testTag("rc-touch-echo"))
                // REM-108 S0: the two interaction echo hooks, NoOp-until-fed (S1 feeds rc-scroll-offset, S2
                // feeds rc-action-echo). Present now so the conformance flows can address them; the values
                // are the S0 NoOp sentinels until the producing slices wire them.
                BasicText(actionEcho, Modifier.testTag("rc-action-echo"))
                BasicText(scrollOffset.toString(), Modifier.testTag("rc-scroll-offset"))
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
