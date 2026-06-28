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
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * `DRAW_TEXT_ON_CIRCLE` (opcode [Operations.DRAW_TEXT_ON_CIRCLE]) — render the text registered
 * under [textId] along a circular arc centred at ([centerX], [centerY]) with [radius], starting
 * at [startAngle] degrees and an optional [warpRadiusOffset].
 *
 * Wire layout (mirrors upstream `DrawTextOnCircle.apply` —
 * `androidx/compose/remote/core/operations/DrawTextOnCircle.java:151-197`):
 * opcode byte + int `textId` + float `centerX` + float `centerY` + float `radius` +
 * float `startAngle` + float `warpRadiusOffset` + byte `alignment.ordinal` + byte `placement.ordinal`
 * = 27 bytes total.
 *
 * [alignment] (`START`/`CENTER`/`END`) and [placement] (`OUTSIDE`/`INSIDE`) are written as single
 * bytes carrying the enum ordinal — `Alignment.fromInt` / `Placement.fromInt` on the reader side
 * fall back to `START`/`OUTSIDE` for out-of-range bytes, matching upstream's lenient decode.
 */
class DrawTextOnCircle(
    val textId: Int,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val startAngle: Float,
    val warpRadiusOffset: Float,
    val alignment: Alignment,
    val placement: Placement,
) : PaintOperation {

    enum class Alignment {
        START, CENTER, END;

        companion object {
            private val VALUES = entries.toTypedArray()
            fun fromInt(value: Int): Alignment = if (value in VALUES.indices) VALUES[value] else START
        }
    }

    enum class Placement {
        OUTSIDE, INSIDE;

        companion object {
            private val VALUES = entries.toTypedArray()
            fun fromInt(value: Int): Placement = if (value in VALUES.indices) VALUES[value] else OUTSIDE
        }
    }

    override val opcode: Int get() = Operations.DRAW_TEXT_ON_CIRCLE

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(textId)
        buffer.writeFloat(centerX)
        buffer.writeFloat(centerY)
        buffer.writeFloat(radius)
        buffer.writeFloat(startAngle)
        buffer.writeFloat(warpRadiusOffset)
        buffer.writeByte(alignment.ordinal)
        buffer.writeByte(placement.ordinal)
    }

    /** L2 render placeholder — text-on-arc rendering lands with the player-side text engine. */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        // Out of scope for REM-113 (creation-side only). Players that don't yet implement this
        // op will leave it as a no-op; the wire bytes round-trip regardless.
    }

    override fun dump(): String =
        "DRAW_TEXT_ON_CIRCLE textId=$textId cx=$centerX cy=$centerY r=$radius startAngle=$startAngle " +
            "warpOffset=$warpRadiusOffset alignment=$alignment placement=$placement"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawTextOnCircle &&
                textId == other.textId &&
                centerX.toRawBits() == other.centerX.toRawBits() &&
                centerY.toRawBits() == other.centerY.toRawBits() &&
                radius.toRawBits() == other.radius.toRawBits() &&
                startAngle.toRawBits() == other.startAngle.toRawBits() &&
                warpRadiusOffset.toRawBits() == other.warpRadiusOffset.toRawBits() &&
                alignment == other.alignment && placement == other.placement
            )

    override fun hashCode(): Int {
        var h = textId
        h = 31 * h + centerX.toRawBits()
        h = 31 * h + centerY.toRawBits()
        h = 31 * h + radius.toRawBits()
        h = 31 * h + startAngle.toRawBits()
        h = 31 * h + warpRadiusOffset.toRawBits()
        h = 31 * h + alignment.ordinal
        h = 31 * h + placement.ordinal
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val textId = buffer.readInt()
            val centerX = buffer.readFloat()
            val centerY = buffer.readFloat()
            val radius = buffer.readFloat()
            val startAngle = buffer.readFloat()
            val warpRadiusOffset = buffer.readFloat()
            val alignment = Alignment.fromInt(buffer.readByte())
            val placement = Placement.fromInt(buffer.readByte())
            operations += DrawTextOnCircle(
                textId, centerX, centerY, radius, startAngle, warpRadiusOffset, alignment, placement,
            )
        }
    }
}
