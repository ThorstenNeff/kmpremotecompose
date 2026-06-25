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
 * `MODIFIER_ZINDEX` (opcode [Operations.MODIFIER_ZINDEX]) — sets a component's draw order.
 *
 * Wire layout: opcode byte + float `value` (mirrors upstream `ZIndexModifierOperation`).
 */
class ZIndexModifier(
    val value: Float,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_ZINDEX

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(value)
    }

    override fun dump(): String = "MODIFIER_ZINDEX value=$value"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ZIndexModifier &&
                value.toRawBits() == other.value.toRawBits()
            )

    override fun hashCode(): Int {
        var h = value.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ZIndexModifier(
                buffer.readFloat(),
            )
        }
    }
}
