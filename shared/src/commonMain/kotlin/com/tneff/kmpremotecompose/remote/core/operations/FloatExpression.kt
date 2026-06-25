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
 * An animated float expression (`ANIMATED_FLOAT`): an RPN float expression [value], optionally with
 * an [animation] spec.
 *
 * Wire layout: opcode, `int id`, `int len` packing `value.size` in the low 16 bits and
 * `animation.size` in the high 16 bits (0 ⇒ no animation), then the value floats and, if present,
 * the animation floats. All floats are raw bits (operators/operands may be NaN-encoded ids).
 */
class FloatExpression(
    val id: Int,
    val value: FloatArray,
    val animation: FloatArray? = null,
) : Operation {

    override val opcode: Int get() = Operations.ANIMATED_FLOAT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        var len = value.size and 0xFFFF
        if (animation != null) len = len or ((animation.size and 0xFFFF) shl 16)
        buffer.writeInt(len)
        for (v in value) buffer.writeFloat(v)
        animation?.forEach { buffer.writeFloat(it) }
    }

    override fun dump(): String =
        "ANIMATED_FLOAT id=$id value[${value.size}]" + (animation?.let { " anim[${it.size}]" } ?: "")

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is FloatExpression &&
                id == other.id &&
                value.contentEquals(other.value) &&
                (animation?.contentEquals(other.animation ?: FloatArray(0)) ?: (other.animation == null)))

    override fun hashCode(): Int = 31 * (31 * id + value.contentHashCode()) + (animation?.contentHashCode() ?: 0)

    companion object : OperationReader {
        /** Mirror of the upstream `Limits.MAX_EXPRESSION_SIZE`. */
        const val MAX_EXPRESSION_SIZE = 32

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val len = buffer.readInt()
            val valueLen = len and 0xFFFF
            val animLen = (len shr 16) and 0xFFFF
            if (valueLen > MAX_EXPRESSION_SIZE) throw IllegalStateException("float expression too long: $valueLen")
            val value = FloatArray(valueLen) { buffer.readFloat() }
            val animation = if (animLen != 0) FloatArray(animLen) { buffer.readFloat() } else null
            operations += FloatExpression(id, value, animation)
        }
    }
}
