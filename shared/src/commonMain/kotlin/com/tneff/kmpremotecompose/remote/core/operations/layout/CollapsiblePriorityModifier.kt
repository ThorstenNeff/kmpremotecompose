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
 * `MODIFIER_COLLAPSIBLE_PRIORITY` (opcode [Operations.MODIFIER_COLLAPSIBLE_PRIORITY]) — assigns a
 * collapse priority to a component within a collapsible container.
 *
 * Wire layout: opcode byte + int `orientation` + float `priority` (mirrors upstream
 * `CollapsiblePriorityModifierOperation`).
 */
class CollapsiblePriorityModifier(
    val orientation: Int,
    val priority: Float,
) : Operation {

    override val opcode: Int get() = Operations.MODIFIER_COLLAPSIBLE_PRIORITY

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(orientation)
        buffer.writeFloat(priority)
    }

    override fun dump(): String =
        "MODIFIER_COLLAPSIBLE_PRIORITY orientation=$orientation priority=$priority"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is CollapsiblePriorityModifier &&
                orientation == other.orientation &&
                priority.toRawBits() == other.priority.toRawBits()
            )

    override fun hashCode(): Int {
        var h = orientation
        h = 31 * h + priority.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += CollapsiblePriorityModifier(
                buffer.readInt(), buffer.readFloat(),
            )
        }
    }
}
