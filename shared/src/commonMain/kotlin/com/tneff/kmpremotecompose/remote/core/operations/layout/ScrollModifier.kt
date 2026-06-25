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
 * `MODIFIER_SCROLL` (opcode [Operations.MODIFIER_SCROLL]) — makes a component scrollable.
 *
 * Wire layout: opcode byte + int `direction` + float `position` + float `max` + float `notchMax`
 * (mirrors upstream `ScrollModifierOperation`).
 */
class ScrollModifier(
    val direction: Int,
    val position: Float,
    val max: Float,
    val notchMax: Float,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_SCROLL

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(direction)
        buffer.writeFloat(position)
        buffer.writeFloat(max)
        buffer.writeFloat(notchMax)
    }

    override fun dump(): String =
        "MODIFIER_SCROLL direction=$direction position=$position max=$max notchMax=$notchMax"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ScrollModifier &&
                direction == other.direction &&
                position.toRawBits() == other.position.toRawBits() &&
                max.toRawBits() == other.max.toRawBits() &&
                notchMax.toRawBits() == other.notchMax.toRawBits()
            )

    override fun hashCode(): Int {
        var h = direction
        h = 31 * h + position.toRawBits()
        h = 31 * h + max.toRawBits()
        h = 31 * h + notchMax.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ScrollModifier(
                buffer.readInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            )
        }
    }
}
