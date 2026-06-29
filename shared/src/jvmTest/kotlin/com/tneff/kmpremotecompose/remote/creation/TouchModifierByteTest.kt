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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.IntegerConstant
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchCancelModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchDownModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchUpModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ValueIntegerChangeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-145 S1 — Byte-anchor tests for touch event modifiers (`onTouchDown` / `onTouchUp` /
 * `onTouchCancel`) + `valueIntegerChange` action.
 *
 * **Anchor strategy (mirror REM-141 borderColorRef sub-span pattern):** the touch-modifier
 * group emits a fixed 11-byte sequence on the wire:
 *   `[MODIFIER_TOUCH_X (1B)] + [VALUE_INTEGER_CHANGE_ACTION (9B: opcode + valueId + value)]
 *    + [CONTAINER_END (1B)]`
 *
 * The group is **ID-decoupled at the op level** — `MODIFIER_TOUCH_*` ops have no operands,
 * `VALUE_INTEGER_CHANGE_ACTION` stores raw int fields the caller controls, and `CONTAINER_END`
 * is opcode-only. So a sub-span extracted from the corpus byte-matches a sub-span from a
 * synthetic doc emitting the same modifier-group — **direct corpus byte-anchor**, no allocator
 * coupling.
 *
 * **Bug-#2 verify-first applied (W12 closed by REM-146):** the corpus emits `DATA_INT id=42`
 * at root-level alongside the touch modifier. Without REM-146's map-form-no-reserve fix, our
 * DSL would have produced id=43 for the DATA_INT and silently shifted any id-referencing
 * action — but the touch sub-span itself has no id reference, so it would still byte-match.
 * The DATA_INT id-anchor is asserted separately as a sanity-pin.
 *
 * **W13 ListActions-scope-leakage guard:** an explicit test verifies that a modifier added
 * AFTER `onTouchDown { ... }` lands at top-level (not sucked into the action list) — the
 * trailing `CONTAINER_END` must always close the scope before the next emitter runs.
 *
 * **W15 EXPERIMENTAL-profile gating:** all 3 corpus fixtures are profile=0x201
 * (PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL). Documents not opened under this profile cannot
 * decode the touch ops — verified by the corpus-fixture profile inspection at the
 * inflate-with-trace step.
 */
class TouchModifierByteTest {

    private val androidxExperimental = Profile(
        operationsProfiles = Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL,
        services = defaultRcPlatformServices(),
    )

    /**
     * Sub-span byte-anchor: extract the `MODIFIER_TOUCH_DOWN` + `VALUE_INTEGER_CHANGE_ACTION` +
     * `CONTAINER_END` 11-byte sequence from `c_modifier_on_touch_down.rc` and from a synthetic
     * minimal-doc emission of `LayoutModifier().onTouchDown { valueIntegerChange(42, 2) }`.
     * Assert byte-equal.
     */
    @Test
    fun onTouchDown_subSpan_matchesCorpusFixture() {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_on_touch_down.rc")
        val corpusGroup = extractTouchModifierGroup(corpus, Operations.MODIFIER_TOUCH_DOWN)
        assertEquals(11, corpusGroup.size, "MODIFIER_TOUCH_DOWN+VALUE_INT_CHANGE+ContainerEnd = 11 bytes")

        val emitted = document(width = 500, height = 500, profile = androidxExperimental) {
            box(modifier = LayoutModifier().onTouchDown { valueIntegerChange(valueId = 42, value = 2) }) {}
        }
        val emittedGroup = extractTouchModifierGroup(emitted, Operations.MODIFIER_TOUCH_DOWN)
        assertTrue(
            corpusGroup.contentEquals(emittedGroup),
            "onTouchDown sub-span must byte-match c_modifier_on_touch_down.rc — direct §2 anchor",
        )
    }

    /**
     * Sub-span byte-anchor for `MODIFIER_TOUCH_UP` (opcode 0xdc) against
     * `c_modifier_on_touch_up.rc`. Same shape as onTouchDown; corpus has VALUE_INTEGER_CHANGE_ACTION
     * `valueId=42 value=3`.
     */
    @Test
    fun onTouchUp_subSpan_matchesCorpusFixture() {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_on_touch_up.rc")
        val corpusGroup = extractTouchModifierGroup(corpus, Operations.MODIFIER_TOUCH_UP)

        val emitted = document(width = 500, height = 500, profile = androidxExperimental) {
            box(modifier = LayoutModifier().onTouchUp { valueIntegerChange(valueId = 42, value = 3) }) {}
        }
        val emittedGroup = extractTouchModifierGroup(emitted, Operations.MODIFIER_TOUCH_UP)
        assertTrue(
            corpusGroup.contentEquals(emittedGroup),
            "onTouchUp sub-span must byte-match c_modifier_on_touch_up.rc",
        )
    }

    /**
     * Sub-span byte-anchor for `MODIFIER_TOUCH_CANCEL` (opcode 0xe1) against
     * `c_modifier_on_touch_cancel.rc`. Corpus has VALUE_INTEGER_CHANGE_ACTION `valueId=42 value=4`.
     */
    @Test
    fun onTouchCancel_subSpan_matchesCorpusFixture() {
        val corpus = RcCorpus.readFixture("corpus/c_modifier_on_touch_cancel.rc")
        val corpusGroup = extractTouchModifierGroup(corpus, Operations.MODIFIER_TOUCH_CANCEL)

        val emitted = document(width = 500, height = 500, profile = androidxExperimental) {
            box(modifier = LayoutModifier().onTouchCancel { valueIntegerChange(valueId = 42, value = 4) }) {}
        }
        val emittedGroup = extractTouchModifierGroup(emitted, Operations.MODIFIER_TOUCH_CANCEL)
        assertTrue(
            corpusGroup.contentEquals(emittedGroup),
            "onTouchCancel sub-span must byte-match c_modifier_on_touch_cancel.rc",
        )
    }

    /**
     * VALUE_INTEGER_CHANGE_ACTION standalone byte pin — opcode + 4-byte int valueId + 4-byte int value
     * = 9 bytes total. ID-decoupled at the op level (caller passes both ints), so the wire bytes are
     * a strict function of the call args. Pins the wire shape independently of touch-modifier
     * scoping so a regression in either branch surfaces here.
     */
    @Test
    fun valueIntegerChange_emitsExpectedWireBytes() {
        val emitted = document(width = 100, height = 100, profile = androidxExperimental) {
            box(modifier = LayoutModifier().onTouchDown { valueIntegerChange(valueId = 42, value = 2) }) {}
        }
        val ops = DocumentReader.inflate(emitted).operations
        val action = ops.first { it is ValueIntegerChangeAction } as ValueIntegerChangeAction
        assertEquals(42, action.valueId)
        assertEquals(2, action.value)

        // Wire layout from the corpus probe: 0xD4 + 4-byte BE int (valueId) + 4-byte BE int (value)
        val spans = DocumentReader.inflateWithTrace(emitted).second
        val span = spans.first { it.opcode == Operations.VALUE_INTEGER_CHANGE_ACTION }
        val bytes = emitted.copyOfRange(span.byteStart, span.byteEnd)
        assertEquals(9, bytes.size, "VALUE_INTEGER_CHANGE_ACTION wire = 9 B (opcode + valueId + value)")
        // 0xD4 opcode, valueId=42=0x2A in big-endian, value=2 in big-endian.
        assertTrue(
            byteArrayOf(0xD4.toByte(), 0x00, 0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x02).contentEquals(bytes),
            "VALUE_INTEGER_CHANGE_ACTION(42, 2) wire shape must match the empirical pin.",
        )
    }

    /**
     * W13 ListActions-scope-leakage guard: a modifier added AFTER `onTouchDown { ... }` must
     * appear AFTER the trailing CONTAINER_END (not get pulled into the action list). Mirrors the
     * REM-96 `scroll_doesNotCorruptFollowingModifiers_postFix` test discipline.
     */
    @Test
    fun onTouchDown_doesNotCorruptFollowingModifier_scopeClosesCleanly() {
        val bytes = document(width = 100, height = 100, profile = androidxExperimental) {
            box(
                modifier = LayoutModifier()
                    .onTouchDown { valueIntegerChange(valueId = 42, value = 1) }
                    .background(0xFF112233.toInt()),
            ) {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        // Find the position of the LIST-ACTIONS-closing CONTAINER_END (it's the FIRST ContainerEnd
        // after the TouchDownModifier+action group) and verify Background follows it.
        val touchIdx = ops.indexOfFirst { it is TouchDownModifier }
        val containerEndOpcode = Operations.CONTAINER_END
        val scopeEndIdx = ops.indexOfFirst { it.opcode == containerEndOpcode }
        val bgIdx = ops.indexOfFirst {
            it is com.tneff.kmpremotecompose.remote.core.operations.layout.BackgroundModifier
        }
        assertTrue(touchIdx >= 0, "TouchDownModifier emitted")
        assertTrue(scopeEndIdx > touchIdx, "ContainerEnd closes the action scope")
        assertTrue(bgIdx > scopeEndIdx, "BackgroundModifier emits AFTER the action-scope ContainerEnd")
    }

    /**
     * W12 sanity-pin: after REM-146 fix, the first user-allocated id in a map-form non-empty-desc
     * doc is 42. Touch corpus has DATA_INT id=42 — verify the procedural DSL allocates the same.
     */
    @Test
    fun firstUserIdIs42_inMapForm_evenWithNonEmptyContentDescription() {
        val bytes = document(
            width = 500, height = 500,
            profile = androidxExperimental,
            contentDescription = "DemoModifierOnTouchDown",
        ) {
            val id = ids.nextId()
            add(IntegerConstant(id, 0))
        }
        val ops = DocumentReader.inflate(bytes).operations
        val intOp = ops.first { it is IntegerConstant } as IntegerConstant
        assertEquals(42, intOp.id, "REM-146-post: map-form + non-empty desc → first user id = 42")
    }

    // -------------------------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------------------------

    /**
     * Extract the 3-op "touch-modifier group" bytes (MODIFIER_TOUCH_* + action body +
     * trailing CONTAINER_END) from a document. Locates the touch opcode and slices through the
     * NEXT CONTAINER_END (inclusive). The middle action body is opaque to this helper — what
     * matters is that the byte range starts at MODIFIER_TOUCH_X and ends after the
     * CONTAINER_END that closes its ListActions scope.
     */
    private fun extractTouchModifierGroup(docBytes: ByteArray, touchOpcode: Int): ByteArray {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        val touchIdx = spans.indexOfFirst { it.opcode == touchOpcode }
        require(touchIdx >= 0) { "Touch opcode $touchOpcode not found in document" }
        // Walk forward to the next CONTAINER_END (closes the ListActions scope opened by the touch op).
        val endIdx = spans.subList(touchIdx + 1, spans.size)
            .indexOfFirst { it.opcode == Operations.CONTAINER_END }
        require(endIdx >= 0) { "No CONTAINER_END after touch opcode $touchOpcode" }
        val containerEndSpan = spans[touchIdx + 1 + endIdx]
        return docBytes.copyOfRange(spans[touchIdx].byteStart, containerEndSpan.byteEnd)
    }
}
