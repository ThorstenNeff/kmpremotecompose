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
 * `MODIFIER_HEIGHT_IN` (opcode [Operations.MODIFIER_HEIGHT_IN]) — constrains a component's height to a
 * `[min, max]` range.
 *
 * Wire layout: opcode byte + float `min` + float `max` (mirrors upstream `HeightInModifierOperation`).
 */
class HeightInModifier(
    val min: Float,
    val max: Float,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_HEIGHT_IN

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(min)
        buffer.writeFloat(max)
    }

    override fun dump(): String = "MODIFIER_HEIGHT_IN min=$min max=$max"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is HeightInModifier &&
                min.toRawBits() == other.min.toRawBits() &&
                max.toRawBits() == other.max.toRawBits()
            )

    override fun hashCode(): Int {
        var h = min.toRawBits()
        h = 31 * h + max.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += HeightInModifier(
                buffer.readFloat(), buffer.readFloat(),
            )
        }
    }
}
