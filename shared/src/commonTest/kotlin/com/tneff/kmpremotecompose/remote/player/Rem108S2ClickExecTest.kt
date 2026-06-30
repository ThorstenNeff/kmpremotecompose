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
import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.IntegerConstant
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.layout.BoxLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClickModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchDownModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchUpModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ValueIntegerChangeAction
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.player.core.RcClickEvent
import com.tneff.kmpremotecompose.remote.player.core.RcInteractionCallbacks
import com.tneff.kmpremotecompose.remote.player.core.RcScrollEvent
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.TapState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REM-108 (Epic-F, REM-154) **S2 — click execution (Option B + active-touch-span).** Drives the player's
 * click dispatch headlessly over synthetic docs whose single 200×200 box (= bounds 0,0..200,200, known so
 * the hit-test is precise) carries a click/touch modifier + a `VALUE_INTEGER_CHANGE_ACTION` over `DATA_INT`
 * id42. Proves:
 *  1. a tap (down+up inside) runs the `MODIFIER_CLICK` action → id42 mutates, echo = "42=<v>", onClick fires;
 *  2. `MODIFIER_TOUCH_DOWN` runs on the DOWN itself;
 *  3. **active-span / drag-off** — a DOWN inside then UP *outside* still delivers `MODIFIER_TOUCH_UP` to the
 *     down-span (routed, NOT re-hit-tested at up-time), and does NOT fire a CLICK;
 *  4. the mutation **persists** across the per-frame-fresh context (re-applied from the overrides);
 *  5. a STATIC render never dispatches (determinism / §0 floor); a tap that hits nothing does nothing.
 *
 * §2: no op `write`/`read`/`equals`/`hashCode` is touched — the dispatch only mutates the runtime int store.
 */
class Rem108S2ClickExecTest {

    private val INT_ID = 42

    private class RecordingCallbacks : RcInteractionCallbacks {
        val clicks = mutableListOf<RcClickEvent>()
        override fun onClick(event: RcClickEvent) { clicks += event }
        override fun onScroll(event: RcScrollEvent) {}
    }

    /** A 200×200 box (bounds 0,0..200,200) with [modifier] + a VALUE_INTEGER_CHANGE_ACTION(id42 := [value]). */
    private fun clickDoc(modifier: Operation, value: Int): RemoteComposeDocument {
        val ops = ArrayList<Operation>()
        ops += Header.flat(200, 200)
        ops += IntegerConstant(INT_ID, 0) // DATA_INT default 0 (re-applied each frame in Phase A)
        ops += RootLayout(-2)
        ops += BoxLayout(-3, -1, 1, 4)
        ops += WidthModifier(DimensionType.EXACT, 200f)
        ops += HeightModifier(DimensionType.EXACT, 200f)
        ops += modifier
        ops += ValueIntegerChangeAction(INT_ID, value)
        ops += ContainerEnd() // closes the modifier's action block
        ops += LayoutContent(-4)
        ops += ContainerEnd() // content
        ops += ContainerEnd() // box
        ops += ContainerEnd() // root
        return RemoteComposeDocument(ops)
    }

    /** Paint one frame with a FRESH context (as the app does) and return (mutated-int, echo). */
    private fun paintFrame(doc: RemoteComposeDocument, live: Boolean, tap: TapState, sink: RcInteractionCallbacks): Pair<Int, String?> {
        val ctx = RemoteContext().also { it.animationEnabled = live }
        val player = RemoteComposePlayer(ctx)
        player.paint(doc, NoOpPaintContext(ctx), tapState = tap, callbacks = sink)
        return ctx.getInt(INT_ID) to player.lastActionEcho
    }

    @Test fun tap_runsClickAction_mutatesInt_emitsEchoAndOnClick() {
        val doc = clickDoc(ClickModifier(), value = 1)
        val tap = TapState(); val sink = RecordingCallbacks()
        tap.down(100f, 100f); tap.up(100f, 100f) // a real tap inside the box
        val (mutated, echo) = paintFrame(doc, live = true, tap, sink)
        assertEquals(1, mutated, "MODIFIER_CLICK must run VALUE_INTEGER_CHANGE_ACTION → id42 = 1")
        assertEquals("42=1", echo, "action echo must be '<valueId>=<value>'")
        assertTrue(sink.clicks.any { it.elementId == -3 }, "onClick must fire for the clicked component (-3)")
    }

    @Test fun touchDown_runsOnDown() {
        val doc = clickDoc(TouchDownModifier(), value = 2)
        val tap = TapState(); val sink = RecordingCallbacks()
        tap.down(100f, 100f) // down alone fires MODIFIER_TOUCH_DOWN
        val (mutated, echo) = paintFrame(doc, live = true, tap, sink)
        assertEquals(2, mutated, "MODIFIER_TOUCH_DOWN must run its action on the DOWN")
        assertEquals("42=2", echo)
    }

    @Test fun dragOff_upRoutesToDownSpan_noReHit_noClick() {
        // DOWN inside, UP far outside the box → MODIFIER_TOUCH_UP still fires (routed to the down-span); a
        // re-hit-test at (300,300) would find nothing, so this proves the active-span routing. No CLICK.
        val doc = clickDoc(TouchUpModifier(), value = 7)
        val tap = TapState(); val sink = RecordingCallbacks()
        tap.down(100f, 100f); tap.up(300f, 300f)
        val (mutated, _) = paintFrame(doc, live = true, tap, sink)
        assertEquals(7, mutated, "drag-off: UP must route to the DOWN-span (not a re-hit-test at up-time)")
        assertEquals(TapState.NO_SPAN, tap.activeSpanId, "the span must be cleared after UP")
    }

    @Test fun mutation_persistsAcrossFrames() {
        val doc = clickDoc(ClickModifier(), value = 1)
        val tap = TapState(); val sink = RecordingCallbacks()
        tap.down(100f, 100f); tap.up(100f, 100f)
        paintFrame(doc, live = true, tap, sink) // frame 1: click
        val (mutated2, echo2) = paintFrame(doc, live = true, tap, sink) // frame 2: fresh ctx, no new events
        assertEquals(1, mutated2, "the click mutation must persist across the per-frame-fresh context")
        assertNull(echo2, "no NEW action fired on frame 2 → echo is null (the app keeps its last value)")
    }

    @Test fun staticRender_neverDispatches() {
        val doc = clickDoc(ClickModifier(), value = 1)
        val tap = TapState(); val sink = RecordingCallbacks()
        tap.down(100f, 100f); tap.up(100f, 100f)
        val (mutated, echo) = paintFrame(doc, live = false, tap, sink)
        assertEquals(0, mutated, "static mode must not dispatch clicks (determinism / §0 floor)")
        assertNull(echo); assertTrue(sink.clicks.isEmpty(), "no onClick in static mode")
    }

    @Test fun tapMissingAllTargets_doesNothing() {
        val doc = clickDoc(ClickModifier(), value = 1)
        val tap = TapState(); val sink = RecordingCallbacks()
        tap.down(300f, 300f); tap.up(300f, 300f) // outside the 200×200 box
        val (mutated, echo) = paintFrame(doc, live = true, tap, sink)
        assertEquals(0, mutated, "a tap that hits no target must run no action")
        assertNull(echo); assertTrue(sink.clicks.isEmpty())
    }
}
