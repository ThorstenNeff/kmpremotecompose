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
 * Update one slot of a dynamic float list (`UPDATE_DYNAMIC_FLOAT_LIST`): sets `list[index] = value`
 * for the list [id].
 *
 * Wire layout: opcode, `int id`, `float index`, `float value` (raw bits — may be NaN-encoded ids).
 * Profile-overlay op (androidx + widgets).
 */
class UpdateDynamicFloatList(val id: Int, val index: Float, val value: Float) : Operation {
    override val opcode: Int get() = Operations.UPDATE_DYNAMIC_FLOAT_LIST

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(index)
        buffer.writeFloat(value)
    }

    override fun dump(): String = "UPDATE_DYNAMIC_FLOAT_LIST id=$id index=$index value=$value"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is UpdateDynamicFloatList && id == other.id &&
                index.toRawBits() == other.index.toRawBits() && value.toRawBits() == other.value.toRawBits())

    override fun hashCode(): Int = 31 * (31 * id + index.toRawBits()) + value.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += UpdateDynamicFloatList(buffer.readInt(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
