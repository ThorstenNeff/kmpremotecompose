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
 * REM-162 — durable Creation-DSL fixture for the **clickable-in-scroll** pattern (scroll-Column over
 * 2 items, 1st item clickable via `MODIFIER_TOUCH_DOWN`). Doubles as:
 *  - **Creation-DSL completeness anchor** — proves the DSL can author the app-core pattern (a
 *    clickable list-item inside a scrollable container) end-to-end (Profile gating + scroll modifier
 *    + touch-down modifier + integer-change action all compose together).
 *  - **REM-162 player-dispatch regression gate** — exercises REM-108-S2 click-execution +
 *    REM-145-S1 touch-modifier on the very fixture dev-1's REM-162 fix is verified against. The
 *    dispatch passes pre-fix (hit-test is independent of the render-side scroll bug); post-fix it
 *    keeps passing and prevents re-regression.
 *
 * **Scope chosen (PO clarification 2026-06-30):** the fixture lives as a TEST-SOURCE-CANONICAL doc
 * (not a corpus `.rc` file) because:
 *  - The 173-doc corpus PROVENANCE pins "verbatim upstream copies"; this fixture is our own
 *    creation-DSL output, not an upstream binary.
 *  - **NO render-golden for this fixture yet** (the current player render is wrong — that IS the
 *    REM-162 bug; the correct render-golden lands WITH dev-1's fix). DesktopRenderSweep would
 *    auto-capture any new corpus `.rc` → risk of baking a buggy reference. Living in test source
 *    avoids that.
 *  - The bytes are still anchored: structural-non-vacuity pin + byte-size sanity + determinism.
 *
 * **Compose-DSL mirror omitted** (intentional, scope-noted): the procedural DSL needs to allocate
 * a region-0 integer via `addInt(0)` for the click action's target, and there is no
 * `RemoteAddInt`-style Compose-DSL surface yet (REM-141 covers Float/Color expressions, REM-145
 * covers touch modifiers; an integer-constant Compose surface is out of REM-162 scope). The
 * procedural fixture is sufficient for both stated purposes — the byte-equality Stage-1 pin lands
 * naturally when a future REM ticket adds the missing Compose-DSL surface.
 *
 * Doc shape:
 *  - 400×600 canvas, PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL (touch-modifiers live in the
 *    AndroidX-experimental overlay — REM-145-S1).
 *  - Region-0 `DATA_INT(counterId, 0)` — the integer the click action mutates.
 *  - `root { column(modifier.scroll(VERTICAL)) { boxLeaf(.. .onTouchDown { valueIntegerChange(counterId, 1) }) ; boxLeaf(..) } }`
 *  - Box dimensions: 400 wide × 200 tall each, so item 1 covers y∈[0,200], item 2 covers y∈[200,400].
 */
class Rem162ClickableScrollFixtureTest {

    private val androidxExperimental: Profile = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL,
        services = defaultRcPlatformServices(),
    )

    /**
     * The procedural creation-DSL fixture. Returns the doc bytes and the allocated counter id.
     */
    private fun proceduralFixture(): Pair<ByteArray, Int> {
        var counterIdCaptured = -1
        val bytes = document(
            width = 400, height = 600,
            profile = androidxExperimental,
            contentDescription = "rem162-clickable-scroll",
        ) {
            val counterId = addInt(0)
            counterIdCaptured = counterId
            root {
                column(
                    modifier = LayoutModifier().scroll(LayoutModifier.SCROLL_VERTICAL),
                ) {
                    // Item 1 — clickable: a touch-down increments the counter to 1.
                    boxLeaf(
                        modifier = LayoutModifier()
                            .width(DimensionType.EXACT, 200)
                            .height(DimensionType.EXACT, 200)
                            .background(0xFFFF0000.toInt())
                            .onTouchDown { valueIntegerChange(counterId, 1) },
                    )
                    // Item 2 — passive: no touch modifier; tap-DOWN here must NOT mutate the counter.
                    boxLeaf(
                        modifier = LayoutModifier()
                            .width(DimensionType.EXACT, 200)
                            .height(DimensionType.EXACT, 200)
                            .background(0xFF0000FF.toInt()),
                    )
                }
            }
        }
        return bytes to counterIdCaptured
    }

    // --- DSL-completeness / DSL-drift pins (Stage-3 + Stage-2-self-golden) ------------------------

    /**
     * Stage-3a — determinism. Emit the same fixture 5× and assert all bytes are identical. Catches
     * accidental non-determinism (e.g. HashMap iteration order, time-driven allocator).
     */
    @Test
    fun stage3a_procedural_determinism_5x() {
        val docs = (1..5).map { proceduralFixture().first }
        for (i in 1..4) {
            assertContentEquals(docs[0], docs[i], "fixture run #$i diverged from run #0 — non-determinism")
        }
        // Counter-id allocation must also be deterministic across runs.
        val firstCounter = proceduralFixture().second
        for (i in 1..4) {
            val (_, c) = proceduralFixture()
            assertEquals(firstCounter, c, "counter-id allocator drifted on run #$i")
        }
    }

    /**
     * Stage-3b — structural non-vacuity. The fixture must contain the actual ops the pattern needs
     * — NOT a trivial header-only or empty-container doc. Pins the structural recipe so a future
     * accidental simplification (e.g. lost `onTouchDown` emission) is caught.
     */
    @Test
    fun stage3b_structural_nonVacuity() {
        val (bytes, _) = proceduralFixture()
        val ops = DocumentReader.inflateWithTrace(bytes).first.operations
        val opcodes = ops.map { it.opcode }
        assertTrue(opcodes.contains(Operations.DATA_INT), "expected DATA_INT for the counter")
        assertTrue(opcodes.contains(Operations.LAYOUT_ROOT), "expected LAYOUT_ROOT container")
        assertTrue(opcodes.contains(Operations.LAYOUT_COLUMN), "expected LAYOUT_COLUMN")
        assertEquals(2, opcodes.count { it == Operations.LAYOUT_BOX }, "expected exactly 2 LAYOUT_BOX")
        assertTrue(opcodes.contains(Operations.MODIFIER_SCROLL), "expected MODIFIER_SCROLL on the column")
        assertEquals(
            1, opcodes.count { it == Operations.MODIFIER_TOUCH_DOWN },
            "exactly one item must carry MODIFIER_TOUCH_DOWN (the first); the second is passive",
        )
        assertTrue(
            opcodes.contains(Operations.VALUE_INTEGER_CHANGE_ACTION),
            "expected VALUE_INTEGER_CHANGE_ACTION inside the touch-down block",
        )
    }

    /**
     * Stage-2-self-golden — DSL-drift detector. The fixture byte-size is pinned here as a coarse
     * self-golden; if a deliberate recipe change shifts the size, update the expected range.
     * A fine-grained hex-snapshot is omitted because the DSL allocator assigns ids dynamically
     * (DATA_INT id, scroll's positionId/maxId/notchMaxId, TouchExpression target) — the structural
     * recipe is the stable contract.
     */
    @Test
    fun stage2_self_golden_byte_size_pin() {
        val (bytes, _) = proceduralFixture()
        assertTrue(
            bytes.size in 100..600,
            "fixture size (${bytes.size}B) outside sanity range [100, 600] — DSL recipe drift?",
        )
        // Header opcode at byte 0 is `Header.opcode = 0x00`.
        assertEquals(0x00.toByte(), bytes[0], "expected header opcode 0x00 at byte 0")
    }

    // --- REM-162 player-dispatch regression gate -----------------------------------------------

    /**
     * **REM-162 regression gate.** A DOWN inside item 1's bounds must fire the
     * `MODIFIER_TOUCH_DOWN` action → `VALUE_INTEGER_CHANGE_ACTION` mutates the counter int. The
     * existing REM-108-S2 dispatch already passes this on hand-built single-Box docs
     * (`Rem108S2ClickExecTest`); this version exercises it on the **clickable-in-scroll** pattern
     * — dev-1's REM-162 fix must continue to satisfy this gate.
     *
     * **Pre-fix expectation:** the dispatch's hit-test is independent of the render-side scroll
     * bug (REM-108-S2's `LayoutMeasure.ClickTarget` builds from the layout-measure pass that
     * computes correct child bounds regardless of the render-side issue). So this dispatch test
     * passes pre-fix and continues to pass post-fix — serving as a permanent guard against
     * re-regression.
     */
    @Test
    fun rem162_dispatch_item1_touchDown_fires_action() {
        val (bytes, counterId) = proceduralFixture()
        val doc = DocumentReader.inflate(bytes)

        val ctx = RemoteContext().also { it.animationEnabled = true }
        val player = RemoteComposePlayer(ctx)
        val tap = TapState().apply { down(100f, 100f) } // dead-center of item 1 (200×200 at top)
        player.paint(doc, NoOpPaintContext(ctx), tapState = tap)

        assertEquals(
            1, ctx.getInt(counterId),
            "REM-162 regression: DOWN inside item 1 (x=100,y=100) must fire MODIFIER_TOUCH_DOWN " +
                "→ counter(id=$counterId) := 1. Got ${ctx.getInt(counterId)}.",
        )
        assertEquals(
            "$counterId=1", player.lastActionEcho,
            "action echo must be '<counterId>=1' — confirms the action ran through runAction.",
        )
    }

    /**
     * Control case: a DOWN on item 2 (the passive box, no `MODIFIER_TOUCH_DOWN`) must NOT fire any
     * action. Proves the dispatch's hit-test correctly distinguishes items by y-bounds and the
     * absence of a touch modifier on item 2 prevents action firing.
     */
    @Test
    fun rem162_dispatch_item2_touchDown_does_NOT_fire_action() {
        val (bytes, counterId) = proceduralFixture()
        val doc = DocumentReader.inflate(bytes)

        val ctx = RemoteContext().also { it.animationEnabled = true }
        val player = RemoteComposePlayer(ctx)
        val tap = TapState().apply { down(100f, 300f) } // dead-center of item 2 (y∈[200,400])
        player.paint(doc, NoOpPaintContext(ctx), tapState = tap)

        assertEquals(
            0, ctx.getInt(counterId),
            "DOWN on item 2 (passive box, no MODIFIER_TOUCH_DOWN) must NOT fire any action — " +
                "counter(id=$counterId) stays at 0. Got ${ctx.getInt(counterId)}.",
        )
    }
}
