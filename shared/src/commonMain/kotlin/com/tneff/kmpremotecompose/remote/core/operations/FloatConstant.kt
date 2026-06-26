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

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * A float constant (`DATA_FLOAT`): binds float [value] to [id].
 *
 * Wire layout: opcode, `int id`, `float value`. The value is written with raw bits, so a NaN-encoded
 * id passed as the value survives byte-for-byte (see [WireBuffer.writeFloat]).
 *
 * Eval-Engine E1 (Stufe-min): a producer — [apply] loads [value] into the float store under [id], so a
 * coord that is the NaN data-variable `asNan(id)` resolves to this value in the paint phase.
 */
class FloatConstant(val id: Int, val value: Float) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.DATA_FLOAT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeFloat(value)
    }

    /** Load the constant into the float store (upstream `DATA_FLOAT.apply` → `loadFloat`). */
    override fun apply(context: RemoteContext) {
        context.loadFloat(id, value)
    }

    override fun dump(): String = "DATA_FLOAT id=$id value=$value"

    override fun equals(other: Any?): Boolean =
        this === other ||
            // compare raw bits so NaN payloads (ids) are distinguished, unlike Float ==
            (other is FloatConstant && id == other.id && value.toRawBits() == other.value.toRawBits())

    override fun hashCode(): Int = 31 * id + value.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val value = buffer.readFloat()
            operations += FloatConstant(id, value)
        }
    }
}
