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
package com.tneff.kmpremotecompose.creation.compose

import androidx.compose.ui.graphics.Color
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.creation.LayoutModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * REM-128 S3a — structural equality / hashCode tests for [RemoteModifier]. TechSpec §2 Q4 lock:
 * `RemoteModifier` holds a list of **data-carrying elements** so Compose
 * `ComposeNode { update { set(modifier) } }` change-detection can skip unchanged recompositions —
 * a lambda-only buffer would compare reference-only and always trigger change-detection,
 * defeating Compose's stability optimisations and painting S4-streaming into a corner.
 */
class RemoteModifierEqualsTest {

    @Test
    fun sameChain_isEqual_andSameHashCode() {
        val a = RemoteModifier.width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 50f)
        val b = RemoteModifier.width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 50f)
        assertEquals(a, b, "RemoteModifier with identical element chain must compare equal")
        assertEquals(a.hashCode(), b.hashCode(), "Equal RemoteModifiers must have equal hashCode")
    }

    @Test
    fun differentValue_isNotEqual() {
        val a = RemoteModifier.width(DimensionType.EXACT, 100f)
        val b = RemoteModifier.width(DimensionType.EXACT, 101f)
        assertNotEquals(a, b, "Different value in same element type must not compare equal")
    }

    @Test
    fun differentOrder_isNotEqual() {
        // Element order matters (= emission order matters for byte-equality).
        val widthFirst = RemoteModifier.width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 50f)
        val heightFirst = RemoteModifier.height(DimensionType.EXACT, 50f).width(DimensionType.EXACT, 100f)
        assertNotEquals(
            widthFirst,
            heightFirst,
            "Element order matters — different chain order must not compare equal (would imply " +
                "different emission order = different bytes).",
        )
    }

    @Test
    fun emptyModifier_equalsCompanion() {
        val empty = RemoteModifier
        val explicitly = RemoteModifier
        assertEquals(empty, explicitly, "Companion-as-empty is the canonical empty RemoteModifier")
    }

    @Test
    fun backgroundColorOverload_equalsBackgroundIntOverload() {
        // Q1 structural guard: Color.toArgb() → same Int → same BackgroundElement floats → equal.
        val viaInt = RemoteModifier.background(color = 0xffff0000.toInt())
        val viaColor = RemoteModifier.background(Color(0xffff0000.toInt()))
        assertEquals(
            viaInt,
            viaColor,
            "background(Color) must compare equal to background(Int) for the same ARGB value — " +
                "Q1 round-trip guard.",
        )
        assertEquals(viaInt.hashCode(), viaColor.hashCode())
    }

    @Test
    fun chainImmutability_originalUnchanged() {
        val base = RemoteModifier.width(DimensionType.EXACT, 100f)
        val extended = base.height(DimensionType.EXACT, 50f)
        // base must NOT have height appended (immutable chain) — proves the .height() call
        // returned a NEW RemoteModifier instead of mutating the shared one.
        assertNotEquals(base, extended, "Chain calls return a new RemoteModifier (immutable).")
    }

    // -------------------------------------------------------------------------------------------
    // REM-130 T2 — equals / hashCode for the 7 new modifier elements
    // -------------------------------------------------------------------------------------------

    @Test
    fun padding_sameValues_equal_differentValues_notEqual() {
        val a = RemoteModifier.padding(20f)
        val b = RemoteModifier.padding(start = 20f, top = 20f, end = 20f, bottom = 20f)
        // Both forms must produce the same PaddingElement (all-sides constructor == per-side with
        // identical floats) — caller convenience must not split element identity.
        assertEquals(a, b, "padding(all) must produce the same element as padding(s,t,e,b) with equal floats")
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, RemoteModifier.padding(start = 10f, top = 20f, end = 20f, bottom = 20f))
    }

    @Test
    fun clipRect_isSingleton_equalsItself() {
        val a = RemoteModifier.clipRect()
        val b = RemoteModifier.clipRect()
        assertEquals(a, b, "clipRect() has no params — equality is unconditional")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun roundedClipRect_perCornerValues_compared() {
        val a = RemoteModifier.roundedClipRect(40f, 40f, 40f, 40f)
        val b = RemoteModifier.roundedClipRect(40f, 40f, 40f, 40f)
        assertEquals(a, b)
        assertNotEquals(a, RemoteModifier.roundedClipRect(40f, 40f, 41f, 40f), "single-corner change breaks equality")
    }

    @Test
    fun border_intOverload_equalsColorOverload_forSameArgb() {
        val viaInt = RemoteModifier.border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xffff0000.toInt())
        val viaColor = RemoteModifier.border(borderWidth = 4f, roundedCorner = 0.1f, color = Color(0xffff0000.toInt()))
        // Q1 sRGB-round-trip guard for border (parallel to background's Q1 guard) — Color.toArgb()
        // must yield byte-identical BorderElement state.
        assertEquals(viaInt, viaColor, "border(Color) must equal border(Int) for the same ARGB")
        assertEquals(viaInt.hashCode(), viaColor.hashCode())
    }

    @Test
    fun border_useLegacyFlag_breaksEquality() {
        val legacy = RemoteModifier.border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xffff0000.toInt(), useLegacy = true)
        val nonLegacy = RemoteModifier.border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xffff0000.toInt(), useLegacy = false)
        // The useLegacy=false form writes reserve1=1 (non-legacy border drawing) — byte-different
        // from useLegacy=true. Equality must reflect that wire-level difference.
        assertNotEquals(legacy, nonLegacy)
    }

    @Test
    fun visibility_differentIdRefs_notEqual() {
        assertNotEquals(
            RemoteModifier.visibility(valueId = 42),
            RemoteModifier.visibility(valueId = 43),
            "Different id-refs target different sources → different wire bytes → different element",
        )
    }

    @Test
    fun scroll_directionsAreDistinctElements() {
        assertNotEquals(
            RemoteModifier.scroll(LayoutModifier.SCROLL_HORIZONTAL),
            RemoteModifier.scroll(LayoutModifier.SCROLL_VERTICAL),
            "MODIFIER_SCROLL direction is part of element state — horizontal != vertical",
        )
    }

    @Test
    fun alignBy_lineAndFlags_bothPartOfEquality() {
        val a = RemoteModifier.alignBy(line = 12.5f, flags = 0)
        val b = RemoteModifier.alignBy(line = 12.5f, flags = 0)
        assertEquals(a, b)
        assertNotEquals(a, RemoteModifier.alignBy(line = 12.5f, flags = 1), "flags is part of equality")
        assertNotEquals(a, RemoteModifier.alignBy(line = 13.0f, flags = 0), "line is part of equality")
    }

    @Test
    fun elementOrder_matters_acrossT2_padding_then_border_vs_reverse() {
        // Modifier emission order = wire order = byte-equality-sensitive (REM-130 mirrors S3a
        // order-matters lock). Padding-then-border emits bytes [PADDING][BORDER]; the reverse
        // emits [BORDER][PADDING] — those are NOT the same wire bytes.
        val paddingFirst = RemoteModifier
            .padding(20f)
            .border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xffff0000.toInt())
        val borderFirst = RemoteModifier
            .border(borderWidth = 4f, roundedCorner = 0.1f, color = 0xffff0000.toInt())
            .padding(20f)
        assertNotEquals(
            paddingFirst,
            borderFirst,
            "Chain order is emit order is wire order — two orders must NOT compare equal.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // REM-141 T3 — Slot-element equality (Q4-lock for slot-based modifier overloads)
    // -------------------------------------------------------------------------------------------

    @Test
    fun visibilityFromSlot_sameSlotInstance_isEqual() {
        // Slot identity drives element equality — same slot instance in both modifiers must
        // produce equal elements so Compose `update { set(modifier) }` change-detection can
        // skip identical compositions (Q4 lock, REM-128 §2 Q4).
        val slot = RemoteFloatSlot()
        val a = RemoteModifier.visibility(slot)
        val b = RemoteModifier.visibility(slot)
        assertEquals(a, b, "Same slot reference → equal VisibilityFromSlotElement → equal RemoteModifier")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun visibilityFromSlot_differentSlotInstances_notEqual() {
        // Even if both slots are "empty" (id=-1), they are distinct instances → different elements.
        // This is correct: at render time they'll resolve to different ids (different primitive
        // composables emitted at different tree positions), so wire bytes will differ.
        val a = RemoteModifier.visibility(RemoteFloatSlot())
        val b = RemoteModifier.visibility(RemoteFloatSlot())
        assertNotEquals(a, b, "Different slot instances → different elements (distinct wire byte sources)")
    }

    @Test
    fun visibilityFromSlot_isDistinctFromIntForm() {
        // The Int-form visibility(valueId=42) and the slot-form visibility(slot) are DIFFERENT
        // element types — they can't be equal even if the slot eventually resolves to id=42,
        // because identity is structural (the element type itself differs).
        val slot = RemoteFloatSlot()
        val viaSlot = RemoteModifier.visibility(slot)
        val viaInt = RemoteModifier.visibility(valueId = 42)
        assertNotEquals(viaSlot, viaInt, "Slot-form and int-form visibility are structurally distinct elements")
    }

    @Test
    fun borderColorRefFromSlot_sameSlotAndParams_isEqual() {
        val slot = RemoteColorSlot()
        val a = RemoteModifier.border(borderWidth = 5f, roundedCorner = 1f, colorIdSlot = slot, shape = 1)
        val b = RemoteModifier.border(borderWidth = 5f, roundedCorner = 1f, colorIdSlot = slot, shape = 1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun borderColorRefFromSlot_differentParams_notEqual() {
        val slot = RemoteColorSlot()
        val a = RemoteModifier.border(5f, 1f, slot, shape = 1)
        val b = RemoteModifier.border(5f, 1f, slot, shape = 2)
        assertNotEquals(a, b, "shape is part of equality")
        val c = RemoteModifier.border(5f, 1f, slot, shape = 1, useLegacy = true)
        val d = RemoteModifier.border(5f, 1f, slot, shape = 1, useLegacy = false)
        assertNotEquals(c, d, "useLegacy (=reserve1 flip) is part of equality")
    }

    // -------------------------------------------------------------------------------------------
    // REM-144 S3 — backgroundColorRef (raw-int + slot) equality
    // -------------------------------------------------------------------------------------------

    @Test
    fun backgroundColorRef_rawInt_sameIdAndShape_isEqual() {
        val a = RemoteModifier.backgroundColorRef(colorId = 1, shape = 0)
        val b = RemoteModifier.backgroundColorRef(colorId = 1, shape = 0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun backgroundColorRef_rawInt_differentColorId_notEqual() {
        assertNotEquals(
            RemoteModifier.backgroundColorRef(colorId = 1),
            RemoteModifier.backgroundColorRef(colorId = 2),
            "Different system colorIds → different wire bytes → different elements",
        )
    }

    @Test
    fun backgroundColorRef_isDistinctFromStaticBackground() {
        // backgroundColorRef(Int) (flags=2 / colorId / rgba=0) is structurally distinct from
        // background(Int) (flags=0 / colorId=0 / decomposed rgba) — element types differ even
        // when the int values happen to be equal.
        val viaColorRef = RemoteModifier.backgroundColorRef(colorId = 1)
        val viaStatic = RemoteModifier.background(color = 1)
        assertNotEquals(viaColorRef, viaStatic, "backgroundColorRef and static background are structurally distinct elements")
    }

    @Test
    fun backgroundColorRef_slotForm_sameSlot_isEqual() {
        val slot = RemoteColorSlot()
        val a = RemoteModifier.background(colorIdSlot = slot)
        val b = RemoteModifier.background(colorIdSlot = slot)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun backgroundColorRef_slotForm_differentSlots_notEqual() {
        assertNotEquals(
            RemoteModifier.background(colorIdSlot = RemoteColorSlot()),
            RemoteModifier.background(colorIdSlot = RemoteColorSlot()),
            "Different slot instances → different elements (distinct wire byte sources)",
        )
    }

    @Test
    fun backgroundColorRef_slotForm_isDistinctFromRawIntForm() {
        val slot = RemoteColorSlot()
        val viaSlot = RemoteModifier.background(colorIdSlot = slot)
        val viaInt = RemoteModifier.backgroundColorRef(colorId = 42)
        assertNotEquals(viaSlot, viaInt, "Slot-form and raw-int form are structurally distinct elements")
    }

    // -------------------------------------------------------------------------------------------
    // REM-145 S3 — Touch event modifier (onTouchDown/Up/Cancel) Q4 equality
    // -------------------------------------------------------------------------------------------

    @Test
    fun onTouchDown_sameActions_isEqual() {
        val a = RemoteModifier.onTouchDown(valueIntegerChange(42, 1), valueIntegerChange(43, 2))
        val b = RemoteModifier.onTouchDown(valueIntegerChange(42, 1), valueIntegerChange(43, 2))
        assertEquals(a, b, "Same action list → equal OnTouchDownElement → equal RemoteModifier")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun onTouchDown_differentActionContent_notEqual() {
        val a = RemoteModifier.onTouchDown(valueIntegerChange(42, 1))
        val b = RemoteModifier.onTouchDown(valueIntegerChange(42, 2))
        assertNotEquals(a, b, "Different action.value → different wire bytes → different element")
    }

    @Test
    fun onTouchDown_differentActionOrder_notEqual() {
        // Action order = wire byte order. Two orderings emit different bytes → must NOT compare equal.
        val a = RemoteModifier.onTouchDown(valueIntegerChange(42, 1), valueIntegerChange(43, 2))
        val b = RemoteModifier.onTouchDown(valueIntegerChange(43, 2), valueIntegerChange(42, 1))
        assertNotEquals(a, b, "Action order is emit order is wire order — distinct elements")
    }

    @Test
    fun onTouchDown_vs_onTouchUp_vs_onTouchCancel_areDistinct() {
        // Three different opcodes (0xdb/0xdc/0xe1) → three different element types even with the
        // same actions. The data-class identity carries the opcode meaning.
        val down = RemoteModifier.onTouchDown(valueIntegerChange(42, 1))
        val up = RemoteModifier.onTouchUp(valueIntegerChange(42, 1))
        val cancel = RemoteModifier.onTouchCancel(valueIntegerChange(42, 1))
        assertNotEquals(down, up)
        assertNotEquals(down, cancel)
        assertNotEquals(up, cancel)
    }

    @Test
    fun valueIntegerChangeActionElement_dataClassEquality() {
        // The ActionElement itself follows the same data-class-equality contract as the
        // RemoteModifierElement sealed hierarchy.
        val a: ActionElement = valueIntegerChange(42, 1)
        val b: ActionElement = valueIntegerChange(42, 1)
        val c: ActionElement = valueIntegerChange(42, 2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
