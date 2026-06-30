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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.FloatConstant
import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawRect
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClipRectModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ComponentStart
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ScrollModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.player.core.RcClickEvent
import com.tneff.kmpremotecompose.remote.player.core.RcInteractionCallbacks
import com.tneff.kmpremotecompose.remote.player.core.RcScrollEvent
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-108 (Epic-F, REM-154) **S1 — scroll observability.** Proves the player surfaces the already-computed
 * `ScrollModifier.scrollOffset` through the S0 [RcInteractionCallbacks.onScroll] sink, read-only over the
 * existing `openScrollBracket` path (no new computation, no op write/read touched → §2 intact):
 *
 *  1. a LIVE paint of a scrollable doc emits exactly one `onScroll` per scroll holder, carrying the offset
 *     (`= scrollOffset`), the holder's component id, and the axis;
 *  2. the offset matches `ScrollModifier.scrollOffset` (the same value the bracket translates by);
 *  3. **STATIC paint emits nothing** — the §0 floor / golden-determinism invariant (no echo without a live
 *     pass);
 *  4. onClick is not emitted (that is S2).
 *
 * Built on the S3b synthetic scroll doc (a clipped 200×200 Column over 300px of content), seeding the paired
 * position directly (no text renderer needed); the on-device visual proof is test-1/2/3's Maestro drag gate.
 */
class Rem108S1ScrollObsTest {

    private val POS = 42; private val MAX = 43; private val NOTCH = 44

    private class RecordingCallbacks : RcInteractionCallbacks {
        val scrolls = mutableListOf<RcScrollEvent>()
        var clicks = 0
        override fun onClick(event: RcClickEvent) { clicks++ }
        override fun onScroll(event: RcScrollEvent) { scrolls += event }
    }

    /** A vertical Column(EXACT 200×200) with clip+scroll over three EXACT 100×100 children (content = 300). */
    private fun scrollDoc(position: Float): RemoteComposeDocument {
        val ops = ArrayList<Operation>()
        ops += Header.flat(200, 200)
        ops += FloatConstant(POS, position) // paired scroll position (seed directly; no TE in the synthetic doc)
        ops += RootLayout(-2)
        ops += ColumnLayout(-3, animationId = -1, horizontalPositioning = 1, verticalPositioning = 4, spacedBy = 0f)
        ops += WidthModifier(DimensionType.EXACT, 200f)
        ops += HeightModifier(DimensionType.EXACT, 200f)
        ops += ClipRectModifier()
        ops += ScrollModifier(ScrollModifier.VERTICAL, WireTypes.asNan(POS), WireTypes.asNan(MAX), WireTypes.asNan(NOTCH))
        ops += ContainerEnd()
        ops += LayoutContent(-4)
        for (id in listOf(-5, -6, -7)) {
            ops += ComponentStart(0, id, 100f, 100f)
            ops += DrawRect(0f, 0f, 100f, 100f)
            ops += ContainerEnd()
        }
        ops += ContainerEnd()
        ops += ContainerEnd()
        ops += ContainerEnd()
        return RemoteComposeDocument(ops)
    }

    @Test fun livePaint_emitsScrollOffset() {
        val ctx = RemoteContext().also { it.animationEnabled = true }
        val sink = RecordingCallbacks()
        val doc = scrollDoc(position = 30f)
        RemoteComposePlayer(ctx).paint(doc, NoOpPaintContext(ctx), callbacks = sink)
        // Exactly one onScroll for the single scroll holder; offset = −min(max=100, position=30) = −30.
        assertEquals(1, sink.scrolls.size, "live paint must emit exactly one onScroll per scroll holder")
        assertEquals(-30f, sink.scrolls[0].offset, 0.01f, "offset must equal ScrollModifier.scrollOffset (−min(max,pos))")
        assertEquals(ScrollModifier.VERTICAL, sink.scrolls[0].axis, "axis must carry the scroll direction")
        assertEquals(0, sink.clicks, "S1 must not emit onClick (that is S2)")
    }

    @Test fun staticPaint_emitsNothing() {
        // No animation → the §0 floor: the player must NOT echo a scroll offset (golden/determinism guard).
        // animationEnabled defaults true, so static mode must be set explicitly (the app sets it = `live`).
        val ctx = RemoteContext().also { it.animationEnabled = false }
        val sink = RecordingCallbacks()
        RemoteComposePlayer(ctx).paint(scrollDoc(position = 30f), NoOpPaintContext(ctx), callbacks = sink)
        assertEquals(0, sink.scrolls.size, "static paint must emit no onScroll (determinism / §0 floor)")
    }

    @Test fun emittedOffset_tracksPosition() {
        // The echoed offset follows the live position: a larger drag position → a larger (clamped) offset.
        for ((pos, expected) in listOf(0f to 0f, 30f to -30f, 100f to -100f, 250f to -100f /* clamped at max=100 */)) {
            val ctx = RemoteContext().also { it.animationEnabled = true }
            val sink = RecordingCallbacks()
            RemoteComposePlayer(ctx).paint(scrollDoc(position = pos), NoOpPaintContext(ctx), callbacks = sink)
            assertTrue(sink.scrolls.isNotEmpty(), "live paint at pos=$pos should emit onScroll")
            assertEquals(expected, sink.scrolls.last().offset, 0.01f, "offset at pos=$pos must be −min(max,pos)")
        }
    }
}
