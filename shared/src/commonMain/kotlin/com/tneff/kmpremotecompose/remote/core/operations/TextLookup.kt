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

import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * Look up a string from a data set by index (`TEXT_LOOKUP`): `textId = dataSet[index]`.
 *
 * Wire layout: opcode, `int textId`, `int dataSet`, `float index` (raw bits — may be a NaN-encoded id).
 *
 * **Binding (REM-59 I2):** a `PaintOperation` (runs in the loop body each iteration, after DATA_TEXT +
 * ID_LIST in pass order) — resolves [index] (NaN var ref → the loop index), looks up the text-id
 * `dataSet[index]` in the ID_LIST collection, then writes `getText(text-id)` under [textId]
 * (upstream `TextLookup.apply`). Chart labels (good_pie_chart/pie_chart2).
 */
class TextLookup(val textId: Int, val dataSet: Int, val index: Float) : PaintOperation {

    override val opcode: Int get() = Operations.TEXT_LOOKUP

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeInt(dataSet)
        buffer.writeFloat(index)
    }

    override fun paint(context: RemoteContext, paint: PaintContext) {
        val idx = (if (index.isNaN()) context.getFloat(WireTypes.idFromNan(index)) else index).toInt()
        val targetTextId = context.getIdArray(dataSet)?.getOrNull(idx) ?: return
        context.putText(textId, context.getText(targetTextId) ?: "")
    }

    override fun dump(): String = "TEXT_LOOKUP id=$textId dataSet=$dataSet index=$index"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is TextLookup && textId == other.textId && dataSet == other.dataSet &&
                index.toRawBits() == other.index.toRawBits())

    override fun hashCode(): Int = 31 * (31 * textId + dataSet) + index.toRawBits()

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += TextLookup(buffer.readInt(), buffer.readInt(), buffer.readFloat())
        }
    }
}
