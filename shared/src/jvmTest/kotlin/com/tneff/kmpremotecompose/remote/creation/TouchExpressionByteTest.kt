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
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-145 S2 — Byte-anchor for the standalone `touchExpression(...)` helper.
 *
 * **Anchor strategy (sub-span vs corpus):** REM-96's `scroll()` group already emits a
 * `TOUCH_EXPRESSION` op at virgin-allocator id=42 with the exact field set that
 * `scroll_fullByteEquality_vsCorpusFixture_verticalScroll` corpus-anchors against
 * `c_modifier_vertical_scroll.rc`. The standalone `touchExpression(...)` helper allocates id=42
 * at virgin pool too and emits the same wire bytes — so calling it with the scroll-V's exact
 * params produces a 49-byte sub-span byte-identical to `c_modifier_vertical_scroll.rc`'s
 * `TOUCH_EXPRESSION` op.
 *
 * **W14 NaN-bit-preservation:** the `exp` `FloatArray` carries NaN-encoded variable refs (e.g.
 * `asNan(ID_TOUCH_POS_Y) = 0xFF80000E`) and NaN-encoded operator ids (e.g. `MUL = 0xFFB10003`)
 * — `FloatArray` stores raw IEEE 754, no repack risk in our path (W2-pattern from REM-141).
 * Belt-and-suspenders: the test asserts the EMIT bytes via `assertContentEquals` AND inflates
 * the doc to confirm the round-tripped `TouchExpression.exp` carries the same raw bits.
 */
class TouchExpressionByteTest {

    /**
     * Stage-2 sub-span anchor: build a minimal-doc that emits ONE `touchExpression(...)` at the
     * virgin allocator (so the resulting id = 42), with the exact field set that REM-96
     * `scroll_fullByteEquality_vsCorpusFixture_verticalScroll` pins. Extract the 49-byte
     * TOUCH_EXPRESSION span. Assert byte-equality against the same TOUCH_EXPRESSION span
     * extracted from `c_modifier_vertical_scroll.rc`.
     *
     * The corpus' TouchExpression at the scroll group is at id=42 (positionId reused) with:
     *   value=0f, min=0f, max=asNan(43), velocityId=0f, touchEffects=3,
     *   exp = [asNan(ID_TOUCH_POS_Y=14), -1f, MUL=asNan(0x310003)],
     *   stopLogic=0, stops=[], easing=[].
     */
    @Test
    fun touchExpression_subSpan_matchesCorpusFixtureScrollVertical() {
        val emitted = document(width = 200, height = 200) {
            touchExpression(
                value = 0f,
                min = 0f,
                max = WireTypes.asNan(43),
                velocityId = 0f,
                touchEffects = 3,
                exp = floatArrayOf(
                    WireTypes.asNan(LayoutModifier.ID_TOUCH_POS_Y),
                    -1f,
                    RcExpression.MUL,
                ),
                stopLogic = 0,
                stops = floatArrayOf(),
                easing = floatArrayOf(),
            )
        }
        val emittedSpan = extractFirstTouchExpressionBytes(emitted)
        assertEquals(49, emittedSpan.size, "TOUCH_EXPRESSION wire size = 49 B (verified vs REM-96 scroll-V anchor)")

        val corpus = RcCorpus.readFixture("corpus/c_modifier_vertical_scroll.rc")
        val corpusSpan = extractFirstTouchExpressionBytes(corpus)
        assertTrue(
            corpusSpan.contentEquals(emittedSpan),
            "Standalone touchExpression(...) bytes must byte-match c_modifier_vertical_scroll.rc " +
                "TOUCH_EXPRESSION sub-span — proves the helper emits identical wire shape as " +
                "scroll()'s embedded TouchExpression group (corpus-anchored via REM-96).",
        )
    }

    /**
     * W14 belt-and-suspenders: NaN raw-bit preservation through the `exp` FloatArray. The
     * standalone helper passes the array directly to TouchExpression; assert that the inflated
     * op carries identical raw NaN bits for the variable-ref and operator-id slots — defends
     * against signaling-NaN repack in any future toolchain or path change.
     */
    @Test
    fun touchExpression_preservesNaNRawBitsInExpArray() {
        val emitted = document(width = 100, height = 100) {
            touchExpression(
                value = 0f, min = 0f, max = WireTypes.asNan(43),
                velocityId = 0f, touchEffects = 3,
                exp = floatArrayOf(
                    WireTypes.asNan(LayoutModifier.ID_TOUCH_POS_X),  // asNan(13) = 0xFF80000D
                    -1f,
                    RcExpression.MUL,                                  // asNan(0x310003) = 0xFFB10003
                ),
            )
        }
        val te = DocumentReader.inflate(emitted).operations
            .first { it is TouchExpression } as TouchExpression
        assertEquals(
            WireTypes.asNan(LayoutModifier.ID_TOUCH_POS_X).toRawBits(),
            te.exp[0].toRawBits(),
            "exp[0] = FLOAT_TOUCH_POS_X (NaN-encoded) preserved bit-exactly",
        )
        assertEquals((-1f).toRawBits(), te.exp[1].toRawBits(), "exp[1] = -1f literal preserved")
        assertEquals(
            RcExpression.MUL.toRawBits(), te.exp[2].toRawBits(),
            "exp[2] = MUL operator id (NaN-encoded) preserved bit-exactly",
        )
        // The id-bearing fields (max=asNan(43)) round-trip identically too.
        assertEquals(WireTypes.asNan(43).toRawBits(), te.max.toRawBits(), "max = asNan(43) raw bits preserved")
    }

    /**
     * Sanity check: the helper allocates a fresh region-0 id and returns its NaN-encoded form
     * (mirrors floatExpression's chainable shape). At virgin allocator state, id = 42.
     */
    @Test
    fun touchExpression_returnsNaNEncodedIdOfAllocatedSlot() {
        var returnedNan = 0f
        document(width = 100, height = 100) {
            returnedNan = touchExpression(
                value = 0f, min = 0f, max = 0f,
                exp = floatArrayOf(0f),
            )
        }
        assertEquals(
            WireTypes.asNan(42).toRawBits(), returnedNan.toRawBits(),
            "touchExpression() returns NaN-encoded id of the freshly-allocated slot (id=42 at virgin pool)",
        )
    }

    /**
     * Find the byte range of the first TOUCH_EXPRESSION op in [docBytes]. Use the
     * `inflateWithTrace` byte-map to look up the span — opaque to op order so the helper works
     * for both standalone (id=42 first) and embedded (scroll-group) cases.
     */
    private fun extractFirstTouchExpressionBytes(docBytes: ByteArray): ByteArray {
        val spans = DocumentReader.inflateWithTrace(docBytes).second
        val span = spans.first { it.opcode == Operations.TOUCH_EXPRESSION }
        return docBytes.copyOfRange(span.byteStart, span.byteEnd)
    }
}
