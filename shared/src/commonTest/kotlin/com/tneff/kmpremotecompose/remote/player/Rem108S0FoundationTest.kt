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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.core.RcClickEvent
import com.tneff.kmpremotecompose.remote.player.core.RcInteractionCallbacks
import com.tneff.kmpremotecompose.remote.player.core.RcScrollEvent
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-108 (Epic-F, REM-154) **S0 — the NoOp/zero-risk foundation gate.** S0 adds the public interaction
 * surface ([RcInteractionCallbacks] + [RcClickEvent]/[RcScrollEvent], the `.rcInteractive()` modifier stub,
 * the two echo hooks) as a purely additive, default-NoOp contract. This proves the foundation is inert:
 *
 *  1. the default sink swallows both events (no-op default impls) and the payload types carry their fields;
 *  2. **render-invariance** — painting a corpus doc with the param defaulted vs. an explicit `NoOp` is
 *     byte-for-byte the same draw stream (the param is unconsumed in S0 → the §0 floor / zero-shift gate);
 *  3. **no premature emission** — even a *recording* sink passed to `paint` receives **zero** callbacks in
 *     S0, so the producing paths (S1 `onScroll`, S2 `onClick`) are genuinely not wired yet.
 *
 * §2: this slice introduces no wire op and touches no operation's `write`/`read`/`equals`/`hashCode` — the
 * interaction state is render-only. The full 173-doc byte-conformance suite (unchanged) is the standing proof.
 */
class Rem108S0FoundationTest {

    private fun doc(name: String): RemoteComposeDocument {
        Builtins.register()
        return DocumentReader.inflate(RcCorpus.readFixture("corpus/$name"))
    }

    /** A draw-stream recorder: the render output we compare for invariance (independent of canvas pixels). */
    private class RecordingDraws(context: RemoteContext) : NoOpPaintContext(context) {
        val log = mutableListOf<String>()
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) { log += "rect($left,$top,$right,$bottom)" }
        override fun drawCircle(centerX: Float, centerY: Float, radius: Float) { log += "circle($centerX,$centerY,$radius)" }
        override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) { log += "line($x1,$y1,$x2,$y2)" }
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) { log += "oval($left,$top,$right,$bottom)" }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float) { log += "roundRect($left,$top,$right,$bottom,$radiusX,$radiusY)" }
        override fun drawPath(id: Int, start: Float, end: Float) { log += "path($id,$start,$end)" }
    }

    /** A sink that records every callback — used to prove S0 emits nothing. */
    private class RecordingCallbacks : RcInteractionCallbacks {
        var clicks = 0
        var scrolls = 0
        override fun onClick(event: RcClickEvent) { clicks++ }
        override fun onScroll(event: RcScrollEvent) { scrolls++ }
    }

    @Test fun noOpSink_isInert_andPayloadsCarryFields() {
        // The default sink must swallow both events without throwing (default no-op impls).
        RcInteractionCallbacks.NoOp.onClick(RcClickEvent(elementId = 7, metadata = 3, docX = 10f, docY = 20f))
        RcInteractionCallbacks.NoOp.onScroll(RcScrollEvent(componentId = 4, offset = -12.5f, axis = 0))
        // The payloads are plain data carriers (the Fremd-Team contract).
        val c = RcClickEvent(elementId = 7, metadata = 3, docX = 10f, docY = 20f)
        assertEquals(7, c.elementId); assertEquals(3, c.metadata); assertEquals(10f, c.docX); assertEquals(20f, c.docY)
        val s = RcScrollEvent(componentId = 4, offset = -12.5f, axis = 1)
        assertEquals(4, s.componentId); assertEquals(-12.5f, s.offset); assertEquals(1, s.axis)
    }

    @IgnoreOnWasm
    @Test fun paint_defaultVsExplicitNoOp_isRenderIdentical() {
        // The additive `callbacks` param must not perturb the render: defaulted == explicit NoOp, draw-for-draw.
        for (name in listOf("touch1.rc", "touch_wrap.rc", "c_modifier_on_touch_down.rc", "procedure_simple1.rc")) {
            val d = doc(name)
            val defaulted = RemoteContext().let { ctx -> RecordingDraws(ctx).also { RemoteComposePlayer(ctx).paint(d, it, frameTimeSeconds = 0f) }.log }
            val explicitNoOp = RemoteContext().let { ctx -> RecordingDraws(ctx).also { RemoteComposePlayer(ctx).paint(d, it, frameTimeSeconds = 0f, callbacks = RcInteractionCallbacks.NoOp) }.log }
            assertTrue(defaulted.isNotEmpty(), "$name should draw something (guard vs vacuous equality)")
            assertEquals(defaulted, explicitNoOp, "S0: the callbacks param must not change the render of $name")
        }
    }

    @IgnoreOnWasm
    @Test fun paint_emitsNothingInS0_evenToARecordingSink() {
        // S0 wires the surface but no producing path: a recording sink must see zero calls through a full paint.
        val sink = RecordingCallbacks()
        for (name in listOf("touch1.rc", "touch_wrap.rc", "c_modifier_on_touch_down.rc")) {
            val ctx = RemoteContext()
            val d = doc(name)
            RemoteComposePlayer(ctx).paint(d, RecordingDraws(ctx), frameTimeSeconds = 0f, callbacks = sink)
        }
        assertEquals(0, sink.clicks, "S0 must not emit onClick yet (wired in S2)")
        assertEquals(0, sink.scrolls, "S0 must not emit onScroll yet (wired in S1)")
    }
}
