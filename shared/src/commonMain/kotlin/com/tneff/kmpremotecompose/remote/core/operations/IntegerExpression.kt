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
 * An integer RPN expression (`INTEGER_EXPRESSION`): binds the computed result to [id]. [mask] marks
 * which [value] entries are ids (vs literals).
 *
 * Wire layout: opcode, `int id`, `int mask`, `int count`, then `count` ints.
 */
class IntegerExpression(val id: Int, val mask: Int, val value: IntArray) : Operation {
    override val opcode: Int get() = Operations.INTEGER_EXPRESSION

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(mask)
        buffer.writeInt(value.size)
        for (v in value) buffer.writeInt(v)
    }

    override fun dump(): String = "INTEGER_EXPRESSION id=$id mask=$mask value[${value.size}]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is IntegerExpression && id == other.id && mask == other.mask && value.contentEquals(other.value))

    override fun hashCode(): Int = 31 * (31 * id + mask) + value.contentHashCode()

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_EXPRESSION_SIZE`. */
        const val MAX_SIZE = 32

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val mask = buffer.readInt()
            val count = buffer.readInt()
            if (count < 0 || count > MAX_SIZE) throw IllegalStateException("integer expression too long: $count")
            operations += IntegerExpression(id, mask, IntArray(count) { buffer.readInt() })
        }
    }
}
