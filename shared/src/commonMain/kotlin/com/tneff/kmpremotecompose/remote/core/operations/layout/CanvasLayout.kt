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
 * `LAYOUT_CANVAS` (opcode [Operations.LAYOUT_CANVAS]) — a canvas layout container.
 *
 * Wire layout: opcode byte + int `componentId` + int `animationId` = 9 bytes (mirrors upstream
 * `CanvasLayout.apply`/`read`).
 */
class CanvasLayout(val componentId: Int, val animationId: Int) : Operation {

    override val opcode: Int get() = Operations.LAYOUT_CANVAS

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(componentId)
        buffer.writeInt(animationId)
    }

    override fun dump(): String = "LAYOUT_CANVAS id=$componentId anim=$animationId"

    override fun equals(other: Any?): Boolean =
        this === other || (other is CanvasLayout && componentId == other.componentId && animationId == other.animationId)

    override fun hashCode(): Int = 31 * componentId + animationId

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += CanvasLayout(buffer.readInt(), buffer.readInt())
        }
    }
}
