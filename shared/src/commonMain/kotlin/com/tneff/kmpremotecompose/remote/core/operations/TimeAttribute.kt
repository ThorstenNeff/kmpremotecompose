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
 * A derived time attribute (`ATTRIBUTE_TIME`): a clock/time-derived value of [type] for [textId]
 * into [id], with optional [args].
 *
 * Wire layout: opcode, `int id`, `int textId`, `short type`, `short argCount`, then `argCount` ints.
 */
class TimeAttribute(val id: Int, val textId: Int, val type: Int, val args: IntArray? = null) : Operation {
    override val opcode: Int get() = Operations.ATTRIBUTE_TIME

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(textId)
        buffer.writeShort(type)
        val a = args
        if (a == null || a.isEmpty()) {
            buffer.writeShort(0)
        } else {
            buffer.writeShort(a.size)
            for (v in a) buffer.writeInt(v)
        }
    }

    override fun dump(): String = "ATTRIBUTE_TIME id=$id textId=$textId type=$type args=${args?.size ?: 0}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimeAttribute || id != other.id || textId != other.textId || type != other.type) return false
        val a = args ?: IntArray(0)
        val b = other.args ?: IntArray(0)
        return a.contentEquals(b)
    }

    override fun hashCode(): Int = 31 * (31 * (31 * id + textId) + type) + (args?.contentHashCode() ?: 0)

    companion object : OperationReader {
        /** Mirror of the upstream `MAX_ARG_LEN`. */
        const val MAX_ARG_LEN = 100

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val textId = buffer.readInt()
            val type = buffer.readShort()
            val len = buffer.readShort()
            if (len > MAX_ARG_LEN) throw IllegalStateException("too many time-attribute args: $len")
            val args = if (len != 0) IntArray(len) { buffer.readInt() } else null
            operations += TimeAttribute(id, textId, type, args)
        }
    }
}
