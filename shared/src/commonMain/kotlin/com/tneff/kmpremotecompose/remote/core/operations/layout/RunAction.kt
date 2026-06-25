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
 * `RUN_ACTION` (opcode [Operations.RUN_ACTION]) — a run-action container block; child operations follow
 * as separate ops.
 *
 * Wire layout: opcode byte only, no fields (mirrors upstream `RunActionOperation`).
 */
class RunAction : Operation {

    override val opcode: Int get() = Operations.RUN_ACTION

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
    }

    override fun dump(): String = "RUN_ACTION"

    override fun equals(other: Any?): Boolean = this === other || other is RunAction

    override fun hashCode(): Int = opcode

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += RunAction()
        }
    }
}
