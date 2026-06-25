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
package com.tneff.kmpremotecompose.remote.core.operations.draw

import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.OperationReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_PATH` (opcode [Operations.DRAW_PATH]) — draw a previously stored path by id.
 *
 * Wire layout: opcode byte + int `id` (mirrors upstream `DrawPath`).
 */
class DrawPath(val id: Int) : Operation {

    override val opcode: Int get() = Operations.DRAW_PATH

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
    }

    override fun dump(): String = "DRAW_PATH id=$id"

    override fun equals(other: Any?): Boolean = this === other || (other is DrawPath && id == other.id)

    override fun hashCode(): Int = id

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += DrawPath(buffer.readInt())
        }
    }
}
