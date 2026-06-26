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
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `PATH_ADD` (opcode [Operations.PATH_ADD]) — appends path command data to a path.
 *
 * Wire layout: opcode byte + int `id` + int `length` + `length`×float `data` (mirrors upstream
 * `PathAppend.apply`/`read`). The floats may carry NaN-encoded ids; raw bits preserved.
 */
class PathAppend(
    val id: Int,
    val data: FloatArray,
) : PaintOperation {

    override val opcode: Int get() = Operations.PATH_ADD

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(data.size)
        for (v in data) buffer.writeFloat(v)
    }

    /** L2 render: single-pass data-apply — append commands onto the path (or RESET), upstream PathAppend (REM-33). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        if (data.isNotEmpty() && data[0].toRawBits() == WireTypes.asNan(17).toRawBits()) {
            context.putPathData(id, FloatArray(0)) // upstream RESET (= asNan(17)) clears the path
            return
        }
        val existing = context.getPathData(id)
        context.putPathData(id, if (existing != null) existing + data else data)
    }

    override fun dump(): String = "PATH_ADD id=$id data=${data.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is PathAppend && id == other.id && rawEquals(data, other.data))

    override fun hashCode(): Int = 31 * id + rawHash(data)

    companion object : OperationReader {
        private fun rawEquals(a: FloatArray, b: FloatArray): Boolean {
            if (a.size != b.size) return false
            for (i in a.indices) if (a[i].toRawBits() != b[i].toRawBits()) return false
            return true
        }

        private fun rawHash(a: FloatArray): Int {
            var h = 1
            for (v in a) h = 31 * h + v.toRawBits()
            return h
        }

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val len = buffer.readInt()
            operations += PathAppend(id, FloatArray(len) { buffer.readFloat() })
        }
    }
}
