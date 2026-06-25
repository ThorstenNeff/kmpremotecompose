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
 * `MODIFIER_ALIGN_BY` (opcode [Operations.MODIFIER_ALIGN_BY]) — aligns a component by an alignment
 * line.
 *
 * Wire layout: opcode byte + float `line` + int `flags` (mirrors upstream `AlignByModifierOperation`).
 */
class AlignByModifier(
    val line: Float,
    val flags: Int,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_ALIGN_BY

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(line)
        buffer.writeInt(flags)
    }

    override fun dump(): String = "MODIFIER_ALIGN_BY line=$line flags=$flags"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is AlignByModifier &&
                line.toRawBits() == other.line.toRawBits() &&
                flags == other.flags
            )

    override fun hashCode(): Int {
        var h = line.toRawBits()
        h = 31 * h + flags
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += AlignByModifier(
                buffer.readFloat(), buffer.readInt(),
            )
        }
    }
}
