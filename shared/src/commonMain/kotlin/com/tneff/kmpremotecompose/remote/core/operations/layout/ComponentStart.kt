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
 * `COMPONENT_START` (opcode [Operations.COMPONENT_START]) — begins a component definition.
 *
 * Wire layout: opcode byte + int `type` + int `componentId` + **float `width` + float `height`**
 * (mirrors upstream `ComponentStart.apply`/`read` exactly — all four fields). Omitting width/height
 * desyncs every following operation, so the byte length here is fixed at 17 bytes.
 */
class ComponentStart(
    val type: Int,
    val componentId: Int,
    val width: Float,
    val height: Float,
) : Operation {

    override val opcode: Int get() = Operations.COMPONENT_START

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(type)
        buffer.writeInt(componentId)
        buffer.writeFloat(width)
        buffer.writeFloat(height)
    }

    override fun dump(): String = "COMPONENT_START type=$type id=$componentId w=$width h=$height"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ComponentStart &&
                type == other.type && componentId == other.componentId &&
                width == other.width && height == other.height
            )

    override fun hashCode(): Int =
        31 * (31 * (31 * type + componentId) + width.hashCode()) + height.hashCode()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += ComponentStart(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readFloat(),
                buffer.readFloat(),
            )
        }
    }
}
