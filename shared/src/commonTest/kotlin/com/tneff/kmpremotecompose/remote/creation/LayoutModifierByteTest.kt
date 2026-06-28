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

        val mod = emit { scroll(direction = LayoutModifier.SCROLL_HORIZONTAL, position = 0f, max = 500f) }
        val s = mod.first { it is ScrollModifier } as ScrollModifier
        assertEquals(1, s.direction); assertEquals(0f, s.position)
        assertEquals(500f, s.max); assertEquals(0f, s.notchMax)
    }

    @Test
    fun alignBy_writesLineFloat_andZeroFlagsByDefault() {
        // Upstream addModifierAlignBy(line): AlignByModifierOperation.apply(buffer, line, 0).
        // RemoteComposeBuffer.java:1782-1784.
        // MODIFIER_ALIGN_BY (237) lives in the ANDROIDX_EXPERIMENTAL_OVERLAY (Operations.kt:413-417);
        // NOT valid under baseline/flat-form documents. The test bypasses document() to use the
        // map-form (api 7) writer with PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL — the same pattern
        // REM-92's namedVariable_byteAnchor test used for experimental-only ops.
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
        ctx.box(modifier = LayoutModifier().alignBy(line = 12.5f)) {}
        val ops = com.tneff.kmpremotecompose.remote.core.document.DocumentReader
            .inflate(ctx.encodeToByteArray()).operations
        val a = ops.first { it is AlignByModifier } as AlignByModifier
        assertEquals(12.5f, a.line)
        assertEquals(0, a.flags, "flags default = 0 per addModifierAlignBy contract")
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
