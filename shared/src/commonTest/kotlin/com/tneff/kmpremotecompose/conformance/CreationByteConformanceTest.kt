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
package com.tneff.kmpremotecompose.conformance

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.creation.RcExpression
import com.tneff.kmpremotecompose.remote.creation.RcPaint
import com.tneff.kmpremotecompose.remote.creation.ROOT_ALIGNMENT_CENTER
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCALE_FILL_BOUNDS
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCALE_FIT
import com.tneff.kmpremotecompose.remote.creation.ROOT_SCROLL_NONE
import com.tneff.kmpremotecompose.remote.creation.ROOT_SIZING_SCALE
import com.tneff.kmpremotecompose.remote.creation.addDataMapIds
import com.tneff.kmpremotecompose.remote.creation.addInt
import com.tneff.kmpremotecompose.remote.creation.addPathData
import com.tneff.kmpremotecompose.remote.creation.addText
import com.tneff.kmpremotecompose.remote.creation.colorExpressionHsv
import com.tneff.kmpremotecompose.remote.creation.createTextFromFloat
import com.tneff.kmpremotecompose.remote.creation.dataMapEntry
import com.tneff.kmpremotecompose.remote.creation.dataMapLookup
import com.tneff.kmpremotecompose.remote.creation.document
import com.tneff.kmpremotecompose.remote.creation.drawOval
import com.tneff.kmpremotecompose.remote.creation.drawPath
import com.tneff.kmpremotecompose.remote.creation.drawRect
import com.tneff.kmpremotecompose.remote.creation.drawTextAnchored
import com.tneff.kmpremotecompose.remote.creation.floatExpression
import com.tneff.kmpremotecompose.remote.creation.matrixRestore
import com.tneff.kmpremotecompose.remote.creation.matrixSave
import com.tneff.kmpremotecompose.remote.creation.matrixScale
import com.tneff.kmpremotecompose.remote.creation.matrixTranslate
import com.tneff.kmpremotecompose.remote.creation.paint
import com.tneff.kmpremotecompose.remote.creation.setRootContentBehavior
import com.tneff.kmpremotecompose.remote.creation.textMeasure
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * E5 — Creation-Byte-Conformance (REM-84, test-2). The §2-WRITE-side gate: a `.rc` produced by our
 * KMP DSL must be **byte-identical** to the upstream-procedural oracle (`procedure_*` corpus fixtures).
 * This is the canonical E5 conformance gate (dev-3 also smoke-tests fixtures as they build the ops;
 * this suite is the holistic byte-equality tracker test-2 owns).
 *
 * Three-stage strategy (TechSpec-REM-E §4): (1) round-trip self-consistency, (2) decode-and-inspect,
 * (3) byte-equality vs oracle. **GATE CLOSED 4/4 (post E3/E4/REM-103, Host + iOS):** stage 1
 * (round-trip/determinism), the stage-3 prolog checkpoint (REM-85), `procedure_simple2` (REM-86), and
 * **all four watchpoint fixtures** `procedure_gradient1` / `procedure_center_text1` / `procedure_look_up1`
 * / `procedure_text_path_effects` are full-byte-equal to the oracle — covering transform + RPN
 * ANIMATED_FLOAT + TEXT_MEASURE/TEXT_FROM_FLOAT + ID_MAP/DATA_MAP_LOOKUP + linear/sweep gradient +
 * HSV COLOR_EXPRESSIONS + DATA_PATH (REM-103 [addPathData]) + stroke/fill/clear paint surfaces. The
 * whole Creation-DSL byte gate ("create on a server" 100%-byte milestone) is green. Replication target:
 * `docs/e5-creation-byte-conformance-prep.md` + the canonical `docs/TECHSPEC-E5-id-order-reference.md`.
 */
class CreationByteConformanceTest {

    private fun oracle(name: String): ByteArray = RcCorpus.readFixture("corpus/$name.rc")

    // ---- Stage 1: round-trip self-consistency (ACTIVE — proves the harness + E1 encode are consistent) ----

    /** E1 emits a header-only doc; decode→reEncode must be byte-stable (the byte-bewiesene L1 codec). */
    @Test
    fun e1_document_roundTrips_byteStable() {
        val bytes = document(width = 300, height = 300, contentDescription = "Clock") { }
        val reEncoded = RcDocumentCodec.decode(bytes).reEncode()
        assertContentEquals(bytes, reEncoded, "E1 document{} output must round-trip byte-stable through the L1 codec")
    }

    /** Deterministic byte output: the same script must encode identically every call (no global state). */
    @Test
    fun e1_document_isDeterministic() {
        val a = document(width = 300, height = 300, contentDescription = "Clock") { }
        val b = document(width = 300, height = 300, contentDescription = "Clock") { }
        assertContentEquals(a, b, "same DSL script must produce identical bytes (id-allocation per-document)")
    }

    // ---- Stage 3: byte-equality vs the oracle fixtures ----
    // Targets (op-sequence + id-order) documented in docs/e5-creation-byte-conformance-prep.md §2.
    // Each asserts: document(300,300,contentDescription="Clock"){ <replicated ops> } == oracle bytes.

    /**
     * E2 milestone (REM-86, ACTIVE/green): full byte-equality vs procedure_simple2 (82 B).
     * Body = setRootContentBehavior(NONE, CENTER, SCALE, SCALE_FIT) + drawOval(0,0,WIN_W,WIN_H), where
     * the right/bottom coords are the region-0 system-variable ids 5/6 (ID_WINDOW_WIDTH/HEIGHT),
     * NaN-boxed into the float slots (decoded raw: 0xff800005 / 0xff800006 — see prep §3a). Exercises
     * the E2 draw + RCB surface on top of REM-85's prolog.
     */
    @Test
    fun simple2_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") {
            setRootContentBehavior(
                scroll = ROOT_SCROLL_NONE,
                alignment = ROOT_ALIGNMENT_CENTER,
                sizing = ROOT_SIZING_SCALE,
                mode = ROOT_SCALE_FIT,
            )
            drawOval(
                left = 0f,
                top = 0f,
                right = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH),
                bottom = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT),
            )
        }
        assertContentEquals(oracle("procedure_simple2"), produced, "DSL must byte-match procedure_simple2 oracle")
    }

    /**
     * E3/E4 milestone (REM-90/REM-92, ACTIVE/green): full byte-equality vs procedure_gradient1 (267 B).
     * Decoded op map (TmpGradient1Decode probe → docs/e5-creation-byte-conformance-prep.md §2):
     *   PAINT_VALUES(12) = linearGradient((0,0)→(0, WIN_H), [0xff00ff00, 0xff0022ff], tile=REPEAT) + textSize(64);
     *   id43 = WIN_W*0.5, id44 = WIN_H*0.5, id45 = (CONTINUOUS_SEC % 2) − 1 (RPN ANIMATED_FLOATs);
     *   MATRIX_SCALE(scaleX=id45, scaleY=1, pivot=(id43,id44)) + DRAW_OVAL(0,0,WIN_W,WIN_H) inside a save/restore;
     *   DATA_TEXT(id46 "gradient") + DRAW_TEXT_ANCHOR(id46, x=id43, y=id44).
     * Watchpoints: gradient y1 + oval r/b are NaN-boxed system-var ids 5/6 (not literals); the three
     * ANIMATED_FLOAT operator tokens are NaN-boxed RPN ops (MUL/MOD/SUB) at RcExpression.OFFSET.
     */
    @Test
    fun gradient1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") {
            setRootContentBehavior(
                scroll = ROOT_SCROLL_NONE,
                alignment = ROOT_ALIGNMENT_CENTER,
                sizing = ROOT_SIZING_SCALE,
                mode = ROOT_SCALE_FILL_BOUNDS,
            )
            paint {
                linearGradient(
                    x0 = 0f, y0 = 0f,
                    x1 = 0f, y1 = RcExpression.WINDOW_HEIGHT,
                    colors = intArrayOf(0xff00ff00.toInt(), 0xff0022ff.toInt()),
                    stops = null,
                    tile = RcPaint.TILE_REPEAT,
                )
                textSize(64f)
            }
            val centerX = floatExpression(RcExpression.WINDOW_WIDTH, 0.5f, RcExpression.MUL)
            val centerY = floatExpression(RcExpression.WINDOW_HEIGHT, 0.5f, RcExpression.MUL)
            val scale = floatExpression(
                RcExpression.CONTINUOUS_SEC, 2.0f, RcExpression.MOD, 1.0f, RcExpression.SUB,
            )
            matrixSave()
            matrixScale(scaleX = scale, scaleY = 1.0f, centerX = centerX, centerY = centerY)
            drawOval(
                left = 0f,
                top = 0f,
                right = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH),
                bottom = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT),
            )
            matrixRestore()
            val gradientText = addText("gradient")
            drawTextAnchored(textId = gradientText, x = centerX, y = centerY)
        }
        assertContentEquals(oracle("procedure_gradient1"), produced, "DSL must byte-match procedure_gradient1 oracle")
    }

    /**
     * E3/E4 milestone (REM-90/REM-92, ACTIVE/green): full byte-equality vs procedure_center_text1 (449 B).
     * Builds on gradient1's transform block (ids 43/44/45 = WIN_W*0.5, WIN_H*0.5, (CONTINUOUS_SEC%2)−1),
     * then a measured-and-centred dynamic text box:
     *   id46 = 99 − (TIME_IN_SEC % 100); id47 = TEXT_FROM_FLOAT(id46, before=3, after=0, flags=3);
     *   id48/id49 = TEXT_MEASURE(id47, width/height); id50 = id43 − id48/2; id51 = id44 − id49/2;
     *   id52 = id51 + id49 (bottom); id53 = id50 + id48 (right);
     *   PAINT(stroke, w=2, black) + DRAW_RECT(id50,id51,id53,id52); PAINT(fill, blue) + DRAW_TEXT_ANCHOR(id47, id43, id44).
     * Measured ids are embedded back into RPN via WireTypes.asNan(id) (TEXT_MEASURE returns the raw pool id).
     */
    @Test
    fun centerText1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") {
            setRootContentBehavior(
                scroll = ROOT_SCROLL_NONE,
                alignment = ROOT_ALIGNMENT_CENTER,
                sizing = ROOT_SIZING_SCALE,
                mode = ROOT_SCALE_FILL_BOUNDS,
            )
            paint {
                color(0xffff0000.toInt())
                textSize(64f)
            }
            val centerX = floatExpression(RcExpression.WINDOW_WIDTH, 0.5f, RcExpression.MUL)
            val centerY = floatExpression(RcExpression.WINDOW_HEIGHT, 0.5f, RcExpression.MUL)
            val scale = floatExpression(
                RcExpression.CONTINUOUS_SEC, 2.0f, RcExpression.MOD, 1.0f, RcExpression.SUB,
            )
            matrixSave()
            matrixScale(scaleX = scale, scaleY = 1.0f, centerX = centerX, centerY = centerY)
            drawOval(
                left = 0f,
                top = 0f,
                right = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH),
                bottom = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT),
            )
            matrixRestore()
            val secs = floatExpression(
                99.0f, RcExpression.TIME_IN_SEC, 100.0f, RcExpression.MOD, RcExpression.SUB,
            )
            val text = createTextFromFloat(value = secs, digitsBefore = 3, digitsAfter = 0, flags = 3)
            val width = textMeasure(textId = text, type = 0)
            val height = textMeasure(textId = text, type = 1)
            val left = floatExpression(centerX, WireTypes.asNan(width), 2.0f, RcExpression.DIV, RcExpression.SUB)
            val top = floatExpression(centerY, WireTypes.asNan(height), 2.0f, RcExpression.DIV, RcExpression.SUB)
            val bottom = floatExpression(top, WireTypes.asNan(height), RcExpression.ADD)
            val right = floatExpression(left, WireTypes.asNan(width), RcExpression.ADD)
            paint {
                style(RcPaint.STYLE_STROKE)
                strokeWidth(2f)
                color(0xff000000.toInt())
            }
            drawRect(left = left, top = top, right = right, bottom = bottom)
            paint {
                color(0xff0000ff.toInt())
                style(RcPaint.STYLE_FILL)
            }
            drawTextAnchored(textId = text, x = centerX, y = centerY)
        }
        assertContentEquals(oracle("procedure_center_text1"), produced, "DSL must byte-match procedure_center_text1 oracle")
    }

    /**
     * E3/E4 milestone (REM-90/REM-92, ACTIVE/green): full byte-equality vs procedure_look_up1 (514 B).
     * The §2 collection-range watchpoint: ID_MAP lands at region-2 id `(2<<20)+42 = 2097194` (separate
     * from the region-0 plain pool), and DATA_MAP_LOOKUP references it verbatim.
     *   DATA_TEXT(id43 "John", id44 "David") + DATA_INT(id45 =32) feed ID_MAP{First→43, Last→44, DOB→45};
     *   ids 46/47/48 = the same WIN_W*0.5 / WIN_H*0.5 / scale transform block (matrix+oval);
     *   DATA_TEXT(id49 "First") is the lookup key; id50 = DATA_MAP_LOOKUP(map, key); id51/52 = TEXT_MEASURE(id50);
     *   id53..56 = centred box (centerX−w/2, centerY−h/2, +h, +w); PAINT(stroke) DRAW_RECT; PAINT(fill) DRAW_TEXT_ANCHOR.
     *
     * **Byte finding (flagged to PO for dev-3):** the two string-valued ID_MAP entries carry wire **type 0**,
     * not `DATA_MAP_TYPE_STRING` (=2); the int entry (DOB) is type 1 (= DATA_MAP_TYPE_INT, matches). So
     * `dataMapEntry`'s default (STRING=2) would diverge here — replicated with explicit literal types.
     */
    @Test
    fun lookUp1_bytesMatchOracle() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") {
            setRootContentBehavior(
                scroll = ROOT_SCROLL_NONE,
                alignment = ROOT_ALIGNMENT_CENTER,
                sizing = ROOT_SIZING_SCALE,
                mode = ROOT_SCALE_FILL_BOUNDS,
            )
            paint {
                color(0xffff0000.toInt())
                textSize(64f)
            }
            val john = addText("John")
            val david = addText("David")
            val dob = addInt(32)
            addDataMapIds(
                listOf(
                    // Wire types decoded from the oracle: strings=0, int=1 (NOT dataMapEntry's STRING=2 default).
                    dataMapEntry(name = "First", valueId = john, type = 0),
                    dataMapEntry(name = "Last", valueId = david, type = 0),
                    dataMapEntry(name = "DOB", valueId = dob, type = 1),
                ),
            )
            val centerX = floatExpression(RcExpression.WINDOW_WIDTH, 0.5f, RcExpression.MUL)
            val centerY = floatExpression(RcExpression.WINDOW_HEIGHT, 0.5f, RcExpression.MUL)
            val scale = floatExpression(
                RcExpression.CONTINUOUS_SEC, 2.0f, RcExpression.MOD, 1.0f, RcExpression.SUB,
            )
            matrixSave()
            matrixScale(scaleX = scale, scaleY = 1.0f, centerX = centerX, centerY = centerY)
            drawOval(
                left = 0f,
                top = 0f,
                right = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH),
                bottom = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT),
            )
            matrixRestore()
            val key = addText("First")
            val value = dataMapLookup(dataMapId = 2097194, keyStringId = key)
            val width = textMeasure(textId = value, type = 0)
            val height = textMeasure(textId = value, type = 1)
            val left = floatExpression(centerX, WireTypes.asNan(width), 2.0f, RcExpression.DIV, RcExpression.SUB)
            val top = floatExpression(centerY, WireTypes.asNan(height), 2.0f, RcExpression.DIV, RcExpression.SUB)
            val bottom = floatExpression(top, WireTypes.asNan(height), RcExpression.ADD)
            val right = floatExpression(left, WireTypes.asNan(width), RcExpression.ADD)
            paint {
                style(RcPaint.STYLE_STROKE)
                strokeWidth(2f)
                color(0xff000000.toInt())
            }
            drawRect(left = left, top = top, right = right, bottom = bottom)
            paint {
                color(0xff0000ff.toInt())
                style(RcPaint.STYLE_FILL)
            }
            drawTextAnchored(textId = value, x = centerX, y = centerY)
        }
        assertContentEquals(oracle("procedure_look_up1"), produced, "DSL must byte-match procedure_look_up1 oracle")
    }

    /**
     * E5 4/4 CLOSE (REM-103, ACTIVE/green): full byte-equality vs procedure_text_path_effects (8986 B,
     * 27 ops). Unblocked by the REM-103 one-shot [addPathData] emitter (the oracle bakes its geometry
     * as a single `DATA_PATH` op, count=2141 floats — the incremental PathBuilder produced a divergent
     * op-shape). The 2141 path floats are **sourced from the oracle's own DATA_PATH payload** (input
     * geometry, not hand-authored); the test validates that the full op sequence + id allocation + every
     * surrounding op is byte-faithful: HSV `COLOR_EXPRESSIONS` (mode 4, alpha 143, hue=id43),
     * SWEEP `sweepGradient` (3 colors incl. the colorId id46), RPN ANIMATED_FLOATs (DIV/MOD/MUL/SUB/ABS),
     * MATRIX scale/translate, 2×`DRAW_PATH`, stroke/fill paints, and `CLEAR_COLOR_FILTER`.
     * **DSL gap flagged to dev-3:** `RcPaint` has no `clearColorFilter()` helper for the bare
     * `PAINT_VALUES[CLEAR_COLOR_FILTER]` (tag 21) bundle → emitted here via the raw L1-proven [PaintData] op.
     */
    @Test
    fun textPathEffects1_bytesMatchOracle() {
        val oracleBytes = oracle("procedure_text_path_effects")
        // Source the path geometry from the oracle's single DATA_PATH op (firstFloat = byteStart + 9:
        // opcode(1) + wireId(4) + count(4)). Geometry is input data; the emitter framing is what we test.
        val (_, spans) = DocumentReader.inflateWithTrace(oracleBytes)
        val pathSpan = spans.first { it.name == "DATA_PATH" }
        fun be(o: Int): Int {
            var v = 0
            for (j in 0 until 4) v = (v shl 8) or (oracleBytes[o + j].toInt() and 0xff)
            return v
        }
        val pathCount = be(pathSpan.byteStart + 5)
        val pathFloats = FloatArray(pathCount) { Float.fromBits(be(pathSpan.byteStart + 9 + it * 4)) }

        val produced = document(width = 300, height = 300, contentDescription = "Clock") {
            setRootContentBehavior(
                scroll = ROOT_SCROLL_NONE,
                alignment = ROOT_ALIGNMENT_CENTER,
                sizing = ROOT_SIZING_SCALE,
                mode = ROOT_SCALE_FILL_BOUNDS,
            )
            val hue = floatExpression(
                RcExpression.CONTINUOUS_SEC, 6.2831855f, RcExpression.DIV, 1.0f, RcExpression.MOD,
            ) // id43 = (continuousSec / 2π) % 1
            val centerX = floatExpression(RcExpression.WINDOW_WIDTH, 0.5f, RcExpression.MUL) // id44
            val centerY = floatExpression(RcExpression.WINDOW_HEIGHT, 0.5f, RcExpression.MUL) // id45
            val hsv = colorExpressionHsv(hue = hue, saturation = 0.9f, value = 0.9f, alpha = 143) // id46
            val topY = floatExpression(centerY, 100.0f, RcExpression.SUB) // id47 = centerY − 100
            paint {
                sweepGradient(
                    centerX = centerX, centerY = centerY,
                    colors = intArrayOf(0xff0022ff.toInt(), hsv, 0xff0022ff.toInt()),
                )
                textSize(64f)
            }
            val scale = floatExpression(
                RcExpression.CONTINUOUS_SEC, 2.0f, RcExpression.MOD, 1.0f, RcExpression.SUB,
            ) // id48
            matrixSave()
            matrixScale(scaleX = scale, scaleY = 0.5f, centerX = centerX, centerY = centerY)
            drawOval(
                left = 0f, top = 0f,
                right = WireTypes.asNan(RemoteContext.ID_WINDOW_WIDTH),
                bottom = WireTypes.asNan(RemoteContext.ID_WINDOW_HEIGHT),
            )
            matrixRestore()
            matrixSave()
            matrixTranslate(centerX, topY)
            val path = addPathData(pathFloats, winding = 0) // id49
            drawPath(path)
            val strokeW = floatExpression(scale, RcExpression.ABS, 10.0f, RcExpression.MUL) // id50 = abs(id48)*10
            paint {
                style(RcPaint.STYLE_STROKE)
                strokeWidth(strokeW)
            }
            drawPath(path)
            paint { style(RcPaint.STYLE_FILL) }
            matrixRestore()
            add(PaintData(intArrayOf(PaintData.CLEAR_COLOR_FILTER))) // tag 21 — no RcPaint helper yet (dev-3)
            val text = addText("0123456789") // id51
            drawTextAnchored(textId = text, x = centerX, y = centerY)
        }
        assertContentEquals(oracleBytes, produced, "DSL must byte-match procedure_text_path_effects oracle")
    }

    // ---- Stage-3 early checkpoint: E1 prolog byte-faithfulness (the smallest end-to-end byte proof) ----

    /**
     * ACTIVE since REM-85 (byte-faithful prolog, develop fad13e2): an empty `document{}` emits exactly
     * the 48-byte prolog — flat-API v1.0.0 HEADER(29) + DATA_TEXT(id42 "Clock")(14) +
     * ROOT_CONTENT_DESCRIPTION(42)(5), byte-identical to every procedure_* oracle's first 48 bytes.
     * ROOT_CONTENT_BEHAVIOR is intentionally NOT here — it arrives in E2 via setRootContentBehavior.
     * This is the gating E1 byte-checkpoint; the four fixture targets build their bodies on top of it.
     */
    @Test
    fun e1Prolog_byteMatchesOracleHeaderBlock() {
        val produced = document(width = 300, height = 300, contentDescription = "Clock") { }
        val oracleProlog = oracle("procedure_gradient1").copyOfRange(0, 48)
        assertContentEquals(oracleProlog, produced, "empty document{} must equal the 48-byte flat-API prolog")
    }
}
