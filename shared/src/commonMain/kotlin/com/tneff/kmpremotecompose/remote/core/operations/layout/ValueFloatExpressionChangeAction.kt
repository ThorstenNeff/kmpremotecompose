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
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `VALUE_FLOAT_EXPRESSION_CHANGE_ACTION` (opcode [Operations.VALUE_FLOAT_EXPRESSION_CHANGE_ACTION]) — on
 * action, set a float-expression value to another value (value-change family, cf.
 * [ValueIntegerChangeAction] / [ValueStringChangeAction]).
 *
 * Wire layout: opcode byte + int `valueId` + int `value` = 9 bytes (mirrors upstream
 * `ValueFloatExpressionChangeActionOperation.apply`/`read`). Both are ids.
 */
class ValueFloatExpressionChangeAction(
    val valueId: Int,
    val value: Int,
) : Operation {

    override val opcode: Int get() = Operations.VALUE_FLOAT_EXPRESSION_CHANGE_ACTION

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(valueId)
        buffer.writeInt(value)
    }

    override fun dump(): String = "VALUE_FLOAT_EXPRESSION_CHANGE_ACTION valueId=$valueId value=$value"

    override fun equals(other: Any?): Boolean =
        this === other || (other is ValueFloatExpressionChangeAction && valueId == other.valueId && value == other.value)

    override fun hashCode(): Int = 31 * valueId + value

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ValueFloatExpressionChangeAction(buffer.readInt(), buffer.readInt())
        }
    }
}
