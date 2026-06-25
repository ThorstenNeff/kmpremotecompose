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
 * A dynamically-sized float list (`DYNAMIC_FLOAT_LIST`): reserves a float array of [nbValues] under
 * [id], where the size is itself a float (a literal count or a NaN-encoded id reference).
 *
 * Wire layout: opcode, `int id`, `float nbValues` (raw bits). Profile-overlay op (androidx + widgets).
 */
class DataDynamicListFloat(val id: Int, val nbValues: Float) : Operation {

    override val opcode: Int get() = Operations.DYNAMIC_FLOAT_LIST

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(nbValues)
    }

    override fun dump(): String = "DYNAMIC_FLOAT_LIST id=$id nbValues=$nbValues"

    override fun equals(other: Any?): Boolean =
        this === other ||
            // raw bits so a NaN-encoded size id is distinguished
            (other is DataDynamicListFloat && id == other.id && nbValues.toRawBits() == other.nbValues.toRawBits())

    override fun hashCode(): Int = 31 * id + nbValues.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DataDynamicListFloat(buffer.readInt(), buffer.readFloat())
        }
    }
}
