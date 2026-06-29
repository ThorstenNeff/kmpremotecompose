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
package com.tneff.kmpremotecompose.remote.core.operations.draw

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.resolveCoord
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_BITMAP` (opcode [Operations.DRAW_BITMAP]) — draws the whole bitmap stored under [id] into a
 * dst rect (the draw command, distinct from the group-A `DATA_BITMAP` image data). The simplest of the
 * three blit ops: no src-rect / no scaleType (cf. [DrawBitmapInt], [DrawBitmapScaled]).
 *
 * Wire layout: opcode byte + int `id` + float `left` + float `top` + float `right` + float `bottom` +
 * int `descriptionId` = 25 bytes (mirrors upstream `DrawBitmap.apply`/`read`). Floats may carry
 * NaN-encoded ids; raw bits preserved.
 *
 * **REM-132 render-apply:** upstream `DrawBitmap extends PaintOperation implements VariableSupport` — a
 * CONSUMER. [updateVariables] resolves NaN l/t/r/b into render-only fields (Phase-A); [paint] blits the
 * whole bitmap into the resolved dst rect via the 5-arg `PaintContext.drawBitmap`. Render-only:
 * [write]/[read] and the raw fields are untouched → 173-byte-conformance intact (§2/§6).
 *
 * **Scope flag (REM-132, dispatch≠visual):** this renders the DrawBitmap *sprite* only. The single
 * corpus user (`impulse_demo_confetti_demo`) draws it per-particle inside the **Impulse/Particles
 * subsystem** (ImpulseStart/Process/ParticlesCreate/ParticleLoop — all still `Operation`-only =
 * separately deferred). At static t=0 our linear walk dispatches this op once → one sprite at the
 * resolved rect, NOT the animated particle field. Full-doc confetti correctness is NOT REM-132; it is
 * gated behind the Particles subsystem ticket.
 */
class DrawBitmap(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val descriptionId: Int,
) : PaintOperation, VariableSupport {

    override val opcode: Int get() = Operations.DRAW_BITMAP

    // REM-132 render-only resolved dst rect (Phase-A output; not serialized → byte-safe).
    private var rLeft: Float = left
    private var rTop: Float = top
    private var rRight: Float = right
    private var rBottom: Float = bottom

    /** Resolve NaN-encoded dst-rect refs against the store (consumer side, upstream `updateVariables`). */
    override fun updateVariables(context: RemoteContext) {
        rLeft = context.resolveCoord(left)
        rTop = context.resolveCoord(top)
        rRight = context.resolveCoord(right)
        rBottom = context.resolveCoord(bottom)
    }

    /** Blit the whole bitmap [id] into the resolved dst rect (upstream `paint` → 5-arg `drawBitmap`). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawBitmap(id, rLeft, rTop, rRight, rBottom)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(left)
        buffer.writeFloat(top)
        buffer.writeFloat(right)
        buffer.writeFloat(bottom)
        buffer.writeInt(descriptionId)
    }

    override fun dump(): String = "DRAW_BITMAP id=$id left=$left top=$top right=$right bottom=$bottom descId=$descriptionId"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawBitmap &&
                id == other.id &&
                left.toRawBits() == other.left.toRawBits() && top.toRawBits() == other.top.toRawBits() &&
                right.toRawBits() == other.right.toRawBits() && bottom.toRawBits() == other.bottom.toRawBits() &&
                descriptionId == other.descriptionId
            )

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + left.toRawBits()
        h = 31 * h + top.toRawBits()
        h = 31 * h + right.toRawBits()
        h = 31 * h + bottom.toRawBits()
        h = 31 * h + descriptionId
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawBitmap(
                buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
