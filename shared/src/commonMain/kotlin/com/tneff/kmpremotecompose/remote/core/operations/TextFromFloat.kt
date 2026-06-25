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
 * Render a float [value] as text under [textId] (`TEXT_FROM_FLOAT`), e.g. `continuousSeconds().format()`.
 *
 * Wire layout: opcode, `int textId`, `float value` (raw bits — may be a NaN-encoded variable id),
 * `int digits` packing `digitsBefore` in the high 16 bits and `digitsAfter` in the low 16 bits, and
 * `int flags`.
 */
class TextFromFloat(
    val textId: Int,
    val value: Float,
    val digitsBefore: Int,
    val digitsAfter: Int,
    val flags: Int,
) : Operation {

    override val opcode: Int get() = Operations.TEXT_FROM_FLOAT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeFloat(value)
        buffer.writeInt(((digitsBefore and 0xFFFF) shl 16) or (digitsAfter and 0xFFFF))
        buffer.writeInt(flags)
    }

    override fun dump(): String =
        "TEXT_FROM_FLOAT id=$textId value=$value digits=$digitsBefore.$digitsAfter flags=$flags"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is TextFromFloat &&
                textId == other.textId &&
                value.toRawBits() == other.value.toRawBits() && // distinguish NaN-id payloads
                digitsBefore == other.digitsBefore &&
                digitsAfter == other.digitsAfter &&
                flags == other.flags)

    override fun hashCode(): Int {
        var result = textId
        result = 31 * result + value.toRawBits()
        result = 31 * result + digitsBefore
        result = 31 * result + digitsAfter
        result = 31 * result + flags
        return result
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val textId = buffer.readInt()
            val value = buffer.readFloat()
            val digits = buffer.readInt()
            val digitsBefore = (digits shr 16) and 0xFFFF
            val digitsAfter = digits and 0xFFFF
            val flags = buffer.readInt()
            operations += TextFromFloat(textId, value, digitsBefore, digitsAfter, flags)
        }
    }
}
