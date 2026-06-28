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
package com.tneff.kmpremotecompose.remote.core.operations.layout

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-108 S3b — additive scroll runtime ([ScrollModifier.scrollOffset] / [ScrollModifier.applyScrollBounds]).
 * Mirrors the decoded `c_modifier_vertical_scroll`: SCROLLMOD position=var(id42) max=var(id43)
 * notchMax=var(id44), paired TE writes its output into id42. These tests drive the store directly (the TE
 * eval is S2-Core, proven elsewhere) and assert the offset read + bounds publish + the §5 defensive clamp.
 * §2 byte-conformance is covered by the breadth suite (wire/equals/hashCode are untouched).
 */
class ScrollModifierRuntimeTest {

    private fun mod() = ScrollModifier(
        direction = ScrollModifier.VERTICAL,
        position = WireTypes.asNan(42),
        max = WireTypes.asNan(43),
        notchMax = WireTypes.asNan(44),
    )

    @Test fun scrollOffset_readsTeOutput_negatedNoClampWhenWithinMax() {
        val ctx = RemoteContext()
        ctx.loadFloat(43, 100f) // bounds published (layout ran)
        ctx.loadFloat(42, 30f)  // TE output (drag)
        assertEquals(-30f, mod().scrollOffset(ctx), "offset = -min(max=100, pos=30)")
    }

    @Test fun scrollOffset_clampsAtMax() {
        val ctx = RemoteContext()
        ctx.loadFloat(43, 100f)
        ctx.loadFloat(42, 150f) // over-drag beyond content
        assertEquals(-100f, mod().scrollOffset(ctx), "offset clamps to -max=100, not -150")
    }

    @Test fun scrollOffset_defensiveNoClampWhenMaxUnset() {
        // §5: layout loadFloat(max) hasn't run yet → id43 = store default 0 → treat as "no upper clamp"
        // (return -pos), NOT clamp-to-0 (which would freeze the first frame).
        val ctx = RemoteContext()
        ctx.loadFloat(42, 30f) // pos set, max (id43) absent
        assertEquals(-30f, mod().scrollOffset(ctx), "unset max ⇒ no clamp ⇒ -pos (no first-frame freeze)")
    }

    @Test fun scrollOffset_zeroWhenContentFits() {
        // content fits: both the TE-clamped pos and max resolve to 0 → offset 0.
        val ctx = RemoteContext()
        ctx.loadFloat(43, 0f)
        ctx.loadFloat(42, 0f)
        assertEquals(0f, mod().scrollOffset(ctx), "content fits ⇒ no offset")
    }

    @Test fun applyScrollBounds_publishesMaxAndNotchMaxToReferencedIds() {
        val ctx = RemoteContext()
        mod().applyScrollBounds(ctx, maxScroll = 80f, contentDimension = 500f)
        assertEquals(80f, ctx.getFloat(43), "max published to the id `max` references (= the TE clamp id)")
        assertEquals(500f, ctx.getFloat(44), "contentDimension published to the id `notchMax` references")
    }

    @Test fun boundsThenOffset_endToEnd_clampsAgainstPublishedMax() {
        // The locked §5 order in one go: applyScrollBounds → (TE writes pos) → scrollOffset.
        val ctx = RemoteContext()
        mod().applyScrollBounds(ctx, maxScroll = 60f, contentDimension = 360f)
        ctx.loadFloat(42, 200f) // TE over-drag; its own clamp also reads id43=60 in the real path
        assertEquals(-60f, mod().scrollOffset(ctx))
    }
}
