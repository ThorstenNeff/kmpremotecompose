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
 * `CANVAS_OPERATIONS` (opcode [Operations.CANVAS_OPERATIONS]) — marks a block of canvas draw
 * operations within a canvas layout.
 *
 * Wire layout: opcode byte only, no fields (mirrors upstream `CanvasOperations`).
 */
class CanvasOperations : Operation {

    override val opcode: Int get() = Operations.CANVAS_OPERATIONS

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
    }

    override fun dump(): String = "CANVAS_OPERATIONS"

    override fun equals(other: Any?): Boolean = this === other || other is CanvasOperations

    override fun hashCode(): Int = opcode

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += CanvasOperations()
        }
    }
}
