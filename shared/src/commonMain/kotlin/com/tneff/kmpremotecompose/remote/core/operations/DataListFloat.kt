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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * A float list (`FLOAT_LIST`): binds an array of floats to [id].
 *
 * Wire layout: opcode, `int id`, `int count`, then `count` raw floats.
 */
class DataListFloat(val id: Int, val values: FloatArray) : Operation {

    override val opcode: Int get() = Operations.FLOAT_LIST

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(values.size)
        for (v in values) buffer.writeFloat(v)
    }

    override fun dump(): String = "FLOAT_LIST id=$id values[${values.size}]"

    override fun equals(other: Any?): Boolean =
        this === other || (other is DataListFloat && id == other.id && values.contentEquals(other.values))

    override fun hashCode(): Int = 31 * id + values.contentHashCode()

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_PARTICLE_FLOAT_ARRAY_SIZE` guard ceiling. */
        const val MAX_FLOAT_LIST_SIZE = 2000

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val count = buffer.readInt()
            if (count < 0 || count > MAX_FLOAT_LIST_SIZE) throw IllegalStateException("float list too long: $count")
            val values = FloatArray(count) { buffer.readFloat() }
            operations += DataListFloat(id, values)
        }
    }
}
