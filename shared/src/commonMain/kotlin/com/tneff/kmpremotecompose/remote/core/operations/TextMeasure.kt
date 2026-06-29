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
 * Measure a text (`TEXT_MEASURE`): exposes a measured dimension ([type]) of [textId] as [id].
 *
 * Wire layout: opcode, `int id`, `int textId`, `int type`.
 *
 * **REM-139 S1:** upstream measures in `paint` (`PaintOperation`); we produce the dimension in **Phase-A**
 * ([apply], VariableSupport) via `context.paintContext.getTextBounds` — the same getTextBounds seam
 * LayoutMeasure uses for CoreText — so the measured value is in the store BEFORE consumers (positioning /
 * FloatExpressions) read it. `type` low byte = which dim (W/H/L/R/T/B), high byte = renderer flags.
 * [write]/[read] untouched (§2). NOTE: measured at the ambient paint state (default in Phase-A), mirroring
 * upstream which measures against the current paint — sufficient for the corpus (looked-up label dims).
 */
class TextMeasure(val id: Int, val textId: Int, val type: Int) : Operation, VariableSupport {
    override val opcode: Int get() = Operations.TEXT_MEASURE

    /** Phase-A — measure [textId] and load the requested dimension into [id]. */
    override fun apply(context: RemoteContext) {
        val pc = context.paintContext ?: return
        val flags = type shr 8
        val b = FloatArray(4)
        pc.getTextBounds(textId, 0, -1, flags, b) // [left, top, right, bottom]
        val dim = when (type and 0xFF) {
            MEASURE_WIDTH -> b[2] - b[0]
            MEASURE_HEIGHT -> b[3] - b[1]
            MEASURE_LEFT -> b[0]
            MEASURE_RIGHT -> b[2]
            MEASURE_TOP -> b[1]
            MEASURE_BOTTOM -> b[3]
            else -> return
        }
        context.loadFloat(id, dim)
    }

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        buffer.writeInt(textId)
        buffer.writeInt(type)
    }

    override fun dump(): String = "TEXT_MEASURE id=$id textId=$textId type=$type"

    override fun equals(other: Any?): Boolean =
        this === other || (other is TextMeasure && id == other.id && textId == other.textId && type == other.type)

    override fun hashCode(): Int = 31 * (31 * id + textId) + type

    companion object : OperationReader {
        // Measured-dimension selector (upstream TextMeasure.MEASURE_*, the `type` low byte).
        private const val MEASURE_WIDTH = 0
        private const val MEASURE_HEIGHT = 1
        private const val MEASURE_LEFT = 2
        private const val MEASURE_RIGHT = 3
        private const val MEASURE_TOP = 4
        private const val MEASURE_BOTTOM = 5

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            operations += TextMeasure(buffer.readInt(), buffer.readInt(), buffer.readInt())
        }
    }
}
