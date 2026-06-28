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
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import com.tneff.kmpremotecompose.remote.wire.WireTypes

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
) : Operation, PaintOperation {

    // REM-37 c_text: render-only draw origin set by LayoutMeasure — x = measured left − text bounds.left,
    // baselineY = measured top − text bounds.top (the negative ascent). Not serialized → byte-safe.
    private var drawX = 0f
    private var baselineY = 0f
    private var positioned = false

    // REM-74 (FC-D1): the measured component box (absolute top-left + size) — needed for multi-line wrap
    // (maxWidth) + complex-text positioning. Set by LayoutMeasure alongside [setTextDraw]. Not serialized.
    private var boxX = 0f
    private var boxY = 0f
    private var boxW = 0f
    private var boxH = 0f

    /** Called by [com.tneff.kmpremotecompose.remote.player.core.LayoutMeasure] with the measured origin. */
    fun setTextDraw(x: Float, baseline: Float) {
        drawX = x; baselineY = baseline; positioned = true
    }

    /** REM-74: the measured component box (absolute) — drives wrap decision + complex-text origin. */
    fun setTextBox(x: Float, y: Float, w: Float, h: Float) {
        boxX = x; boxY = y; boxW = w; boxH = h
    }

    /**
     * Emit the component text at its measured position. **REM-74 (FC-D1):** route to the **complex**
     * (`layoutComplexText`/`drawComplexText`) path when upstream would — mirroring `CoreText.java`'s
     * `textLayout()` precondition (lines 777-811):
     *
     *   `forceComplex || (width > maxWidth && maxLines > 1 && maxWidth > 0)`
     *
     * where `forceComplex` is true for any of: an ellipsis overflow (END/START/MIDDLE), `letterSpacing≠0`,
     * `lineHeightMultiplier≠1` (upstream field default is **1f**, not 0), `lineHeightAdd>0`, underline,
     * strikethrough, `justification>0`, `breakStrategy>0`, `hyphenation>0`, or a `\n`/`\t` in the string.
     * This is what makes single-line **END-ellipsis** text (e.g. `text_refresh_bug.rc`: overflow=3,
     * maxLines=1) truncate with "…" instead of overflowing/clipping. Otherwise keep the **exact
     * single-line** `drawTextRun` path (REM-37) so non-wrapping component text stays pixel-identical
     * (Bein-2). The text must already be loaded (DATA_TEXT ran earlier in the walk / the measure pre-load).
     */
    override fun paint(context: RemoteContext, paint: PaintContext) {
        val text = context.getText(textId)
        if (!positioned || text == null) return
        paint.savePaint()
        applyStyle(context, paint)
        val maxLines = paramInt(P_MAX_LINES) ?: Int.MAX_VALUE // upstream default = unlimited (wrap)
        val overflow = paramInt(P_OVERFLOW) ?: 0
        // Upstream's forceComplex preconditions (CoreText.java:782-805). NOTE the lineHeightMultiplier
        // default: upstream's mLineHeightMultiplier is 1f, so the "is-set" test is `!= 1f` (a 0f default
        // here would force-complex EVERY text). This default differs from the renderer's layout-call
        // convention below (0f ⇒ "use default lineHeight"), so the two are computed separately on purpose.
        val lineHeightMult = paramFloat(P_LINE_HEIGHT_MULT) ?: 1f
        val forceComplex =
            overflow == OVERFLOW_ELLIPSIS ||
                overflow == OVERFLOW_START_ELLIPSIS ||
                overflow == OVERFLOW_MIDDLE_ELLIPSIS ||
                (paramFloat(P_LETTER_SPACING) ?: 0f) != 0f ||
                lineHeightMult != 1f ||
                (paramFloat(P_LINE_HEIGHT_ADD) ?: 0f) > 0f ||
                paramBool(P_UNDERLINE) ||
                paramBool(P_STRIKETHROUGH) ||
                (paramInt(P_JUSTIFICATION) ?: 0) > 0 ||
                (paramInt(P_BREAK_STRATEGY) ?: 0) > 0 ||
                (paramInt(P_HYPHENATION) ?: 0) > 0 ||
                text.contains('\n') || text.contains('\t')
        val wraps = forceComplex || (boxW > 0f && maxLines > 1 && singleLineWidth(paint) > boxW)
        if (wraps) {
            val layout = paint.layoutComplexText(
                textId, 0, -1,
                alignment = paramInt(P_TEXT_ALIGN) ?: 0,
                overflow = overflow,
                maxLines = maxLines,
                maxWidth = boxW, maxHeight = boxH,
                letterSpacing = paramFloat(P_LETTER_SPACING) ?: 0f,
                lineHeightAdd = paramFloat(P_LINE_HEIGHT_ADD) ?: 0f,
                lineHeightMultiplier = paramFloat(P_LINE_HEIGHT_MULT) ?: 0f, // renderer: 0f ⇒ default lineHeight
                lineBreakStrategy = paramInt(P_BREAK_STRATEGY) ?: 0,
                hyphenationFrequency = paramInt(P_HYPHENATION) ?: 0,
                justificationMode = paramInt(P_JUSTIFICATION) ?: 0,
                useUnderline = paramBool(P_UNDERLINE),
                strikethrough = paramBool(P_STRIKETHROUGH),
                flags = paramInt(P_TEXT_FLAGS) ?: 0,
            )
            // drawComplexText paints at the canvas origin → translate to the box top-left first.
            paint.matrixSave()
            paint.matrixTranslate(boxX, boxY)
            paint.drawComplexText(layout)
            paint.matrixRestore()
        } else {
            paint.drawTextRun(textId, 0, -1, 0, 1, drawX, baselineY, false)
        }
        paint.restorePaint()
    }

    /** The single-line text width (for the wrap decision), via the renderer's getTextBounds. */
    private fun singleLineWidth(paint: PaintContext): Float {
        val b = FloatArray(4)
        paint.getTextBounds(textId, 0, -1, 0, b)
        return b[2] - b[0]
    }

    private fun param(id: Int): ByteArray? = params.firstOrNull { it.id == id }?.value
    private fun paramInt(id: Int): Int? = param(id)?.let { if (it.size >= 4) intOf(it) else (it[0].toInt() and 0xFF) }
    private fun paramFloat(id: Int): Float? = param(id)?.let { Float.fromBits(intOf(it)) }
    private fun paramBool(id: Int): Boolean = param(id)?.let { it.isNotEmpty() && it[0].toInt() != 0 } ?: false

    /**
     * Apply this component's TextStyle params (REM-37) to the shared paint state via the canonical
     * `applyPaint` seam, so the text renderer draws/measures it styled: `P_COLOR`/`P_COLOR_ID` → text
     * color (the colorId path chains the ColorExpression eval), `P_FONT_SIZE` → text size. Float params
     * may be NaN variable refs → resolved against the store. Call inside save/restorePaint (caller).
     * `P_FONT_STYLE`/`P_FONT_WEIGHT` → italic/weight via [PaintContext.applyTextStyle] (read by the renderer).
     */
    fun applyStyle(context: RemoteContext, paint: PaintContext) {
        var color: Int? = null
        var size = DEFAULT_FONT_SIZE // TextStyle default when no P_FONT_SIZE param (upstream = 36, not 16)
        var fontStyle = 0 // 0 = normal, 1 = italic
        var fontWeight = 0 // CSS 100–900, 0 = renderer default
        for (p in params) when (p.id) {
            // mirror upstream applyStyle's isDefault skip: default color (black) / colorId (-1) ⇒ leave renderer default.
            P_COLOR -> intOf(p.value).let { if (it != DEFAULT_COLOR) color = it }
            P_COLOR_ID -> intOf(p.value).let { if (it != DEFAULT_COLOR_ID) color = context.getColor(it) }
            P_FONT_SIZE -> {
                val raw = Float.fromBits(intOf(p.value))
                size = if (raw.isNaN()) context.getFloat(WireTypes.idFromNan(raw)) else raw
            }
            P_FONT_STYLE -> fontStyle = intOf(p.value)
            P_FONT_WEIGHT -> {
                val raw = Float.fromBits(intOf(p.value))
                fontWeight = (if (raw.isNaN()) context.getFloat(WireTypes.idFromNan(raw)) else raw).toInt()
            }
        }
        val b = PaintData.Builder().textSize(size)
        color?.let { b.color(it) }
        paint.applyPaint(b.build())
        paint.applyTextStyle(fontStyle, fontWeight) // italic/weight → paint state (read by the renderer; dev-1)
    }

    /** Big-endian int from a 4-byte param value (wire order). */
    private fun intOf(v: ByteArray): Int =
        (v[0].toInt() and 0xFF shl 24) or (v[1].toInt() and 0xFF shl 16) or
            (v[2].toInt() and 0xFF shl 8) or (v[3].toInt() and 0xFF)

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

        // TextStyle param ids used for rendering (REM-37): color, colorId, fontSize, fontStyle, fontWeight.
        private const val P_COLOR = 3
        private const val P_COLOR_ID = 4
        private const val P_FONT_SIZE = 5
        private const val P_FONT_STYLE = 6
        private const val P_FONT_WEIGHT = 7

        // REM-74 (FC-D1): complex-text layout param ids (mirror the PARAM_TYPE map field numbers).
        private const val P_TEXT_ALIGN = 9
        private const val P_OVERFLOW = 10
        private const val P_MAX_LINES = 11
        private const val P_LETTER_SPACING = 12
        private const val P_LINE_HEIGHT_ADD = 13
        private const val P_LINE_HEIGHT_MULT = 14
        private const val P_BREAK_STRATEGY = 15
        private const val P_HYPHENATION = 16
        private const val P_JUSTIFICATION = 17
        private const val P_UNDERLINE = 18
        private const val P_STRIKETHROUGH = 19
        private const val P_TEXT_FLAGS = 23

        // Overflow modes (upstream CoreText OVERFLOW_*). The three ellipsis modes force the complex path.
        private const val OVERFLOW_ELLIPSIS = 3        // END ellipsis
        private const val OVERFLOW_START_ELLIPSIS = 4
        private const val OVERFLOW_MIDDLE_ELLIPSIS = 5

        // Upstream TextStyle defaults — params at these values mean "renderer default" (skip applying).
        private const val DEFAULT_COLOR = 0xFF000000.toInt()
        private const val DEFAULT_COLOR_ID = -1
        private const val DEFAULT_FONT_SIZE = 36f

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
