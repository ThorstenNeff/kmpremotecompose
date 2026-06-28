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

import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.ColorExpression
import com.tneff.kmpremotecompose.remote.core.operations.DataMapIds
import com.tneff.kmpremotecompose.remote.core.operations.DataMapLookup
import com.tneff.kmpremotecompose.remote.core.operations.FloatExpression
import com.tneff.kmpremotecompose.remote.core.operations.IntegerConstant
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.core.operations.TextMeasure
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootContentBehavior
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-92 (E4) — integration tests that compose the full E1-E4 surface against the **op-sequence**
 * extracted from the four E5 watchpoint fixtures (`docs/TECHSPEC-E5-id-order-reference.md`).
 *
 * These are **Stage-2** (decode-and-inspect) tests, not Stage-3 byte-equality — the exact RPN /
 * paint / draw operand values in each fixture aren't reproduced here (test-2 owns that against the
 * un-ignored corpus, REM-84 harness). What these pin is: **the DSL can compose the right op types
 * in the right order with the right ids**, so when test-2 plugs in the operand values, the bytes
 * land. If this fails for an id-allocation reason, the byte-equality harness will fail too.
 *
 * REM-90 (E3) already covered the look_up1 id-prefix through op #8 (ID_MAP). This story extends
 * coverage to op #11 (ANIMATED_FLOAT) and beyond.
 */
class E5SequenceTest {

    @Test
    fun lookUp1_idSequence_throughAnimatedFloats_matchesE5Reference() {
        // Replicates the id-allocation pattern of procedure_look_up1.rc (E5 doc, op #1 .. #11):
        //   42  DATA_TEXT "Clock"        (auto-emitted by REM-85 prolog from contentDescription)
        //   42  ROOT_CONTENT_DESCRIPTION (auto-emitted)
        //   _   ROOT_CONTENT_BEHAVIOR     (W#2 default)
        //   43  DATA_TEXT "John"
        //   44  DATA_TEXT "David"
        //   45  DATA_INT 32
        //   2097194  ID_MAP (region-2 anchor)
        //   46/47/48  ANIMATED_FLOAT
        var idJohn = -1; var idDavid = -1; var idAge = -1
        var idMap = -1
        var idF1 = -1; var idF2 = -1; var idF3 = -1
        val bytes = document(width = 200, height = 200, contentDescription = "Clock") {
            setRootContentBehavior() // W#2 default (0, 34, 2, 6)
            idJohn = addText("John")            // 43
            idDavid = addText("David")          // 44
            idAge = addInt(32)                  // 45
            idMap = addDataMapIds(              // region-2 = 2097194
                listOf(
                    dataMapEntry("John", idJohn),
                    dataMapEntry("David", idDavid),
                    dataMapEntry("age", idAge, DATA_MAP_TYPE_INT),
                ),
            )
            // ANIMATED_FLOAT id 46/47/48 — exact RPN bodies arbitrary; what we pin is the id-order.
            idF1 = (com.tneff.kmpremotecompose.remote.wire.WireTypes.idFromNan(
                floatExpression(0f),
            ))
            idF2 = (com.tneff.kmpremotecompose.remote.wire.WireTypes.idFromNan(
                floatExpression(60f, RcExpression.TIME_IN_SEC, RcExpression.MUL),
            ))
            idF3 = (com.tneff.kmpremotecompose.remote.wire.WireTypes.idFromNan(
                floatExpression(RcExpression.CONTINUOUS_SEC),
            ))
        }
        assertEquals(43, idJohn); assertEquals(44, idDavid); assertEquals(45, idAge)
        assertEquals(2097194, idMap)
        assertEquals(46, idF1); assertEquals(47, idF2); assertEquals(48, idF3)

        // Cross-check the op stream.
        val ops = DocumentReader.inflate(bytes).operations
        // Order: HEADER, DATA_TEXT(42), ROOT_CONTENT_DESCRIPTION, ROOT_CONTENT_BEHAVIOR,
        //        DATA_TEXT(43), DATA_TEXT(44), DATA_INT(45), ID_MAP(2097194),
        //        ANIMATED_FLOAT(46), ANIMATED_FLOAT(47), ANIMATED_FLOAT(48)
        val opcodes = ops.map { it.opcode }
        val expectedPrefix = listOf(
            Operations.HEADER,
            Operations.DATA_TEXT, // id 42 "Clock"
            Operations.ROOT_CONTENT_DESCRIPTION,
            Operations.ROOT_CONTENT_BEHAVIOR,
            Operations.DATA_TEXT, // id 43 John
            Operations.DATA_TEXT, // id 44 David
            Operations.DATA_INT,
            Operations.ID_MAP,
            Operations.ANIMATED_FLOAT,
            Operations.ANIMATED_FLOAT,
            Operations.ANIMATED_FLOAT,
        )
        assertEquals(expectedPrefix, opcodes.take(expectedPrefix.size))
        val rcb = ops.first { it is RootContentBehavior } as RootContentBehavior
        assertEquals(0, rcb.scroll); assertEquals(34, rcb.alignment)
        assertEquals(2, rcb.sizing); assertEquals(6, rcb.mode)
    }

    @Test
    fun lookUp1_dataMapLookup_andTextMeasure_chainCorrectly() {
        // Continues through op #17 (DATA_MAP_LOOKUP) and #18-#19 (TEXT_MEASURE x2):
        //   49 DATA_TEXT "First"
        //   50 DATA_MAP_LOOKUP id=50 dataMapId=2097194 key=49
        //   51 TEXT_MEASURE id=51 textId=50 type=0
        //   52 TEXT_MEASURE id=52 textId=50 type=1
        var idMap = -1; var idLookup = -1; var idMeasureW = -1; var idMeasureH = -1
        val bytes = document(width = 200, height = 200) {
            val a = addText("John")
            val b = addText("David")
            val ageId = addInt(32)
            idMap = addDataMapIds(
                listOf(
                    dataMapEntry("John", a),
                    dataMapEntry("David", b),
                    dataMapEntry("age", ageId, DATA_MAP_TYPE_INT),
                ),
            )
            floatExpression(0f); floatExpression(0f); floatExpression(0f) // 45/46/47
            val firstKey = addText("First") // 48
            idLookup = dataMapLookup(idMap, firstKey) // 49
            idMeasureW = textMeasure(idLookup, 0) // 50
            idMeasureH = textMeasure(idLookup, 1) // 51
        }
        assertEquals(49, idLookup)
        assertEquals(50, idMeasureW); assertEquals(51, idMeasureH)

        val ops = DocumentReader.inflate(bytes).operations
        val lookup = ops.first { it is DataMapLookup } as DataMapLookup
        assertEquals(2097194, lookup.dataMapId, "lookup references the full region-2-tagged id")
        val measureOps = ops.filterIsInstance<TextMeasure>()
        assertEquals(2, measureOps.size)
        assertEquals(idLookup, measureOps[0].textId)
        assertEquals(0, measureOps[0].type)
        assertEquals(1, measureOps[1].type)
    }

    @Test
    fun textPathEffects_animatedFloatPlusColorExpression_compose() {
        // Mirrors procedure_text_path_effects's op-shape: ANIMATED_FLOAT (46-49) → COLOR_EXPRESSIONS
        // — proves the chain produces COLOR_EXPRESSIONS over a recently allocated id.
        var idColor = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            floatExpression(0f); floatExpression(0f); floatExpression(0f) // 43/44/45
            idColor = colorExpressionInterpolate(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f) // 46
        }
        assertEquals(46, idColor)
        val ops = DocumentReader.inflate(bytes).operations
        val colorExpr = ops.first { it is ColorExpression } as ColorExpression
        assertEquals(46, colorExpr.id)
    }

    @Test
    fun gradient1_paintWithLinearGradient_composesAsSinglePaintValues() {
        // gradient1 has a PaintData with a GRADIENT bundle. Pin that paint{} block emits one
        // PAINT_VALUES op carrying the gradient slots (count = 10 for 2-colour linear, no stops).
        val bytes = document(width = 300, height = 300, contentDescription = "Clock") {
            paint {
                linearGradient(
                    x0 = 0f, y0 = 0f, x1 = 300f, y1 = 300f,
                    colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
                )
            }
        }
        val paints = DocumentReader.inflate(bytes).operations
            .filterIsInstance<com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData>()
        assertEquals(1, paints.size, "one paint { } block = one PAINT_VALUES")
        assertEquals(10, paints[0].values.size, "linear gradient with 2 colours, no stops, default tile")
    }

    @Test
    fun e4_idAllocations_doNotInterfere_acrossRegions() {
        // Cross-region sanity: a mixed sequence of region-0 (text/int/float-expr/measure/lookup),
        // region-2 (id-map), and region-1 (named variable) preserves all three counters.
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            assertEquals(IdAllocator.START_ARRAY, ids.peekArray())
            assertEquals(IdAllocator.START_VAR, ids.peekVariable())

            val t = addText("a")                                                  // plain 43
            val n = addInt(1)                                                     // plain 44
            val e = floatExpression(1f, 2f, RcExpression.ADD)                     // plain 45
            val nv = addNamedVariable("accent", 0)                                // var 1048618
            val m = addDataMapIds(listOf(dataMapEntry("k", t)))                   // array 2097194
            val lookup = dataMapLookup(m, t)                                      // plain 46
            val measure = textMeasure(t, 0)                                       // plain 47

            assertEquals(48, ids.peek())
            assertEquals(2097195, ids.peekArray())
            assertEquals(1048619, ids.peekVariable())

            // Ensure all ids are stable (no aliasing between counters).
            assertEquals(43, t)
            assertEquals(44, n)
            assertEquals(45, com.tneff.kmpremotecompose.remote.wire.WireTypes.idFromNan(e))
            assertEquals(1048618, nv)
            assertEquals(2097194, m)
            assertEquals(46, lookup)
            assertEquals(47, measure)
        }
    }
}
