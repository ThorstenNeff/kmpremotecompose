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
 * `LAYOUT_CANVAS_CONTENT` (opcode [Operations.LAYOUT_CANVAS_CONTENT]) — the content marker for a
 * canvas layout (partner of [CanvasLayout]).
 *
 * Wire layout: opcode byte + int `componentId` = 5 bytes (mirrors upstream `CanvasContent.apply`/`read`).
 */
class CanvasContent(val componentId: Int) : Operation {

    override val opcode: Int get() = Operations.LAYOUT_CANVAS_CONTENT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(componentId)
    }

    override fun dump(): String = "LAYOUT_CANVAS_CONTENT id=$componentId"

    override fun equals(other: Any?): Boolean =
        this === other || (other is CanvasContent && componentId == other.componentId)

    override fun hashCode(): Int = componentId

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += CanvasContent(buffer.readInt())
        }
    }
}
