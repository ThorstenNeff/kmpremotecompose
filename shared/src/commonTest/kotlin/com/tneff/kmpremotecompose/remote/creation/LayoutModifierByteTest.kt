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

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.layout.AlignByModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.BackgroundModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.BorderModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClickModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClipRectModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightInModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.PaddingModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.RoundedClipRectModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ScrollModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.VisibilityModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthInModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ZIndexModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-96 (FC-Layout-Container) — 14 modifier byte-anchors.
 *
 * No corpus fixture exercises border/padding/scroll/etc. on their own (the 3 `c_*.rc` files
 * cover Box/Background/Width/Column/Text only — REM-92-Lektion: corpus is necessary, not
 * sufficient). Per-modifier byte-anchors below stand in for the missing fixtures, each pinning
 * the wire-int / float / decomposition against a verified upstream caller-site:
 *
 *  - `addModifierBackground(int color, int shape)` decomposes ARGB → 0/0/0/0/r/g/b/a/shape
 *    (`RemoteComposeBuffer.java:1740-1745`)
 *  - `addModifierBorder` `useLegacy=true` writes `reserve1=0`, `useLegacy=false` writes
 *    `reserve1=1` (`RemoteComposeBuffer.java:1808-1815`)
 *  - `addModifierAlignBy(line)` writes `flags=0` (`RemoteComposeBuffer.java:1782-1784`)
 *  - `DimensionType.ordinal` is the int wire value
 *  - `ScrollModifier` direction `0=VERTICAL, 1=HORIZONTAL` (`ScrollModifierOperation.java:245`)
 */
class LayoutModifierByteTest {

    private fun emit(build: LayoutModifier.() -> Unit): List<Any> {
        val bytes = document(width = 100, height = 100) {
            box(modifier = LayoutModifier().apply(build)) {}
        }
        return DocumentReader.inflate(bytes).operations
    }

    @Test
    fun width_carriesDimensionTypeOrdinal_andValue() {
        val mod = emit { width(DimensionType.FILL) }
        val w = mod.first { it is WidthModifier } as WidthModifier
        assertEquals(DimensionType.FILL, w.type)
        assertEquals(0f, w.value)

        val mod2 = emit { width(DimensionType.EXACT_DP, 120f) }
        val w2 = mod2.first { it is WidthModifier } as WidthModifier
        assertEquals(DimensionType.EXACT_DP, w2.type)
        assertEquals(120f, w2.value)
    }

    @Test
    fun height_carriesDimensionTypeOrdinal_andValue() {
        val mod = emit { height(DimensionType.WRAP) }
        val h = mod.first { it is HeightModifier } as HeightModifier
        assertEquals(DimensionType.WRAP, h.type)
        assertEquals(0f, h.value)
    }

    @Test
    fun widthIn_heightIn_carryMinMaxFloats() {
        val mod = emit { widthIn(min = 10f, max = 200f); heightIn(min = -1f, max = 300f) }
        val wi = mod.first { it is WidthInModifier } as WidthInModifier
        val hi = mod.first { it is HeightInModifier } as HeightInModifier
        assertEquals(10f, wi.min); assertEquals(200f, wi.max)
        assertEquals(-1f, hi.min, "-1f sentinel = unconstrained (mirror upstream comment)")
        assertEquals(300f, hi.max)
    }

    @Test
    fun padding_uniform_andPerSide() {
        val mod = emit { padding(16f) }
        val p = mod.first { it is PaddingModifier } as PaddingModifier
        assertEquals(16f, p.left); assertEquals(16f, p.top)
        assertEquals(16f, p.right); assertEquals(16f, p.bottom)

        val mod2 = emit { padding(start = 4f, top = 8f, end = 12f, bottom = 16f) }
        val p2 = mod2.first { it is PaddingModifier } as PaddingModifier
        assertEquals(4f, p2.left); assertEquals(8f, p2.top)
        assertEquals(12f, p2.right); assertEquals(16f, p2.bottom)
    }

    @Test
    fun background_intColor_decomposesArgb_andZerosFlagsAndColorIdAndReserves() {
        // Upstream addModifierBackground(int color, int shape): BackgroundModifierOperation.apply(
        //   buffer, 0, 0, 0, 0, r, g, b, a, shape). RemoteComposeBuffer.java:1740-1745.
        val mod = emit { background(color = 0x80FF8040.toInt()) }
        val bg = mod.first { it is BackgroundModifier } as BackgroundModifier
        assertEquals(0, bg.flags); assertEquals(0, bg.colorId)
        assertEquals(0, bg.reserve1); assertEquals(0, bg.reserve2)
        // ARGB 0x80FF8040 → a=128/255, r=255/255, g=128/255, b=64/255.
        assertEquals(128f / 255f, bg.a)
        assertEquals(255f / 255f, bg.r)
        assertEquals(128f / 255f, bg.g)
        assertEquals(64f / 255f, bg.b)
        assertEquals(0, bg.shapeType, "shape defaults to 0 (RECT)")
    }

    @Test
    fun background_floatChannels_preservesRawBitsForNaNRefs() {
        // Float-channel form is the entry-point for NaN-encoded id refs in r/g/b/a.
        val mod = emit { background(r = 0.25f, g = 0.5f, b = 0.75f, a = 1f, shape = 1) }
        val bg = mod.first { it is BackgroundModifier } as BackgroundModifier
        assertEquals(0, bg.flags); assertEquals(0, bg.colorId)
        assertEquals(0.25f, bg.r); assertEquals(0.5f, bg.g)
        assertEquals(0.75f, bg.b); assertEquals(1f, bg.a)
        assertEquals(1, bg.shapeType)
    }

    @Test
    fun border_useLegacyTrue_writes_reserve1Zero() {
        val mod = emit { border(borderWidth = 2f, roundedCorner = 8f, color = 0xFF112233.toInt()) }
        val b = mod.first { it is BorderModifier } as BorderModifier
        assertEquals(0, b.flags); assertEquals(0, b.colorId)
        assertEquals(0, b.reserve1, "default useLegacy=true → reserve1=0")
        assertEquals(0, b.reserve2)
        assertEquals(2f, b.borderWidth); assertEquals(8f, b.roundedCorner)
    }

    @Test
    fun border_useLegacyFalse_writes_reserve1One() {
        // The single bit that flips wire bytes — pin both branches.
        val mod = emit {
            border(borderWidth = 2f, roundedCorner = 8f, color = 0xFF000000.toInt(), useLegacy = false)
        }
        val b = mod.first { it is BorderModifier } as BorderModifier
        assertEquals(1, b.reserve1, "useLegacy=false → reserve1=1 (per RemoteComposeBuffer.java:1815)")
    }

    @Test
    fun borderColorRef_emitsFlags2_andStoresColorIdAndZeroRgba() {
        // REM-141 S1 — dynamic-color form (flags=2 / colorId / rgba=0). Pin the wire shape
        // shape independent of any corpus, then anchor against c_modifier_dynamic_border.rc
        // below. Mirrors upstream addModifierBorder(borderWidth, roundedCorner, colorId, shape):
        // BorderModifierOperation.apply(buffer, 2, colorId, reserve1, 0, bw, rc, 0, 0, 0, 0, shape).
        val mod = emit {
            borderColorRef(borderWidth = 5f, roundedCorner = 1f, colorId = 42, shape = 1)
        }
        val b = mod.first { it is BorderModifier } as BorderModifier
        assertEquals(2, b.flags, "borderColorRef → flags=2 (resolve-by-colorId switch)")
        assertEquals(42, b.colorId)
        assertEquals(0, b.reserve1, "default useLegacy=true → reserve1=0")
        assertEquals(0, b.reserve2)
        assertEquals(5f, b.borderWidth); assertEquals(1f, b.roundedCorner)
        assertEquals(0f, b.r); assertEquals(0f, b.g); assertEquals(0f, b.b); assertEquals(0f, b.a)
        assertEquals(1, b.shapeType)
    }

    @Test
    fun backgroundColorRef_emitsFlags2_andStoresColorIdAndZeroRgba() {
        // REM-144 S1 — dynamic-color form (flags=2 / colorId / rgba=0). Mirror upstream
        // addModifierBackground(colorId, shape) overload:
        // BackgroundModifierOperation.apply(buffer, 2, colorId, 0, 0, 0, 0, 0, 0, shape).
        val mod = emit { backgroundColorRef(colorId = 1, shape = 0) }
        val b = mod.first { it is BackgroundModifier } as BackgroundModifier
        assertEquals(2, b.flags, "backgroundColorRef → flags=2 (resolve-by-colorId switch)")
        assertEquals(1, b.colorId)
        assertEquals(0, b.reserve1)
        assertEquals(0, b.reserve2)
        assertEquals(0f, b.r); assertEquals(0f, b.g); assertEquals(0f, b.b); assertEquals(0f, b.a)
        assertEquals(0, b.shapeType)
    }

    @Test
    @IgnoreOnWasm
    fun backgroundColorRef_fullByteEquality_vsCorpusFixture_backgroundId() {
        // REM-144 S1 — Stage-2 sub-span byte-anchor: the MODIFIER_BACKGROUND 37-byte op on
        // `c_modifier_background_id.rc` carries `flags=2, colorId=1` (system colour id, NOT a
        // region-0 ColorExpression — empirical decode finding, REM-144 scoping). Build a
        // synthetic procedural-DSL document carrying the same `backgroundColorRef(colorId=1)`,
        // extract the MODIFIER_BACKGROUND op span, assert byte-equal against the corpus span.
        // **Direct corpus byte-anchor** — the op is ID-decoupled at the wire level (colorId is a
        // plain int the caller writes; no allocator coupling at the op itself).
        val corpus = com.tneff.kmpremotecompose.conformance.RcCorpus
            .readFixture("corpus/c_modifier_background_id.rc")
        val corpusOps = DocumentReader.inflateWithTrace(corpus).second
        val bgOpcode = com.tneff.kmpremotecompose.remote.core.operations.Operations.MODIFIER_BACKGROUND
        val corpusBgSpan = corpusOps.first { it.opcode == bgOpcode }
        // 1 opcode + 4 ints(flags, colorId, res1, res2) + 4 floats(r, g, b, a) + 1 int(shapeType) = 37 B.
        assertEquals(37, corpusBgSpan.byteEnd - corpusBgSpan.byteStart, "MODIFIER_BACKGROUND wire = 37 B")
        val corpusBgBytes = corpus.copyOfRange(corpusBgSpan.byteStart, corpusBgSpan.byteEnd)

        val emitted = document(width = 100, height = 100) {
            box(modifier = LayoutModifier().backgroundColorRef(colorId = 1, shape = 0)) {}
        }
        val emittedOps = DocumentReader.inflateWithTrace(emitted).second
        val emittedBgSpan = emittedOps.first { it.opcode == bgOpcode }
        val emittedBgBytes = emitted.copyOfRange(emittedBgSpan.byteStart, emittedBgSpan.byteEnd)
        assertTrue(
            corpusBgBytes.contentEquals(emittedBgBytes),
            "MODIFIER_BACKGROUND 37-byte op sub-span emitted by LayoutModifier.backgroundColorRef " +
                "must byte-match c_modifier_background_id.rc — direct §2 corpus anchor for the " +
                "dynamic-color background wire shape (parallel to REM-141 S1 borderColorRef).",
        )
    }

    @Test
    @IgnoreOnWasm
    fun borderColorRef_fullByteEquality_vsCorpusFixture_dynamicBorder() {
        // REM-141 S1 — Stage-2 sub-span byte-anchor: the MODIFIER_BORDER 45-byte op on
        // `c_modifier_dynamic_border.rc` is ID-decoupled at the op level (`colorId` is a plain
        // int field — caller passes the value, no allocator coupling). Build a synthetic
        // procedural-DSL document carrying the same `borderColorRef(5, 1, 42, shape=1)`, extract
        // the MODIFIER_BORDER op span, assert byte-equal against the corpus span. **Direct
        // corpus byte-anchor** — closes assist's REM-130 §2-completeness concern transitively
        // for the dynamic-border branch (proves the new borderColorRef helper emits exactly
        // the upstream wire shape).
        val corpus = com.tneff.kmpremotecompose.conformance.RcCorpus
            .readFixture("corpus/c_modifier_dynamic_border.rc")
        val corpusOps = DocumentReader.inflateWithTrace(corpus).second
        val borderOpcode = com.tneff.kmpremotecompose.remote.core.operations.Operations.MODIFIER_BORDER
        val corpusBorderSpan = corpusOps.first { it.opcode == borderOpcode }
        // 1 opcode + 4 ints(flags,colorId,res1,res2) + 6 floats(bw,rc,r,g,b,a) + 1 int(shapeType) = 45 B.
        assertEquals(45, corpusBorderSpan.byteEnd - corpusBorderSpan.byteStart, "MODIFIER_BORDER wire = 45 B")
        val corpusBorderBytes = corpus.copyOfRange(corpusBorderSpan.byteStart, corpusBorderSpan.byteEnd)

        // Build a synthetic doc that emits a MODIFIER_BORDER matching the corpus payload exactly:
        // borderWidth=5, roundedCorner=1, colorId=42, shape=1, useLegacy=true (→ reserve1=0).
        // The surrounding container is irrelevant for the sub-span — borderColorRef sets the op's
        // own fields, independent of any prior ColorExpression (no allocator dependency at the
        // op level — the ColorExpression that ultimately defines colorId=42 lives elsewhere on
        // the document and is anchored separately in REM-141 S2/S3).
        val emitted = document(width = 100, height = 100) {
            box(modifier = LayoutModifier().borderColorRef(
                borderWidth = 5f, roundedCorner = 1f, colorId = 42, shape = 1,
            )) {}
        }
        val emittedOps = DocumentReader.inflateWithTrace(emitted).second
        val emittedBorderSpan = emittedOps.first { it.opcode == borderOpcode }
        val emittedBorderBytes = emitted.copyOfRange(emittedBorderSpan.byteStart, emittedBorderSpan.byteEnd)
        assertTrue(
            corpusBorderBytes.contentEquals(emittedBorderBytes),
            "MODIFIER_BORDER 45-byte op sub-span emitted by LayoutModifier.borderColorRef must " +
                "byte-match c_modifier_dynamic_border.rc — direct §2 corpus anchor for the " +
                "dynamic-color border wire shape.",
        )
    }

    @Test
    fun clipRect_emitsZeroFieldOp() {
        val mod = emit { clipRect() }
        assertEquals(1, mod.count { it is ClipRectModifier })
    }

    @Test
    fun roundedClipRect_perCornerRadii() {
        val mod = emit { roundedClipRect(topStart = 4f, topEnd = 8f, bottomStart = 12f, bottomEnd = 16f) }
        val r = mod.first { it is RoundedClipRectModifier } as RoundedClipRectModifier
        assertEquals(4f, r.topStart); assertEquals(8f, r.topEnd)
        assertEquals(12f, r.bottomStart); assertEquals(16f, r.bottomEnd)
    }

    @Test
    fun visibility_carriesValueId_int() {
        val mod = emit { visibility(valueId = 99) }
        val v = mod.first { it is VisibilityModifier } as VisibilityModifier
        assertEquals(99, v.valueId)
    }

    @Test
    fun zIndex_carriesFloatValue() {
        val mod = emit { zIndex(value = 3.5f) }
        val z = mod.first { it is ZIndexModifier } as ZIndexModifier
        assertEquals(3.5f, z.value)
    }

    @Test
    fun click_emitsZeroFieldOp() {
        val mod = emit { click() }
        assertEquals(1, mod.count { it is ClickModifier })
    }

    @Test
    fun scroll_directionConstants_matchUpstream_0Vertical_1Horizontal() {
        // Off-by-one risk pinned: upstream ScrollModifierOperation.java:245 says
        // "0=VERTICAL, 1=HORIZONTAL". DO NOT swap these.
        assertEquals(0, LayoutModifier.SCROLL_VERTICAL)
        assertEquals(1, LayoutModifier.SCROLL_HORIZONTAL)
    }

    @Test
    fun scroll_emits_fullUpstreamGroup_DataFloat_ScrollModifier_TouchExpression_ContainerEnd() {
        // 🔴 REM-96 scroll() fix anchor — REM-92/97-class fixture-blindness avoided (iter-2).
        //
        // Pre-fix (iter-1): scroll() emitted only ScrollModifier(direction, 0f, 0f, 0f) →
        //   corrupted the container stack (ScrollModifierOperation extends ListActionsOperation
        //   → opens a scope that requires a trailing ContainerEnd). Following ops were sucked
        //   into the scroll-action list. Fixed via trailing ContainerEnd + TouchExpression.
        // Pre-fix (iter-2): the positionId was reserved via ids.nextId() but no DATA_FLOAT op
        //   was emitted to declare it. TouchExpression then referenced an undeclared variable.
        //   Fixed by emitting FloatConstant(positionId, 0f) BEFORE the ScrollModifier op.
        //
        // Verified group, mirror upstream:
        //   1. ScrollModifier.write() (creation/modifiers/ScrollModifier.java:42-46) — default-
        //      position branch calls writer.addFloatConstant(0f) → DATA_FLOAT(positionId, 0f)
        //      + returns asNan(positionId).
        //   2. RemoteComposeWriter.addModifierScroll(direction, positionId)
        //      (RemoteComposeWriter.java:3670-3691) — reserveFloatVariable() × 2 (no op),
        //      ScrollModifier.apply, addTouchExpression, addContainerEnd.
        //
        // Anchored against the c_modifier_vertical_scroll.rc / c_modifier_horizontal_scroll.rc
        // corpus fixtures (assist NO-GO 2026-06-28 — both decoded to this group).
        val bytes = document(width = 200, height = 200) {
            box(modifier = LayoutModifier().scroll(direction = LayoutModifier.SCROLL_VERTICAL)) {}
        }
        val ops = DocumentReader.inflate(bytes).operations

        val boxIdx = ops.indexOfFirst {
            it is com.tneff.kmpremotecompose.remote.core.operations.layout.BoxLayout
        }
        assertTrue(boxIdx >= 0)
        val opcodes = ops.drop(boxIdx).map { it.opcode }
        assertEquals(
            listOf(
                com.tneff.kmpremotecompose.remote.core.operations.Operations.LAYOUT_BOX,
                com.tneff.kmpremotecompose.remote.core.operations.Operations.DATA_FLOAT,
                com.tneff.kmpremotecompose.remote.core.operations.Operations.MODIFIER_SCROLL,
                com.tneff.kmpremotecompose.remote.core.operations.Operations.TOUCH_EXPRESSION,
                com.tneff.kmpremotecompose.remote.core.operations.Operations.CONTAINER_END,
                com.tneff.kmpremotecompose.remote.core.operations.Operations.LAYOUT_CONTENT,
                com.tneff.kmpremotecompose.remote.core.operations.Operations.CONTAINER_END,
                com.tneff.kmpremotecompose.remote.core.operations.Operations.CONTAINER_END,
            ),
            opcodes,
            "scroll() = DATA_FLOAT(positionId, 0f) + ScrollModifier + TouchExpression + " +
                "ContainerEnd (closing scroll scope) BEFORE LayoutContent + 2 × ContainerEnd " +
                "(closing the box)",
        )
    }

    @Test
    fun scroll_positionIdDeclaredViaDataFloat_beforeScrollModifier() {
        // Iter-2 specific anchor: the DATA_FLOAT op must (a) precede MODIFIER_SCROLL and
        // (b) carry id == positionId, value == 0f. Without it, TOUCH_EXPRESSION's id field
        // references an undeclared variable — silent byte divergence the iter-1 anchors missed.
        val bytes = document(width = 200, height = 200) {
            box(modifier = LayoutModifier().scroll(direction = LayoutModifier.SCROLL_VERTICAL)) {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        val dataFloatIdx = ops.indexOfFirst {
            it is com.tneff.kmpremotecompose.remote.core.operations.FloatConstant
        }
        val scrollIdx = ops.indexOfFirst { it is ScrollModifier }
        assertTrue(dataFloatIdx >= 0, "DATA_FLOAT must be emitted as part of the scroll group")
        assertTrue(
            dataFloatIdx < scrollIdx,
            "DATA_FLOAT(positionId, 0f) must precede MODIFIER_SCROLL — declares the position var",
        )
        val df = ops[dataFloatIdx] as com.tneff.kmpremotecompose.remote.core.operations.FloatConstant
        assertEquals(42, df.id, "positionId = first allocated plain id (42, no contentDescription)")
        assertEquals(0f, df.value, "positionId is bound to literal 0f (default position)")
        // And the ScrollModifier's position slot must be asNan(positionId).
        val s = ops[scrollIdx] as ScrollModifier
        assertEquals(
            com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan(42).toRawBits(),
            s.position.toRawBits(),
            "MODIFIER_SCROLL.position = asNan(positionId) — back-reference to the just-declared DATA_FLOAT",
        )
    }

    @Test
    @IgnoreOnWasm
    fun scroll_fullByteEquality_vsCorpusFixture_verticalScroll() {
        // 🔑 The "Gold" byte-anchor per assist iter-2: extract the scroll-region (DATA_FLOAT
        // through the scroll's trailing ContainerEnd) from BOTH a minimal DSL document and the
        // c_modifier_vertical_scroll.rc corpus fixture, then assertContentEquals.
        //
        // The fixture was generated upstream from DemoModifierVerticalScroll (assist-decoded
        // 2026-06-28). The MODIFIER_CLIP_RECT op that appears in the fixture before the scroll
        // group is upstream's Modifier.verticalScroll() convenience clip — it's caller-side
        // (LayoutModifier.clipRect), NOT part of scroll's mandatory op group. So this test
        // anchors ONLY the scroll group itself.
        //
        // The expected 76-byte scroll-region (positionId=42 / maxId=43 / notchMaxId=44 at the
        // virgin allocator state — matches the fixture's id-pool position at the scroll site):
        //   DATA_FLOAT(0x50): 1 + 4(id=42) + 4(0f)                            = 9 bytes
        //   MODIFIER_SCROLL(0xE2): 1 + 4(dir=0) + 4(asNan42) + 4(asNan43) + 4(asNan44) = 17 bytes
        //   TOUCH_EXPRESSION(0x9D): 1 + 4(id=42) + 4*4(value/min/max/velocity) + 4(touchEff=3)
        //     + 4(expLen=3) + 4*3(touchPosY/-1f/MUL) + 4(stopLogic=0) + 4(easingLen=0) = 49 bytes
        //   CONTAINER_END(0xD6): 1 byte
        //   Total = 76 bytes
        val expectedScrollRegion = byteArrayOf(
            // DATA_FLOAT(id=42, value=0f)
            0x50,
            0x00, 0x00, 0x00, 0x2A,
            0x00, 0x00, 0x00, 0x00,
            // MODIFIER_SCROLL(direction=0, asNan(42), asNan(43), asNan(44))
            0xE2.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2A,
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2B,
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2C,
            // TOUCH_EXPRESSION(id=42, value=0f, min=0f, max=asNan(43), velocityId=0f, touchEff=3,
            //                  exp=[asNan(14)=FLOAT_TOUCH_POS_Y, -1f, asNan(0x310003)=MUL],
            //                  stopLogic=STOP_GENTLY<<16=0, stops=[], easing=[])
            0x9D.toByte(),
            0x00, 0x00, 0x00, 0x2A, // id
            0x00, 0x00, 0x00, 0x00, // value
            0x00, 0x00, 0x00, 0x00, // min
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2B, // max=asNan(43)
            0x00, 0x00, 0x00, 0x00, // velocityId
            0x00, 0x00, 0x00, 0x03, // touchEffects=3
            0x00, 0x00, 0x00, 0x03, // exp.length=3
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x0E, // exp[0]=FLOAT_TOUCH_POS_Y=asNan(14)
            0xBF.toByte(), 0x80.toByte(), 0x00, 0x00, // exp[1]=-1f
            0xFF.toByte(), 0xB1.toByte(), 0x00, 0x03, // exp[2]=MUL=asNan(0x310003)
            0x00, 0x00, 0x00, 0x00, // stopLogic = 0
            0x00, 0x00, 0x00, 0x00, // easing.length = 0
            // CONTAINER_END
            0xD6.toByte(),
        )

        // (a) DSL-produced scroll region.
        val dslBytes = document(width = 200, height = 200) {
            box(modifier = LayoutModifier().scroll(direction = LayoutModifier.SCROLL_VERTICAL)) {}
        }
        val dslStart = findScrollRegionStart(dslBytes)
        val dslRegion = dslBytes.copyOfRange(dslStart, dslStart + expectedScrollRegion.size)
        assertTrue(
            expectedScrollRegion.contentEquals(dslRegion),
            "DSL-produced scroll bytes diverge from the hand-computed expected sequence",
        )

        // (b) Corpus-fixture scroll region (Gold-Oracle).
        val fixtureBytes = com.tneff.kmpremotecompose.conformance.RcCorpus
            .readFixture("corpus/c_modifier_vertical_scroll.rc")
        val fixStart = findScrollRegionStart(fixtureBytes)
        val fixRegion = fixtureBytes.copyOfRange(fixStart, fixStart + expectedScrollRegion.size)
        assertTrue(
            expectedScrollRegion.contentEquals(fixRegion),
            "Fixture c_modifier_vertical_scroll.rc scroll-region diverges from the expected " +
                "sequence — either upstream changed or the audit was wrong",
        )

        // (c) Triple-pin: DSL and fixture must agree byte-for-byte at the scroll region.
        assertTrue(
            dslRegion.contentEquals(fixRegion),
            "DSL scroll-region bytes ≠ fixture scroll-region bytes (§2-divergence)",
        )
    }

    /**
     * REM-130 — Mirror of [scroll_fullByteEquality_vsCorpusFixture_verticalScroll] for
     * `SCROLL_HORIZONTAL`. Was previously asserted in the `scroll()` docstring ("verified against
     * `c_modifier_vertical_scroll.rc` + `c_modifier_horizontal_scroll.rc`") but the H test never
     * existed — assist (REM-130 corpus-anchor confirm, 2026-06-29) flagged the overclaim. This
     * test closes that gap so the docstring is now truthful.
     *
     * Difference from the V test: `direction=1` (instead of 0) at the MODIFIER_SCROLL operand,
     * and TOUCH_EXPRESSION `exp[0] = asNan(13) = FLOAT_TOUCH_POS_X` (instead of `asNan(14) = POS_Y`).
     * All other bytes — id-allocator values (positionId=42, maxId=43, notchMaxId=44 at virgin
     * pool), value/min/max NaN-id-refs, MUL marker, stop logic, expected lengths — are identical.
     */
    @Test
    @IgnoreOnWasm
    fun scroll_fullByteEquality_vsCorpusFixture_horizontalScroll() {
        // The expected 76-byte scroll-region for SCROLL_HORIZONTAL (direction=1, exp[0]=POS_X).
        val expectedScrollRegion = byteArrayOf(
            // DATA_FLOAT(id=42, value=0f)
            0x50,
            0x00, 0x00, 0x00, 0x2A,
            0x00, 0x00, 0x00, 0x00,
            // MODIFIER_SCROLL(direction=1, asNan(42), asNan(43), asNan(44))
            0xE2.toByte(),
            0x00, 0x00, 0x00, 0x01, // direction = 1 (horizontal)
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2A,
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2B,
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2C,
            // TOUCH_EXPRESSION(id=42, value=0f, min=0f, max=asNan(43), velocityId=0f, touchEff=3,
            //                  exp=[asNan(13)=FLOAT_TOUCH_POS_X, -1f, asNan(0x310003)=MUL],
            //                  stopLogic=STOP_GENTLY<<16=0, stops=[], easing=[])
            0x9D.toByte(),
            0x00, 0x00, 0x00, 0x2A, // id
            0x00, 0x00, 0x00, 0x00, // value
            0x00, 0x00, 0x00, 0x00, // min
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x2B, // max=asNan(43)
            0x00, 0x00, 0x00, 0x00, // velocityId
            0x00, 0x00, 0x00, 0x03, // touchEffects=3
            0x00, 0x00, 0x00, 0x03, // exp.length=3
            0xFF.toByte(), 0x80.toByte(), 0x00, 0x0D, // exp[0]=FLOAT_TOUCH_POS_X=asNan(13)
            0xBF.toByte(), 0x80.toByte(), 0x00, 0x00, // exp[1]=-1f
            0xFF.toByte(), 0xB1.toByte(), 0x00, 0x03, // exp[2]=MUL=asNan(0x310003)
            0x00, 0x00, 0x00, 0x00, // stopLogic = 0
            0x00, 0x00, 0x00, 0x00, // easing.length = 0
            // CONTAINER_END
            0xD6.toByte(),
        )

        // (a) DSL-produced scroll region.
        val dslBytes = document(width = 200, height = 200) {
            box(modifier = LayoutModifier().scroll(direction = LayoutModifier.SCROLL_HORIZONTAL)) {}
        }
        val dslStart = findScrollRegionStart(dslBytes)
        val dslRegion = dslBytes.copyOfRange(dslStart, dslStart + expectedScrollRegion.size)
        assertTrue(
            expectedScrollRegion.contentEquals(dslRegion),
            "DSL-produced horizontal-scroll bytes diverge from the hand-computed expected sequence",
        )

        // (b) Corpus-fixture scroll region (Gold-Oracle).
        val fixtureBytes = com.tneff.kmpremotecompose.conformance.RcCorpus
            .readFixture("corpus/c_modifier_horizontal_scroll.rc")
        val fixStart = findScrollRegionStart(fixtureBytes)
        val fixRegion = fixtureBytes.copyOfRange(fixStart, fixStart + expectedScrollRegion.size)
        assertTrue(
            expectedScrollRegion.contentEquals(fixRegion),
            "Fixture c_modifier_horizontal_scroll.rc scroll-region diverges from the expected " +
                "sequence — either upstream changed or the audit was wrong",
        )

        // (c) Triple-pin: DSL and fixture must agree byte-for-byte at the scroll region.
        assertTrue(
            dslRegion.contentEquals(fixRegion),
            "DSL horizontal-scroll-region bytes ≠ fixture scroll-region bytes (§2-divergence)",
        )
    }

    /**
     * Find the start of the scroll-region by scanning for the DATA_FLOAT(id=42, value=0f) byte
     * signature — that's the first op of the scroll group at the virgin allocator state.
     * Returns the offset of the leading DATA_FLOAT opcode byte (0x50).
     */
    private fun findScrollRegionStart(bytes: ByteArray): Int {
        // Signature: 0x50, 0x00, 0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x00 (9 bytes).
        for (i in 0..bytes.size - 9) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x00.toByte() && bytes[i + 2] == 0x00.toByte() &&
                bytes[i + 3] == 0x00.toByte() && bytes[i + 4] == 0x2A.toByte() &&
                bytes[i + 5] == 0x00.toByte() && bytes[i + 6] == 0x00.toByte() &&
                bytes[i + 7] == 0x00.toByte() && bytes[i + 8] == 0x00.toByte() &&
                // Confirm the next op is MODIFIER_SCROLL (0xE2).
                i + 9 < bytes.size && bytes[i + 9] == 0xE2.toByte()
            ) {
                return i
            }
        }
        kotlin.test.fail("DATA_FLOAT(id=42, 0f) + MODIFIER_SCROLL signature not found in bytes")
    }

    @Test
    fun scroll_allocatesThreePlainIds_positionMaxNotchMax_andNaNEncodesIntoScrollModifier() {
        // Mirror upstream id-allocation pattern (iter-2):
        //   positionId via addFloatConstant(0f) → emits DATA_FLOAT(positionId, 0f) + allocates the id
        //   maxId + notchMaxId via reserveFloatVariable() × 2 (pure id alloc, no op)
        // All three are NaN-wrapped into the ScrollModifier's position/max/notchMax slots.
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            // content-desc claimed id 42; scroll's first allocated id (positionId) = 43,
            // then max = 44, notchMax = 45.
            box(modifier = LayoutModifier().scroll(direction = LayoutModifier.SCROLL_HORIZONTAL)) {}
        }
        val s = DocumentReader.inflate(bytes).operations
            .first { it is ScrollModifier } as ScrollModifier
        assertEquals(1, s.direction, "horizontal direction = 1")
        assertEquals(
            com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan(43).toRawBits(),
            s.position.toRawBits(),
            "position slot = asNan(positionId=43)",
        )
        assertEquals(
            com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan(44).toRawBits(),
            s.max.toRawBits(),
            "max slot = asNan(maxId=44)",
        )
        assertEquals(
            com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan(45).toRawBits(),
            s.notchMax.toRawBits(),
            "notchMax slot = asNan(notchMaxId=45)",
        )
    }

    @Test
    fun scroll_touchExpression_carriesUpstreamRpnGroup_andPositionIdReference() {
        // Mirror addTouchExpression call at RemoteComposeWriter.java:3677-3689:
        //   id = idFromNan(positionId) = bare id
        //   value=0f, min=0f, max=asNan(maxId), velocityId=0f
        //   touchEffects=3
        //   exp = [touchExpressionDirection, -1f, MUL]
        //   stopLogic = STOP_GENTLY (=0) shl 16 = 0
        //   stops = [], easing = []
        // touchExpressionDirection = FLOAT_TOUCH_POS_X (=asNan(13)) for horizontal,
        //                          = FLOAT_TOUCH_POS_Y (=asNan(14)) for vertical
        val bytes = document(width = 200, height = 200) {
            box(modifier = LayoutModifier().scroll(direction = LayoutModifier.SCROLL_VERTICAL)) {}
        }
        val te = DocumentReader.inflate(bytes).operations
            .first {
                it is com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
            } as com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
        // positionId = first reserved = 42 (no contentDescription in this doc).
        assertEquals(42, te.id, "TouchExpression.id = bare positionId (reserveFloatVariable's id)")
        assertEquals(0f, te.value); assertEquals(0f, te.min)
        assertEquals(
            com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan(43).toRawBits(),
            te.max.toRawBits(),
            "TouchExpression.max = asNan(maxId=43)",
        )
        assertEquals(0f, te.velocityId); assertEquals(3, te.touchEffects)
        assertEquals(3, te.exp.size, "exp = [touchDir, -1, MUL]")
        assertEquals(
            com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan(LayoutModifier.ID_TOUCH_POS_Y).toRawBits(),
            te.exp[0].toRawBits(),
            "vertical scroll → exp[0] = FLOAT_TOUCH_POS_Y = asNan(14)",
        )
        assertEquals((-1f).toRawBits(), te.exp[1].toRawBits())
        assertEquals(
            RcExpression.MUL.toRawBits(), te.exp[2].toRawBits(),
            "exp[2] = MUL RPN marker = asNan(OFFSET + 3)",
        )
        assertEquals(0, te.stopLogic, "STOP_GENTLY(=0) shl 16 | stops.length(=0) = 0")
        assertEquals(0, te.stops.size)
        assertEquals(0, te.easing.size)
    }

    @Test
    fun scroll_horizontal_useFloatTouchPosX_asExpDirection() {
        // Direction-conditional touchDirection: != 0 → FLOAT_TOUCH_POS_X. Mirror upstream
        // RemoteComposeWriter.java:3673-3674 ternary.
        val bytes = document(width = 200, height = 200) {
            box(modifier = LayoutModifier().scroll(direction = LayoutModifier.SCROLL_HORIZONTAL)) {}
        }
        val te = DocumentReader.inflate(bytes).operations
            .first {
                it is com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
            } as com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
        assertEquals(
            com.tneff.kmpremotecompose.remote.wire.WireTypes.asNan(LayoutModifier.ID_TOUCH_POS_X).toRawBits(),
            te.exp[0].toRawBits(),
            "horizontal scroll → exp[0] = FLOAT_TOUCH_POS_X = asNan(13)",
        )
    }

    @Test
    fun scroll_doesNotCorruptFollowingModifiers_postFix() {
        // Pre-fix regression: scroll left ListActionsOperation open → following BACKGROUND ops
        // got sucked into the scroll action list. After the fix the trailing ContainerEnd closes
        // the scope so the next modifier emits cleanly at top-level.
        val bytes = document(width = 200, height = 200) {
            box(
                modifier = LayoutModifier()
                    .scroll(direction = LayoutModifier.SCROLL_VERTICAL)
                    .background(0xFF112233.toInt()),
            ) {}
        }
        val ops = DocumentReader.inflate(bytes).operations
        val containerEnd = com.tneff.kmpremotecompose.remote.core.operations.Operations.CONTAINER_END
        val bgIdx = ops.indexOfFirst {
            it is com.tneff.kmpremotecompose.remote.core.operations.layout.BackgroundModifier
        }
        // Background must appear AFTER the trailing ContainerEnd that closes scroll's scope.
        val scrollEndIdx = ops.indexOfFirst { it.opcode == containerEnd }
        assertTrue(
            scrollEndIdx >= 0 && bgIdx > scrollEndIdx,
            "BackgroundModifier must follow the scroll-scope-closing ContainerEnd " +
                "(pre-fix it would have been swallowed into the scroll action list)",
        )
    }

    @Test
    @IgnoreOnWasm
    fun alignBy_fullByteEquality_vsCorpusFixture_alignByBaseline() {
        // REM-130 — Replaces the prior `alignBy_writesLineFloat_andZeroFlagsByDefault` test, which
        // pinned a fictional `line=12.5f` value that appears in no corpus document (assist flagged
        // the fictional anchor in the REM-130 corpus-anchor confirm, 2026-06-29). The fix lifts
        // the line value from the real corpus fixture (raw NaN bits — the corpus encodes line as
        // a NaN-id-ref to an auto-resolved baseline reference), so the byte-anchor is grounded
        // against the upstream oracle rather than a literal we wrote ourselves.
        //
        // MODIFIER_ALIGN_BY (237) lives in the ANDROIDX_EXPERIMENTAL_OVERLAY (Operations.kt:413-417);
        // requires PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL, map-form api=7. `c_modifier_align_by_baseline.rc`
        // matches that profile (header `props=[5=500,6=500,9=DemoModifierAlignByBaseline,14=513]`).
        val corpus = com.tneff.kmpremotecompose.conformance.RcCorpus
            .readFixture("corpus/c_modifier_align_by_baseline.rc")
        // Locate the MODIFIER_ALIGN_BY op (opcode 237 = 0xED) in the corpus byte stream and lift
        // its 4-byte big-endian `line` payload. (Doing it from raw bytes — not via the inflated
        // op — preserves the exact NaN payload; `Float == Float` semantics drop NaN identity but
        // raw-bit lifting via `Float.fromBits(int)` is byte-faithful.)
        val alignByOpcode = com.tneff.kmpremotecompose.remote.core.operations.Operations.MODIFIER_ALIGN_BY
        val corpusOps = DocumentReader.inflateWithTrace(corpus).second
        val alignBySpan = corpusOps.first { it.opcode == alignByOpcode }
        // Wire: 1B opcode + 4B float(line) + 4B int(flags) = 9 bytes.
        assertEquals(9, alignBySpan.byteEnd - alignBySpan.byteStart, "MODIFIER_ALIGN_BY wire = 9 B")
        val lineBits = ((corpus[alignBySpan.byteStart + 1].toInt() and 0xff) shl 24) or
            ((corpus[alignBySpan.byteStart + 2].toInt() and 0xff) shl 16) or
            ((corpus[alignBySpan.byteStart + 3].toInt() and 0xff) shl 8) or
            (corpus[alignBySpan.byteStart + 4].toInt() and 0xff)
        val corpusLine = Float.fromBits(lineBits)
        // flags is at offset +5..+9 (big-endian int).
        val corpusFlags = ((corpus[alignBySpan.byteStart + 5].toInt() and 0xff) shl 24) or
            ((corpus[alignBySpan.byteStart + 6].toInt() and 0xff) shl 16) or
            ((corpus[alignBySpan.byteStart + 7].toInt() and 0xff) shl 8) or
            (corpus[alignBySpan.byteStart + 8].toInt() and 0xff)
        assertEquals(0, corpusFlags, "corpus alignBy flags must be 0 (default)")

        // Emit our own MODIFIER_ALIGN_BY with the corpus-derived line value and assert the 9-byte
        // op span is byte-identical (raw-NaN preservation is the §2-critical property — signaling
        // NaN payloads can be repacked silently in some toolchains).
        val profile = Profile(
            operationsProfiles = com.tneff.kmpremotecompose.remote.core.operations.Operations.PROFILE_ANDROIDX or
                com.tneff.kmpremotecompose.remote.core.operations.Operations.PROFILE_EXPERIMENTAL,
            services = defaultRcPlatformServices(),
        )
        val ctx = RemoteComposeContext(
            writer = com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter(
                width = 100, height = 100,
                profiles = profile.operationsProfiles,
                apiLevel = 7,
            ),
            profile = profile,
        )
        ctx.box(modifier = LayoutModifier().alignBy(line = corpusLine, flags = corpusFlags)) {}
        val emitted = ctx.encodeToByteArray()
        val emittedOps = DocumentReader.inflateWithTrace(emitted).second
        val emittedSpan = emittedOps.first { it.opcode == alignByOpcode }
        val emittedBytes = emitted.copyOfRange(emittedSpan.byteStart, emittedSpan.byteEnd)
        val corpusBytes = corpus.copyOfRange(alignBySpan.byteStart, alignBySpan.byteEnd)
        assertTrue(
            corpusBytes.contentEquals(emittedBytes),
            "MODIFIER_ALIGN_BY 9-byte op sub-span emitted by LayoutModifier.alignBy(line=Float.fromBits, " +
                "flags=0) must byte-match c_modifier_align_by_baseline.rc — NaN raw-bit preservation pin.",
        )
        // Belt-and-suspenders: the inflated AlignByModifier must round-trip the raw NaN bits
        // exactly (some toolchains repack signaling NaN silently; this catches that explicitly).
        val inflatedAlignBy = DocumentReader.inflate(emitted).operations
            .first { it is AlignByModifier } as AlignByModifier
        assertEquals(lineBits, inflatedAlignBy.line.toRawBits(), "raw NaN bits preserved through round-trip")
        assertEquals(corpusFlags, inflatedAlignBy.flags, "flags preserved through round-trip")
    }

    @Test
    fun fluentChaining_preservesOpOrder() {
        // The container open helper emits modifier ops in insertion order between Container-Open
        // and LayoutContent. Verify chain order is the emit order — critical for byte-equality
        // against fixtures where modifier sequence matters.
        val mod = emit {
            width(DimensionType.FILL)
                .padding(16f)
                .background(0xFF112233.toInt())
        }
        val openIdx = mod.indexOfFirst { it is com.tneff.kmpremotecompose.remote.core.operations.layout.BoxLayout }
        val contentIdx = mod.indexOfFirst { it is com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent }
        val between = mod.subList(openIdx + 1, contentIdx)
        assertEquals(3, between.size, "exactly 3 modifier ops between Box open and LayoutContent")
        // Check ordering matches chain.
        val classes = between.map { it::class.simpleName }
        assertEquals(listOf("WidthModifier", "PaddingModifier", "BackgroundModifier"), classes)
    }
}
