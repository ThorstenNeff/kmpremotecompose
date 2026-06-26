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
 * `DATA_PATH` (opcode [Operations.DATA_PATH]) — stores a path's geometry as a float array.
 *
 * **Wire layout:** opcode byte + int `id` + int `count` + `count` big-endian floats (mirrors upstream
 * `PathData.apply`). The `id` int packs the winding rule in its high byte and the id in the low 24
 * bits, and individual floats may be NaN-encoded variable ids — but both are runtime semantics; the
 * wire is just `id` + a length-prefixed float array. We carry [id] and the raw float bits ([data])
 * verbatim, so it round-trips byte-exact (the winding split and id-remap are Layer-2 / Loom concerns).
 * `count` is bounded to `0..`[MAX_PATH_LENGTH] on read (upstream corruption guard).
 */
class PathData(val id: Int, val data: IntArray) : PaintOperation {

    override val opcode: Int get() = Operations.DATA_PATH

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(data.size)
        for (bits in data) buffer.writeInt(bits)
    }

    /** L2 render: single-pass data-apply — store path floats so a later DRAW_PATH can build it (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        context.putPathData(id, FloatArray(data.size) { Float.fromBits(data[it]) })
    }

    override fun dump(): String = "DATA_PATH id=$id count=${data.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is PathData && id == other.id && data.contentEquals(other.data))

    override fun hashCode(): Int = 31 * id + data.contentHashCode()

    companion object : OperationReader {

        /** Upstream `PathData` corruption guard. */
        const val MAX_PATH_LENGTH: Int = 20000

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val count = buffer.readInt()
            require(count in 0..MAX_PATH_LENGTH) { "corrupt path length=$count" }
            operations += PathData(id, IntArray(count) { buffer.readInt() })
        }
    }
}
