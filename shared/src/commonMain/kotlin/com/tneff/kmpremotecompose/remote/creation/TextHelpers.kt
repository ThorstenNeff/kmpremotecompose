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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.core.operations.TextFromFloat
import com.tneff.kmpremotecompose.remote.core.operations.TextLookup
import com.tneff.kmpremotecompose.remote.core.operations.TextMeasure
import com.tneff.kmpremotecompose.remote.core.operations.TextMerge
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawText
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextAnchored
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextOnPath
import com.tneff.kmpremotecompose.remote.core.operations.layout.CoreText
import com.tneff.kmpremotecompose.remote.wire.WireTypes

/**
 * Text helpers (REM-87 / E3). Resource emitters return the allocated region-0 id; draw helpers are
 * void.
 *
 * **id-allocation (E5 byte-contract).** `addText` / `createTextFromFloat` / `textMeasure` are
 * **id-bearing** (region-0 plain pool — see [IdAllocator]); `drawTextRun` / `drawTextAnchored` /
 * `drawTextOnPath` reference existing text/path ids and are not id-bearing.
 */

/**
 * `DATA_TEXT` — register [text] under a freshly allocated region-0 id and return it. Mirrors
 * upstream `RemoteComposeWriter.addText`.
 */
fun RemoteComposeContext.addText(text: String): Int {
    val id = ids.nextId()
    add(TextData(id, text))
    return id
}

/**
 * `DRAW_TEXT_RUN` — draw a run `[start, end)` of the text registered at [textId], with `[contextStart,
 * contextEnd)` as the bidi context for shaping. [x]/[y] are baseline coords (may be NaN-encoded
 * variable refs); [rtl] flips run direction.
 */
fun RemoteComposeContext.drawTextRun(
    textId: Int,
    start: Int,
    end: Int,
    contextStart: Int,
    contextEnd: Int,
    x: Number,
    y: Number,
    rtl: Boolean = false,
) {
    add(
        DrawText(
            textId = textId,
            start = start,
            end = end,
            contextStart = contextStart,
            contextEnd = contextEnd,
            x = x.toFloat(),
            y = y.toFloat(),
            rtl = rtl,
        ),
    )
}

/**
 * `DRAW_TEXT_ANCHOR` — draw the full text at [textId] anchored to ([x], [y]) by pan factors
 * ([panX]/[panY] in `[-1..1]`, `panX=0` = centred). [flags] mirror upstream anchor flag bits
 * (see [DrawTextAnchored.ANCHOR_TEXT_RTL] / [DrawTextAnchored.ANCHOR_MONOSPACE_MEASURE] /
 * [DrawTextAnchored.BASELINE_RELATIVE]).
 */
fun RemoteComposeContext.drawTextAnchored(
    textId: Int,
    x: Number,
    y: Number,
    panX: Number = 0f,
    panY: Number = 0f,
    flags: Int = 0,
) {
    add(
        DrawTextAnchored(
            textId = textId,
            x = x.toFloat(),
            y = y.toFloat(),
            panX = panX.toFloat(),
            panY = panY.toFloat(),
            flags = flags,
        ),
    )
}

/**
 * `DRAW_TEXT_ON_PATH` — draw the text registered at [textId] along the path registered at [pathId].
 * Wire stores [vOffset] before [hOffset] (mirrors upstream `DrawTextOnPath` wire order; the rendering
 * primitive's arg order is the opposite — handled inside the op).
 */
fun RemoteComposeContext.drawTextOnPath(
    textId: Int,
    pathId: Int,
    vOffset: Number = 0f,
    hOffset: Number = 0f,
) {
    add(DrawTextOnPath(textId, pathId, vOffset.toFloat(), hOffset.toFloat()))
}

/**
 * `TEXT_FROM_FLOAT` — bind a freshly allocated text id to a formatted-float producer. [value] is
 * either a literal float or a NaN-encoded variable ref. [digitsBefore]/[digitsAfter] follow upstream
 * digit-count semantics; [flags] selects pre-/post-pad mode (see [TextFromFloat] for the bit
 * layout). Returns the new region-0 text id.
 */
fun RemoteComposeContext.createTextFromFloat(
    value: Number,
    digitsBefore: Int,
    digitsAfter: Int,
    flags: Int = 0,
): Int {
    val id = ids.nextId()
    add(TextFromFloat(id, value.toFloat(), digitsBefore, digitsAfter, flags))
    return id
}

/**
 * `TEXT_MEASURE` — register a measured dimension of [textId] under a freshly allocated region-0 id.
 * [type] selects which dimension (upstream: `0` = width, `1` = height). Pin for W#3: id-bearing,
 * pulls from the plain pool.
 */
fun RemoteComposeContext.textMeasure(textId: Int, type: Int): Int {
    val id = ids.nextId()
    add(TextMeasure(id, textId, type))
    return id
}

/**
 * `TEXT_MERGE` (REM-119) — concatenate the texts at [srcId1] and [srcId2] into a freshly
 * allocated region-0 id and return it. Mirrors upstream `RemoteComposeBuffer.textMerge(textId,
 * id1, id2)` — wire shape: opcode + int textId + int srcId1 + int srcId2 = 13 bytes.
 *
 * **Render binding:** [TextMerge] implements `VariableSupport.apply` — Phase A reads the two
 * source texts from the context store and writes their concatenation under [textId]. Missing
 * source ⇒ empty string (fail-soft, mirror upstream).
 */
fun RemoteComposeContext.textMerge(srcId1: Int, srcId2: Int): Int {
    val id = ids.nextId()
    add(TextMerge(id, srcId1, srcId2))
    return id
}

/**
 * `TEXT_LOOKUP` (REM-119) — bind a freshly allocated text id to a lookup `dataSet[index]`
 * against the id-list registered at [dataSet] (a NaN-encoded id-ref, mirror upstream
 * `Utils.idFromNan(dataSet)`); [index] is either a literal float or a NaN-encoded variable ref.
 *
 * Returns the allocated text id. Wire shape: opcode + int textId + int dataSet + float index
 * = 13 bytes.
 *
 * Used by chart-label demos (`good_pie_chart`, `pie_chart2`, `spread_sheet`, etc.).
 */
fun RemoteComposeContext.textLookup(dataSet: Float, index: Number): Int {
    val id = ids.nextId()
    add(TextLookup(textId = id, dataSet = WireTypes.idFromNan(dataSet), index = index.toFloat()))
    return id
}

/**
 * `CORE_TEXT` (REM-149) — emit a styled text component referencing the already-allocated [textId].
 * **Not id-bearing**: [textId] references an existing text id (from [addText] / [createTextFromFloat]
 * / [textLookup] / [textMerge]).
 *
 * Each [CoreText.Param] carries a 1-byte TextStyle parameter id (1..26) and the raw value bytes,
 * exactly as wide as the parameter's TextStyle type requires (see upstream `TextStyle.PARAMETERS` /
 * the [CoreText] reader for the type map). Construct typed params via [coreTextIntParam] /
 * [coreTextFloatParam] / [coreTextShortParam] / [coreTextByteParam] / [coreTextBoolParam] —
 * those factories produce byte-faithful BE encodings (and `toRawBits()` for Float, preserving
 * NaN-encoded variable refs verbatim).
 *
 * Profile: CORE_TEXT lives in the AndroidX overlay — the surrounding `document(...)` must open with
 * a profile carrying it (e.g. `Profile(operationsProfiles = Operations.PROFILE_ANDROIDX, ...)`).
 */
fun RemoteComposeContext.coreText(textId: Int, params: List<CoreText.Param>) {
    add(CoreText(textId, params.map { CoreText.Param(it.id, it.value.copyOf()) }))
}

/** Typed BE-int (4B) CORE_TEXT param — covers P_INT (TextStyle ids 1..4, 6, 8..11, 15..17, 23, 24). */
fun coreTextIntParam(id: Int, value: Int): CoreText.Param =
    CoreText.Param(id, coreTextIntBeBytes(value))

/**
 * Typed BE-float (4B) CORE_TEXT param via [Float.toRawBits] — covers P_FLOAT (TextStyle ids 5, 7,
 * 12, 13, 14, 25, 26). W14 NaN-bits preservation: a NaN-encoded variable ref (constructed via
 * `Float.fromBits(0xFF800000.toInt() or id)`) round-trips byte-exact to the wire.
 */
fun coreTextFloatParam(id: Int, value: Float): CoreText.Param =
    CoreText.Param(id, coreTextIntBeBytes(value.toRawBits()))

/**
 * Typed BE-short (2B) CORE_TEXT param — covers the P_SHORT wire type.
 *
 * **Forward-looking (corpus-unexercised).** No TextStyle parameter id in the upstream PARAM_TYPE
 * map currently uses P_SHORT, and no `c_modifier_*`/`text_*` corpus fixture exercises this factory.
 * It exists for **API-completeness symmetry** with the upstream `CommandParameters.P_SHORT` wire
 * type so a future TextStyle param that selects P_SHORT can author byte-faithfully without a new
 * factory. The factory is byte-equivalence-pinned by the procedural helper's defensive `.copyOf()`
 * — its 2B BE encoding is mechanical, not corpus-validated.
 */
fun coreTextShortParam(id: Int, value: Int): CoreText.Param =
    CoreText.Param(id, byteArrayOf((value ushr 8).toByte(), value.toByte()))

/**
 * Typed 1-byte CORE_TEXT param — covers the P_BYTE wire type.
 *
 * **Forward-looking (corpus-unexercised).** No TextStyle parameter id in the upstream PARAM_TYPE
 * map currently uses P_BYTE, and no corpus fixture exercises this factory. Same forward-looking
 * rationale as [coreTextShortParam] — kept for upstream-symmetric API surface; the 1B encoding is
 * mechanical (not corpus-validated).
 */
fun coreTextByteParam(id: Int, value: Int): CoreText.Param =
    CoreText.Param(id, byteArrayOf(value.toByte()))

/** Typed 1-byte boolean CORE_TEXT param — covers P_BOOLEAN (TextStyle ids 18, 19, 22). */
fun coreTextBoolParam(id: Int, value: Boolean): CoreText.Param =
    CoreText.Param(id, byteArrayOf(if (value) 1 else 0))

private fun coreTextIntBeBytes(v: Int): ByteArray = byteArrayOf(
    (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
)
