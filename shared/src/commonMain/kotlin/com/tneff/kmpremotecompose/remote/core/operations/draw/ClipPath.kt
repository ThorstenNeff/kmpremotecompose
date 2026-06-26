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
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `CLIP_PATH` (opcode [Operations.CLIP_PATH]) — clips the canvas to a path (the draw op, distinct from
 * `CLIP_RECT` 39 and `MODIFIER_CLIP_RECT` 108).
 *
 * Wire layout: opcode byte + a single packed int = 5 bytes (mirrors upstream `ClipPath`). The int packs
 * the path id (low 20 bits) and the region op (`>> 24`); we carry the raw packed int verbatim so it
 * round-trips byte-exact (bits 20–23 are preserved, which a decompose/recompose would drop).
 */
class ClipPath(val packed: Int) : PaintOperation {

    /** Path id — low 20 bits of [packed] (upstream `pack & 0xFFFFF`). */
    val id: Int get() = packed and 0xFFFFF

    /** Region op — upstream `pack >> 24`. */
    val regionOp: Int get() = packed shr 24

    override val opcode: Int get() = Operations.CLIP_PATH

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(packed)
    }

    /** L2 render: bind this op to the paint context (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.clipPath(id, regionOp)
    }

    override fun dump(): String = "CLIP_PATH id=$id regionOp=$regionOp"

    override fun equals(other: Any?): Boolean = this === other || (other is ClipPath && packed == other.packed)

    override fun hashCode(): Int = packed

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ClipPath(buffer.readInt())
        }
    }
}
