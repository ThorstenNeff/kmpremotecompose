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

/**
 * Bitmap font data (`DATA_BITMAP_FONT`): glyph table (and optional kerning table) under [id].
 *
 * Wire layout: opcode, `int id`, `int versionAndGlyphCount` (glyph count in the low 16 bits, format
 * version in the high 16 bits), then each glyph (chars, bitmapId, 6 shorts). When a kerning table is
 * present the version is V2 and a `short count` + that many (key, `short` adjustment) entries follow.
 *
 * The version is derived from kerning presence (mirrors upstream), so a decoded font re-encodes
 * byte-for-byte. Entry order is preserved.
 *
 * Render binding (REM-35 Inc2): a data op — [paint] registers this font under [id] so a
 * `DrawBitmapFontText` op can resolve it (the DATA-op-must-dispatch rule, like `DATA_TEXT`).
 */
class BitmapFontData(
    val id: Int,
    val glyphs: List<Glyph>,
    val kerning: List<KerningEntry> = emptyList(),
) : Operation, PaintOperation {

    /** One glyph: the [chars] it renders, its [bitmapId], margins and bitmap size (all 16-bit). */
    data class Glyph(
        val chars: String,
        val bitmapId: Int,
        val marginLeft: Int,
        val marginTop: Int,
        val marginRight: Int,
        val marginBottom: Int,
        val bitmapWidth: Int,
        val bitmapHeight: Int,
    )

    /** One kerning entry: a glyph-pair [key] and its [adjustment] (16-bit). */
    data class KerningEntry(val key: String, val adjustment: Int)

    override val opcode: Int get() = Operations.DATA_BITMAP_FONT

    override fun write(buffer: WireBuffer) {
        buffer.writeByte(opcode)
        buffer.writeInt(id)
        // Kerning is a V2 feature; the version sits in the high 16 bits of the glyph-count word.
        if (kerning.isNotEmpty()) {
            buffer.writeInt((glyphs.size and 0xFFFF) or (VERSION_2 shl 16))
        } else {
            buffer.writeInt(glyphs.size and 0xFFFF)
        }
        for (g in glyphs) {
            buffer.writeUTF8(g.chars)
            buffer.writeInt(g.bitmapId)
            buffer.writeShort(g.marginLeft)
            buffer.writeShort(g.marginTop)
            buffer.writeShort(g.marginRight)
            buffer.writeShort(g.marginBottom)
            buffer.writeShort(g.bitmapWidth)
            buffer.writeShort(g.bitmapHeight)
        }
        if (kerning.isNotEmpty()) {
            buffer.writeShort(kerning.size)
            for (k in kerning) {
                buffer.writeUTF8(k.key)
                buffer.writeShort(k.adjustment)
            }
        }
    }

    /** Register this font under [id] so `DrawBitmapFontText` can resolve it (data-op dispatch). */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        context.putObject(id, this)
    }

    override fun dump(): String = "DATA_BITMAP_FONT id=$id glyphs=${glyphs.size} kerning=${kerning.size}"

    override fun equals(other: Any?): Boolean =
        this === other || (other is BitmapFontData && id == other.id && glyphs == other.glyphs && kerning == other.kerning)

    override fun hashCode(): Int = 31 * (31 * id + glyphs.hashCode()) + kerning.hashCode()

    companion object : OperationReader {
        const val VERSION_2 = 1

        override fun read(buffer: WireBuffer, operations: MutableList<Operation>) {
            val id = buffer.readInt()
            val versionAndCount = buffer.readInt()
            val version = versionAndCount ushr 16
            val count = versionAndCount and 0xFFFF
            val glyphs = ArrayList<Glyph>(count)
            repeat(count) {
                glyphs += Glyph(
                    chars = buffer.readUTF8(),
                    bitmapId = buffer.readInt(),
                    marginLeft = buffer.readShort(),
                    marginTop = buffer.readShort(),
                    marginRight = buffer.readShort(),
                    marginBottom = buffer.readShort(),
                    bitmapWidth = buffer.readShort(),
                    bitmapHeight = buffer.readShort(),
                )
            }
            val kerning = ArrayList<KerningEntry>()
            if (version >= VERSION_2) {
                val kCount = buffer.readShort()
                repeat(kCount) {
                    kerning += KerningEntry(buffer.readUTF8(), buffer.readShort())
                }
            }
            operations += BitmapFontData(id, glyphs, kerning)
        }
    }
}
