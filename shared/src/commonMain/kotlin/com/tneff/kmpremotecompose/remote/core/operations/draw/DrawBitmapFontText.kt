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
 * `DRAW_BITMAP_FONT_TEXT_RUN` (opcode [Operations.DRAW_BITMAP_FONT_TEXT_RUN]) — draws a run of text
 * using a bitmap font.
 *
 * Wire layout (mirrors upstream `DrawBitmapFontText.apply`/`read`): opcode byte + int `textId` (with bit
 * 31 set iff a `glyphSpacing` follows) + **optional** float `glyphSpacing` + int `bitmapFontId` + int
 * `start` + int `end` + float `x` + float `y`. We carry `textId` stripped of the marker bit and rebuild
 * the marker on write from `glyphSpacing != 0f`, so the conditional field round-trips byte-exact.
 */
class DrawBitmapFontText(
    val textId: Int,
    val bitmapFontId: Int,
    val start: Int,
    val end: Int,
    val x: Float,
    val y: Float,
    val glyphSpacing: Float,
) : PaintOperation {

    override val opcode: Int get() = Operations.DRAW_BITMAP_FONT_TEXT_RUN

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        if (glyphSpacing == 0f) {
            buffer.writeInt(textId)
        } else {
            buffer.writeInt(textId or 0x7FFFFFFF.inv()) // set bit 31
            buffer.writeFloat(glyphSpacing)
        }
        buffer.writeInt(bitmapFontId)
        buffer.writeInt(start)
        buffer.writeInt(end)
        buffer.writeFloat(x)
        buffer.writeFloat(y)
    }

    /** Render: the adapter resolves text [textId] + font [bitmapFontId] + glyph bitmaps from context. */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        paint.drawBitmapFontText(textId, bitmapFontId, start, end, x, y, glyphSpacing)
    }

    override fun dump(): String =
        "DRAW_BITMAP_FONT_TEXT_RUN textId=$textId font=$bitmapFontId start=$start end=$end x=$x y=$y glyphSpacing=$glyphSpacing"

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DrawBitmapFontText &&
                textId == other.textId && bitmapFontId == other.bitmapFontId &&
                start == other.start && end == other.end &&
                x.toRawBits() == other.x.toRawBits() && y.toRawBits() == other.y.toRawBits() &&
                glyphSpacing.toRawBits() == other.glyphSpacing.toRawBits()
            )

    override fun hashCode(): Int {
        var h = textId
        h = 31 * h + bitmapFontId
        h = 31 * h + start
        h = 31 * h + end
        h = 31 * h + x.toRawBits()
        h = 31 * h + y.toRawBits()
        h = 31 * h + glyphSpacing.toRawBits()
        return h
    }

    companion object : OperationReader {
        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val raw = buffer.readInt()
            val hasGlyph = (raw and 0x7FFFFFFF.inv()) != 0
            val textId = if (hasGlyph) raw and 0x7FFFFFFF else raw
            val glyphSpacing = if (hasGlyph) buffer.readFloat() else 0f
            val bitmapFontId = buffer.readInt()
            val start = buffer.readInt()
            val end = buffer.readInt()
            val x = buffer.readFloat()
            val y = buffer.readFloat()
            operations += DrawBitmapFontText(textId, bitmapFontId, start, end, x, y, glyphSpacing)
        }
    }
}
