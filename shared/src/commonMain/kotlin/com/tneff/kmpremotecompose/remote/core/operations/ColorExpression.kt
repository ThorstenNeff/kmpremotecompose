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
 * A color expression (`COLOR_EXPRESSIONS`): a computed/interpolated color bound to [id].
 *
 * Wire layout: opcode, `int id`, then four raw `int` parameters. The parameters pack mode, channel
 * values and tween (as raw float bits or NaN-encoded ids) in a layout the player interprets; at the
 * wire level they are four opaque 32-bit words, which is all byte-compatibility needs.
 */
class ColorExpression(
    val id: Int,
    val param1: Int,
    val param2: Int,
    val param3: Int,
    val param4: Int,
) : Operation {

    override val opcode: Int get() = Operations.COLOR_EXPRESSIONS

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(param1)
        buffer.writeInt(param2)
        buffer.writeInt(param3)
        buffer.writeInt(param4)
    }

    override fun dump(): String = "COLOR_EXPRESSIONS id=$id params=[$param1,$param2,$param3,$param4]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ColorExpression && id == other.id &&
                param1 == other.param1 && param2 == other.param2 &&
                param3 == other.param3 && param4 == other.param4)

    override fun hashCode(): Int {
        var r = id
        r = 31 * r + param1
        r = 31 * r + param2
        r = 31 * r + param3
        r = 31 * r + param4
        return r
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ColorExpression(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
            )
        }
    }
}
