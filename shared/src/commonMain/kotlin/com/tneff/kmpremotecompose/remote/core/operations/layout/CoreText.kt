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
 * `CORE_TEXT` (opcode [Operations.CORE_TEXT]) — a styled text component (ANDROIDX/WIDGETS overlay op).
 *
 * Wire layout: opcode byte + int `textId` + short `paramCount` + `paramCount` styled parameters.
 * Each parameter is a 1-byte id followed by a value whose width is fixed by the id's TextStyle type
 * (mirrors upstream `CoreText.read` + `TextStyle.PARAMETERS`/`CommandParameters`):
 *  - INT/FLOAT → 4 bytes, SHORT → 2, BYTE/BOOLEAN → 1
 *  - PA_INT/PA_FLOAT → short count + count×4 bytes
 *  - PA_STRING → int length + length bytes
 *
 * We carry each parameter's id + raw value bytes verbatim, so it round-trips byte-exact without the
 * semantic TextStyle model (which is a Layer-2/creation concern). The float-typed values may be
 * NaN-encoded ids; raw bytes are preserved either way.
 */
class CoreText(
    val textId: Int,
    val params: List<Param>,
) : Operation {

    /** One styled parameter: its TextStyle [id] and the raw value bytes exactly as on the wire. */
    class Param(val id: Int, val value: ByteArray) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Param && id == other.id && value.contentEquals(other.value))

        override fun hashCode(): Int = 31 * id + value.contentHashCode()
    }

    override val opcode: Int get() = Operations.CORE_TEXT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeShort(params.size)
        for (p in params) {
            buffer.writeByte(p.id)
            for (b in p.value) buffer.writeByte(b.toInt() and 0xFF)
        }
    }

    override fun dump(): String = "CORE_TEXT textId=$textId params=${params.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is CoreText && textId == other.textId && params == other.params)

    override fun hashCode(): Int = 31 * textId + params.hashCode()

    companion object : OperationReader {

        // CommandParameters value-type codes (upstream CommandParameters.P_*/PA_*).
        private const val P_INT = 1
        private const val P_FLOAT = 2
        private const val P_SHORT = 3
        private const val P_BYTE = 4
        private const val P_BOOLEAN = 5
        private const val PA_INT = 6
        private const val PA_FLOAT = 7
        private const val PA_STRING = 8

        /**
         * TextStyle parameter id → value type (upstream `TextStyle.PARAMETERS`). The type fixes each
         * parameter's on-wire width; only the width matters for byte-faithful carry.
         */
        private val PARAM_TYPE: Map<Int, Int> = mapOf(
            1 to P_INT, 2 to P_INT, 3 to P_INT, 4 to P_INT, // id, animationId, color, colorId
            5 to P_FLOAT, // fontSize
            6 to P_INT, // fontStyle
            7 to P_FLOAT, // fontWeight
            8 to P_INT, 9 to P_INT, 10 to P_INT, 11 to P_INT, // fontFamily, textAlign, overflow, maxLines
            12 to P_FLOAT, 13 to P_FLOAT, 14 to P_FLOAT, // letterSpacing, lineHeightAdd, lineHeightMultiplier
            15 to P_INT, 16 to P_INT, 17 to P_INT, // breakStrategy, hyphenationFrequency, justificationMode
            18 to P_BOOLEAN, 19 to P_BOOLEAN, // underline, strikethrough
            20 to PA_INT, 21 to PA_FLOAT, // fontAxis, fontAxisValues
            22 to P_BOOLEAN, // autosize
            23 to P_INT, 24 to P_INT, // flags, parentId
            25 to P_FLOAT, 26 to P_FLOAT, // minFontSize, maxFontSize
        )

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val textId = buffer.readInt()
            val count = buffer.readShort()
            val params = ArrayList<Param>(count)
            repeat(count) {
                val id = buffer.readByte()
                val type = PARAM_TYPE[id] ?: throw IllegalStateException("CORE_TEXT: unknown param id $id")
                params += Param(id, readValue(buffer, type))
            }
            operations += CoreText(textId, params)
        }

        /** Read a parameter value's raw bytes for [type], exactly as wide as the wire format dictates. */
        private fun readValue(buffer: WireBuffer, type: Int): ByteArray = when (type) {
            P_INT, P_FLOAT -> raw(buffer, 4)
            P_SHORT -> raw(buffer, 2)
            P_BYTE, P_BOOLEAN -> raw(buffer, 1)
            PA_INT, PA_FLOAT -> { // short count + count×4
                val hi = buffer.readByte()
                val lo = buffer.readByte()
                val n = (hi shl 8) or lo
                byteArrayOf(hi.toByte(), lo.toByte()) + raw(buffer, n * 4)
            }
            PA_STRING -> { // int length + length bytes
                val lenBytes = raw(buffer, 4)
                val len = (lenBytes[0].toInt() and 0xFF shl 24) or
                    (lenBytes[1].toInt() and 0xFF shl 16) or
                    (lenBytes[2].toInt() and 0xFF shl 8) or
                    (lenBytes[3].toInt() and 0xFF)
                lenBytes + raw(buffer, len)
            }
            else -> throw IllegalStateException("CORE_TEXT: unknown param type $type")
        }

        private fun raw(buffer: WireBuffer, n: Int): ByteArray = ByteArray(n) { buffer.readByte().toByte() }
    }
}
