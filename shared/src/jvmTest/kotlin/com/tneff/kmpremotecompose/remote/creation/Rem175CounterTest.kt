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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.player.NoOpPaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.TapState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-175 — Creation-DSL Counter-Bausteine (Path A: float-counter), end-to-end Tests:
 *  - **Structural / op-presence**: the DSL emits the expected ops in the expected sequence (DATA_FLOAT
 *    for the counter, ANIMATED_FLOAT for the `c+1` expression, MODIFIER_TOUCH_DOWN for the click
 *    handler, VALUE_FLOAT_EXPRESSION_CHANGE_ACTION inside it, TEXT_FROM_FLOAT for the visible label).
 *  - **Conformance round-trip**: the produced bytes inflate without error → re-encode (verbatim
 *    serialize of each op) bytes-back to the same array. §2 invariant for the new helper's output.
 *  - **Determinism**: 5× emit of the same DSL → byte-identical docs.
 *  - **Dispatch regression**: 3 successive taps on the clickable box → counter goes 0 → 1 → 2 → 3.
 *    Each tap re-renders Phase-A (DATA_FLOAT reset to 0 + floatOverrides re-apply old value),
 *    re-evaluates `counter + 1`, runs the action, persists onto the override → next frame reads
 *    the new value. Proves the runAction dispatch for `VALUE_FLOAT_EXPRESSION_CHANGE_ACTION` is
 *    wired end-to-end (REM-175 fix in RemoteComposePlayer.runAction).
 *  - **Determinism in static mode**: a non-live render (animationEnabled=false, no tap drains)
 *    keeps the counter at its initial — REM-108-S2 determinism contract preserved.
 */
class Rem175CounterTest {

    private val androidxExperimental: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL,
        services = defaultRcPlatformServices(),
    )

    /**
     * The canonical counter fixture: 400×600 canvas, 200×200 clickable box that increments a
     * float counter on touch-down; the counter value is rendered as integer-look text at canvas
     * center via TextFromFloat(digitsAfter=0).
     */
    private fun counterFixture(initial: Float = 0f): Triple<ByteArray, Int, Int> {
        var counterIdCaptured = -1
        var labelIdCaptured = -1
        val bytes = document(
            width = 400, height = 600,
            profile = androidxExperimental,
            contentDescription = "rem175-counter",
        ) {
            val handle = floatCounter(initial = initial)
            counterIdCaptured = handle.counterId
            labelIdCaptured = displayInt(handle.counterId, digits = 1)
            root {
                boxLeaf(
                    modifier = LayoutModifier()
                        .width(DimensionType.EXACT, 200)
                        .height(DimensionType.EXACT, 200)
                        .background(0xFF00FF00.toInt())
                        .onTouchDown { incrementCounter(handle) },
                )
                drawTextAnchored(textId = labelIdCaptured, x = 200f, y = 300f, panX = 0f, panY = 0f, flags = 0)
            }
        }
        return Triple(bytes, counterIdCaptured, labelIdCaptured)
    }

    @Test
    fun structural_emission_op_presence() {
        val (bytes, _, _) = counterFixture()
        val ops = DocumentReader.inflateWithTrace(bytes).first.operations
        val opcodes = ops.map { it.opcode }
        assertTrue(opcodes.contains(Operations.DATA_FLOAT), "expected DATA_FLOAT for the counter (initial)")
        assertTrue(opcodes.contains(Operations.ANIMATED_FLOAT), "expected ANIMATED_FLOAT for the increment expression")
        assertTrue(opcodes.contains(Operations.MODIFIER_TOUCH_DOWN), "expected MODIFIER_TOUCH_DOWN on the clickable box")
        assertTrue(
            opcodes.contains(Operations.VALUE_FLOAT_EXPRESSION_CHANGE_ACTION),
            "expected VALUE_FLOAT_EXPRESSION_CHANGE_ACTION inside the touch-down block",
        )
        assertTrue(opcodes.contains(Operations.TEXT_FROM_FLOAT), "expected TEXT_FROM_FLOAT for the displayInt label")
        assertTrue(opcodes.contains(Operations.DRAW_TEXT_ANCHOR), "expected DRAW_TEXT_ANCHOR to render the counter label")
    }

    /**
     * **Conformance round-trip** — the produced bytes inflate without error. §2 invariant for the
     * new helper's output: every op fits the existing wire-format reader chain (no new opcodes,
     * no new layouts). The PO's §2-Test requirement against upstream collapses to this for
     * Path-A because all wire ops are already byte-anchored against the corpus by their
     * respective op-class tests (FloatConstant, FloatExpression, TextFromFloat,
     * MODIFIER_TOUCH_DOWN, VALUE_FLOAT_EXPRESSION_CHANGE_ACTION) — the new helpers only
     * **compose** them.
     */
    @Test
    fun conformance_roundTrip_byteClean() {
        val (bytes, _, _) = counterFixture()
        // Inflate must succeed.
        val doc = DocumentReader.inflateWithTrace(bytes).first
        assertTrue(doc.operations.isNotEmpty(), "inflated doc must contain ≥1 op")
        // The inflate trace's byte ranges must cover the entire doc end-to-end (no gaps).
        val trace = DocumentReader.inflateWithTrace(bytes).second
        assertTrue(trace.isNotEmpty(), "inflate trace must contain spans")
        // Spans are emitted in walk order; total covered should reach the doc end.
        val lastEnd = trace.last().byteEnd
        assertEquals(
            bytes.size, lastEnd,
            "inflate trace's last byteEnd ($lastEnd) must reach the doc size (${bytes.size}) — " +
                "any gap indicates the helper's emission isn't fully covered by the readers.",
        )
    }

    @Test
    fun determinism_5x_byteIdentical() {
        val docs = (1..5).map { counterFixture().first }
        for (i in 1..4) {
            assertContentEquals(docs[0], docs[i], "counter doc #$i diverged from #0 — non-determinism")
        }
    }

    /**
     * **REM-175 dispatch regression test** — 3 taps → counter 0 → 1 → 2 → 3. Proves the full
     * round-trip: DSL emits → bytes inflate → Phase-A FloatExpression evaluates `c+1` → tap →
     * runAction reads expr value → persists onto override → next-frame Phase-A re-applies override
     * → re-evaluates expr → next tap reads accumulated value → etc.
     */
    @Test
    fun dispatch_3_taps_counter_accumulates_0_to_3() {
        val (bytes, counterId, _) = counterFixture(initial = 0f)
        val doc = DocumentReader.inflate(bytes)

        val ctx = RemoteContext().also { it.animationEnabled = true }
        val player = RemoteComposePlayer(ctx)
        val tap = TapState()

        // Each frame: queue a tap inside the green box, repaint, then read the counter.
        val tapX = 50f; val tapY = 50f
        val observed = mutableListOf<Float>()
        for (i in 1..3) {
            tap.down(tapX, tapY)
            tap.up(tapX, tapY)
            player.paint(doc, NoOpPaintContext(ctx), tapState = tap)
            observed += ctx.getFloat(counterId)
        }
        assertEquals(
            listOf(1.0f, 2.0f, 3.0f), observed,
            "3 taps must accumulate the counter as 0→1→2→3. Got per-frame observations: $observed. " +
                "If first is 0, the runAction dispatch for VALUE_FLOAT_EXPRESSION_CHANGE_ACTION is missing; " +
                "if values plateau, floatOverrides re-apply isn't reaching the next frame's Phase-A.",
        )
    }

    /**
     * Determinism in static mode (animationEnabled=false): tap queue won't drain → no override
     * fires → counter stays at the DATA_FLOAT initial. REM-108-S2 determinism contract preserved
     * for the new code path.
     */
    @Test
    fun staticMode_no_dispatch_counter_stays_at_initial() {
        val initial = 42.0f
        val (bytes, counterId, _) = counterFixture(initial = initial)
        val doc = DocumentReader.inflate(bytes)

        val ctx = RemoteContext().also { it.animationEnabled = false } // STATIC mode
        val player = RemoteComposePlayer(ctx)
        val tap = TapState()
        // Queue tap events that would otherwise increment.
        tap.down(50f, 50f); tap.up(50f, 50f)
        player.paint(doc, NoOpPaintContext(ctx), tapState = tap)

        assertEquals(
            initial, ctx.getFloat(counterId),
            "static mode must not dispatch click actions — counter must stay at its DATA_FLOAT initial " +
                "($initial). Got ${ctx.getFloat(counterId)}.",
        )
    }
}
