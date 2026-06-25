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
 * `MODIFIER_ROUNDED_CLIP_RECT` (opcode [Operations.MODIFIER_ROUNDED_CLIP_RECT]) — clip a component to
 * a rounded rectangle.
 *
 * Wire layout: opcode byte + float `topStart` + float `topEnd` + float `bottomStart` +
 * float `bottomEnd` (mirrors upstream `RoundedClipRectModifierOperation.apply`/`read`).
 */
class RoundedClipRectModifier(
    val topStart: Float,
    val topEnd: Float,
    val bottomStart: Float,
    val bottomEnd: Float,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_ROUNDED_CLIP_RECT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeFloat(topStart)
        buffer.writeFloat(topEnd)
        buffer.writeFloat(bottomStart)
        buffer.writeFloat(bottomEnd)
    }

    override fun dump(): String =
        "MODIFIER_ROUNDED_CLIP_RECT topStart=$topStart topEnd=$topEnd " +
            "bottomStart=$bottomStart bottomEnd=$bottomEnd"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is RoundedClipRectModifier &&
                topStart.toRawBits() == other.topStart.toRawBits() &&
                topEnd.toRawBits() == other.topEnd.toRawBits() &&
                bottomStart.toRawBits() == other.bottomStart.toRawBits() &&
                bottomEnd.toRawBits() == other.bottomEnd.toRawBits()
            )

    override fun hashCode(): Int {
        var h = topStart.toRawBits()
        h = 31 * h + topEnd.toRawBits()
        h = 31 * h + bottomStart.toRawBits()
        h = 31 * h + bottomEnd.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += RoundedClipRectModifier(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            )
        }
    }
}
