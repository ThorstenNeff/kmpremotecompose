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
import com.tneff.kmpremotecompose.remote.core.operations.TextData
import com.tneff.kmpremotecompose.remote.core.operations.TextFromFloat
import com.tneff.kmpremotecompose.remote.core.operations.TextMeasure
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawText
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextAnchored
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextOnPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-90 (E3) — Text-helper byte tests. Stage-1 (round-trip) + Stage-2 (decode-and-inspect) per
 * helper, plus the W#3 id-bearing pin (addText/createTextFromFloat/textMeasure pull plain;
 * drawText* do not).
 */
class TextHelpersTest {

    @Test
    fun addText_allocatesPlainId_andEmitsDataText() {
        var id = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            // Prolog pulls id 42 for the content-description. addText pulls 43 next.
            id = addText("John")
        }
        assertEquals(43, id)
        val op = DocumentReader.inflate(bytes).operations.filterIsInstance<TextData>()
            .first { it.id == 43 }
        assertEquals("John", op.text)
    }

    @Test
    fun drawTextRun_emitsDrawTextOp_withOperands() {
        var textId = -1
        val bytes = document(width = 100, height = 100) {
            textId = addText("hello")
            drawTextRun(textId = textId, start = 0, end = 5, contextStart = 0, contextEnd = 5, x = 10, y = 20)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawText } as DrawText
        assertEquals(textId, op.textId)
        assertEquals(0, op.start); assertEquals(5, op.end)
        assertEquals(0, op.contextStart); assertEquals(5, op.contextEnd)
        assertEquals(10f, op.x); assertEquals(20f, op.y)
        assertEquals(false, op.rtl)
    }

    @Test
    fun drawTextAnchored_emitsDrawTextAnchorOp_withDefaults() {
        var textId = -1
        val bytes = document(width = 100, height = 100) {
            textId = addText("centred")
            drawTextAnchored(textId, x = 50, y = 50)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawTextAnchored } as DrawTextAnchored
        assertEquals(textId, op.textId)
        assertEquals(50f, op.x); assertEquals(50f, op.y)
        assertEquals(0f, op.panX); assertEquals(0f, op.panY)
        assertEquals(0, op.flags)
    }

    @Test
    fun drawTextAnchored_passesFlagsThrough() {
        val bytes = document(width = 100, height = 100) {
            val t = addText("rtl")
            drawTextAnchored(
                textId = t, x = 10, y = 10, panX = -1, panY = 1,
                flags = DrawTextAnchored.ANCHOR_TEXT_RTL or DrawTextAnchored.BASELINE_RELATIVE,
            )
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawTextAnchored } as DrawTextAnchored
        assertEquals(-1f, op.panX); assertEquals(1f, op.panY)
        assertEquals(
            DrawTextAnchored.ANCHOR_TEXT_RTL or DrawTextAnchored.BASELINE_RELATIVE,
            op.flags,
        )
    }

    @Test
    fun drawTextOnPath_emitsOp_withWireOrderedOffsets() {
        // Wire order is `vOffset` then `hOffset` (matches `DrawTextOnPath` byte layout).
        val bytes = document(width = 100, height = 100) {
            val t = addText("loop")
            val p = pathCreate(0, 0)
            drawTextOnPath(textId = t, pathId = p, vOffset = 3, hOffset = 7)
        }
        val op = DocumentReader.inflate(bytes).operations.first { it is DrawTextOnPath } as DrawTextOnPath
        assertEquals(3f, op.vOffset)
        assertEquals(7f, op.hOffset)
    }

    @Test
    fun createTextFromFloat_allocatesPlainId_andPacksDigits() {
        var id = -1
        val bytes = document(width = 100, height = 100, contentDescription = "Clock") {
            // 42 = content-desc; 43 = createTextFromFloat result
            id = createTextFromFloat(value = 3.14f, digitsBefore = 2, digitsAfter = 3)
        }
        assertEquals(43, id)
        val op = DocumentReader.inflate(bytes).operations.first { it is TextFromFloat } as TextFromFloat
        assertEquals(id, op.textId)
        assertEquals(3.14f, op.value)
        assertEquals(2, op.digitsBefore)
        assertEquals(3, op.digitsAfter)
    }

    @Test
    fun textMeasure_allocatesPlainId_andReferencesTextId() {
        var textId = -1
        var measureWidthId = -1
        var measureHeightId = -1
        document(width = 100, height = 100, contentDescription = "Clock") {
            textId = addText("look") // = 43
            measureWidthId = textMeasure(textId, type = 0) // = 44
            measureHeightId = textMeasure(textId, type = 1) // = 45
        }
        assertEquals(43, textId)
        assertEquals(44, measureWidthId)
        assertEquals(45, measureHeightId)
    }

    @Test
    fun textHelpers_idBearingVsNot_followW3Contract() {
        // W#3: addText, createTextFromFloat, textMeasure are id-bearing (region-0).
        // drawTextRun/Anchored/OnPath are NOT id-bearing.
        document(width = 100, height = 100, contentDescription = "Clock") {
            assertEquals(43, ids.peek())
            val t = addText("a") // pulls 43
            assertEquals(44, ids.peek())
            val f = createTextFromFloat(0f, 0, 1) // pulls 44
            assertEquals(45, ids.peek())
            val m = textMeasure(t, 0) // pulls 45
            assertEquals(46, ids.peek())

            // Draws — none allocate.
            drawTextRun(t, 0, 1, 0, 1, 0, 0)
            drawTextAnchored(t, 0, 0)
            val p = pathCreate(0, 0) // pulls 46 (path is id-bearing — E2)
            assertEquals(47, ids.peek())
            drawTextOnPath(t, p, 0, 0)
            assertEquals(47, ids.peek())
        }
    }
}
