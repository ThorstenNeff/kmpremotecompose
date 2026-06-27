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

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * Concatenate two text sources into a new text (`TEXT_MERGE`): `textId = srcId1 + srcId2`.
 *
 * Wire layout: opcode, `int textId`, `int srcId1`, `int srcId2`.
 *
 * **Binding (REM-53):** producer op — Phase A reads the two source texts from the store and writes the
 * concatenation under [textId] (so `TextFromFloat`/`DATA_TEXT` sources, resolved earlier in Phase A,
 * are already present). Missing source ⇒ empty (fail-soft).
 */
class TextMerge(val textId: Int, val srcId1: Int, val srcId2: Int) : Operation, VariableSupport {

    override val opcode: Int get() = Operations.TEXT_MERGE

    override fun apply(context: RemoteContext) {
        context.putText(textId, (context.getText(srcId1) ?: "") + (context.getText(srcId2) ?: ""))
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeInt(srcId1)
        buffer.writeInt(srcId2)
    }

    override fun dump(): String = "TEXT_MERGE id=$textId src=[$srcId1,$srcId2]"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is TextMerge && textId == other.textId && srcId1 == other.srcId1 && srcId2 == other.srcId2)

    override fun hashCode(): Int = 31 * (31 * textId + srcId1) + srcId2

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += TextMerge(buffer.readInt(), buffer.readInt(), buffer.readInt())
        }
    }
}
