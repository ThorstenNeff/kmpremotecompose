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
 * Save the current canvas matrix (`MATRIX_SAVE`) — a no-field marker; the matching restore pops it.
 *
 * Wire layout: just the opcode.
 */
class MatrixSave : Operation {

    override val opcode: Int get() = Operations.MATRIX_SAVE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
    }

    override fun dump(): String = "MATRIX_SAVE"

    override fun equals(other: Any?): Boolean = this === other || other is MatrixSave

    override fun hashCode(): Int = Operations.MATRIX_SAVE

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += MatrixSave()
        }
    }
}
