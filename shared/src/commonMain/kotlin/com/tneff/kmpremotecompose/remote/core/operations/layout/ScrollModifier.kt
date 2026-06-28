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

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.math.min

/**
 * `MODIFIER_SCROLL` (opcode [Operations.MODIFIER_SCROLL]) — makes a component scrollable.
 *
 * Wire layout: opcode byte + int `direction` + float `position` + float `max` + float `notchMax`
 * (mirrors upstream `ScrollModifierOperation`).
 *
 * **REM-108 S3b — interactive scroll (TechSpec `docs/TECHSPEC-REM-108-S3b-scroll.md`).** The wire form
 * stays a byte-faithful carrier; the scroll *effect* is additive runtime layered on top (§6: `read` /
 * `write` / `equals` / `hashCode` are UNCHANGED → 173 byte-conformance intact). In our player the paired
 * `TouchExpression` is already evaluated (S2-Core Phase-A) and touch-driven (S2b `dispatchTouch` notifies
 * every TE) each frame, writing its output into the float store under the id `position` references. So
 * this modifier does **not** re-own or re-evaluate the TE — it just *reads* that output ([scrollOffset])
 * and *exposes the bounds* the TE clamps against ([applyScrollBounds]); the scrollable component (dev-2)
 * translates + clips by the offset.
 */
class ScrollModifier(
    val direction: Int,
    val position: Float,
    val max: Float,
    val notchMax: Float,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_SCROLL

    /**
     * REM-108 S3b — the content offset this modifier currently imposes, read live from the float store.
     * Mirrors upstream `ScrollModifierOperation` paint: `scroll = getFloat(idFromNan(position))` (the
     * paired TE's output) → `offset = -min(maxScroll, scroll)`. `maxScroll` is read back from the id
     * `max` references — the same id [applyScrollBounds] writes during layout (upstream round-trips the
     * computed max through the store; in delta-mode docs `max` is also the TE's own clamp id, §4a).
     *
     * The sign/axis convention matches upstream (negative = content shifted toward origin); the caller
     * applies it on Y for [VERTICAL] / X for [HORIZONTAL] (read [direction]).
     *
     * **Defensive clamp (TechSpec §5):** if the layout `loadFloat(max,…)` has not yet run this frame
     * (walk can't guarantee layout-before-eval), `maxScroll` reads as the store default 0 → treat that as
     * "no upper clamp" (return `-scroll`), NOT "clamp to 0" (which would freeze the first frame). When the
     * content genuinely fits, both `max` and the TE-clamped `scroll` resolve to 0 → offset 0 either way.
     */
    fun scrollOffset(context: RemoteContext): Float {
        val scroll = context.getFloat(WireTypes.idFromNan(position))
        val maxScroll = context.getFloat(WireTypes.idFromNan(max))
        val clamped = if (maxScroll > 0f) min(maxScroll, scroll) else scroll
        val offset = -clamped
        // Normalize -0.0 → 0.0 (negating a zero clamp): identical as a translate, but keeps the
        // golden/static baseline and any downstream equality stable (no signed-zero wart).
        return if (offset == 0f) 0f else offset
    }

    /**
     * REM-108 S3b — layout-side bounds publish. Mirrors upstream `ScrollModifierOperation.layout`:
     * `loadFloat(idFromNan(max), maxScroll)` + `loadFloat(idFromNan(notchMax), contentDimension)`. The
     * scrollable component (dev-2) supplies the measured `maxScroll` (= `max(0, contentDim − hostDim)`)
     * and `contentDimension` from `LayoutMeasure`.
     *
     * **Sequencing (TechSpec §5, MUST hold):** call this BEFORE the Phase-A eval of the paired TE — in
     * the delta-mode corpus docs `max` is the TE's own clamp id, so a stale/zero value on the first frame
     * would clamp the scroll to 0 (first-frame glitch). Order: layout-measure → applyScrollBounds →
     * eval(TE) → scrollOffset read → apply.
     */
    fun applyScrollBounds(context: RemoteContext, maxScroll: Float, contentDimension: Float) {
        context.loadFloat(WireTypes.idFromNan(max), maxScroll)
        context.loadFloat(WireTypes.idFromNan(notchMax), contentDimension)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(direction)
        buffer.writeFloat(position)
        buffer.writeFloat(max)
        buffer.writeFloat(notchMax)
    }

    override fun dump(): String =
        "MODIFIER_SCROLL direction=$direction position=$position max=$max notchMax=$notchMax"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ScrollModifier &&
                direction == other.direction &&
                position.toRawBits() == other.position.toRawBits() &&
                max.toRawBits() == other.max.toRawBits() &&
                notchMax.toRawBits() == other.notchMax.toRawBits()
            )

    override fun hashCode(): Int {
        var h = direction
        h = 31 * h + position.toRawBits()
        h = 31 * h + max.toRawBits()
        h = 31 * h + notchMax.toRawBits()
        return h
    }

    companion object : OperationReader {
        /** Scroll axis (upstream `ScrollModifierOperation`): wire `direction` value. */
        const val VERTICAL = 0
        const val HORIZONTAL = 1

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ScrollModifier(
                buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            )
        }
    }
}
