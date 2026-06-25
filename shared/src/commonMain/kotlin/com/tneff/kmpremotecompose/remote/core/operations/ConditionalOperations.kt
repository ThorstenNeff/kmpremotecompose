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
 * A conditional gate (`CONDITIONAL_OPERATIONS`): compares [a] and [b] by [type] to enable/skip the
 * operations that follow.
 *
 * Wire layout: opcode, `byte type`, `float a`, `float b` (a/b raw bits — may be NaN-encoded ids).
 */
class ConditionalOperations(val type: Int, val a: Float, val b: Float) : Operation {
    override val opcode: Int get() = Operations.CONDITIONAL_OPERATIONS

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeByte(type)
        buffer.writeFloat(a)
        buffer.writeFloat(b)
    }

    override fun dump(): String = "CONDITIONAL_OPERATIONS type=$type a=$a b=$b"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ConditionalOperations && type == other.type &&
                a.toRawBits() == other.a.toRawBits() && b.toRawBits() == other.b.toRawBits())

    override fun hashCode(): Int = 31 * (31 * type + a.toRawBits()) + b.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ConditionalOperations(buffer.readByte(), buffer.readFloat(), buffer.readFloat())
        }
    }
}
