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
 * `ROOT_CONTENT_BEHAVIOR` (opcode [Operations.ROOT_CONTENT_BEHAVIOR]) — document-level scroll /
 * alignment / sizing behaviour.
 *
 * Wire layout: opcode byte + int `scroll` + int `alignment` + int `sizing` + int `mode` (mirrors
 * upstream `RootContentBehavior.apply`/`read`).
 *
 * Registration note: upstream registers this in the **API-6 base map** and, for API ≥ 7, only under
 * the **deprecated** overlays (androidx/widgets) — not the v7 base. See [LayoutOps.register].
 */
class RootContentBehavior(
    val scroll: Int,
    val alignment: Int,
    val sizing: Int,
    val mode: Int,
) : Operation {

    override val opcode: Int get() = Operations.ROOT_CONTENT_BEHAVIOR

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(scroll)
        buffer.writeInt(alignment)
        buffer.writeInt(sizing)
        buffer.writeInt(mode)
    }

    override fun dump(): String =
        "ROOT_CONTENT_BEHAVIOR scroll=$scroll alignment=$alignment sizing=$sizing mode=$mode"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is RootContentBehavior &&
                scroll == other.scroll && alignment == other.alignment &&
                sizing == other.sizing && mode == other.mode
            )

    override fun hashCode(): Int = 31 * (31 * (31 * scroll + alignment) + sizing) + mode

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += RootContentBehavior(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
            )
        }
    }
}
