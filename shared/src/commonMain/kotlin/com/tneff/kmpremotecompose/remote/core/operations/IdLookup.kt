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
 * Look up an id from a data set by index (`ID_LOOKUP`): `id = dataSet[index]`.
 *
 * Wire layout: opcode, `int id`, `int dataSet`, `float index` (raw bits — may be a NaN-encoded id).
 * Profile-overlay op (androidx + widgets).
 */
class IdLookup(val id: Int, val dataSet: Int, val index: Float) : Operation {

    override val opcode: Int get() = Operations.ID_LOOKUP

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(dataSet)
        buffer.writeFloat(index)
    }

    override fun dump(): String = "ID_LOOKUP id=$id dataSet=$dataSet index=$index"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is IdLookup && id == other.id && dataSet == other.dataSet &&
                index.toRawBits() == other.index.toRawBits())

    override fun hashCode(): Int = 31 * (31 * id + dataSet) + index.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += IdLookup(buffer.readInt(), buffer.readInt(), buffer.readFloat())
        }
    }
}
