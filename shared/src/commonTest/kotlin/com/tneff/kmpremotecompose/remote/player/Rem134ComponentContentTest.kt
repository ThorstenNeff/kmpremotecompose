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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawContent
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.core.operations.layout.AlignByModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.TextLayout
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-134 (Option C) — AttributedString component-content render path. The corpus `attribute_string`
 * previously rendered 0 text spans (DrawContent was a no-op; LAYOUT_TEXT never painted). With the
 * measure-wired DrawContent delegation it now draws all 24 spans at their measured baselines.
 *
 * jvmTest has no real text renderer (`getTextBounds` is a no-op → zero metrics), so these tests inject a
 * [FakeTextContext] with deterministic metrics (width ∝ length·size, ascent ∝ size) to validate the
 * **layout math** — span x-advance within a row, AlignBy baseline alignment across mixed font sizes,
 * and vertical row stacking. The real per-pixel positions are test-3's desktop data-oracle gate (§6).
 */
class Rem134ComponentContentTest {

    private data class Draw(val textId: Int, val x: Float, val baseline: Float, val text: String, val size: Float)

    /** A deterministic text-measuring PaintContext: width = len·size·0.6, ascent = size·0.8, descent = size·0.2. */
    private class FakeTextContext(context: RemoteContext) : NoOpPaintContext(context) {
        var size = 36f
        val draws = ArrayList<Draw>()
        var lines = 0
        override fun applyPaint(paint: PaintData) {
            val v = paint.values
            var i = 0
            while (i + 1 < v.size) { if (v[i] == PaintData.TEXT_SIZE) size = Float.fromBits(v[i + 1]); i += 2 }
        }
        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            val t = context.getText(textId) ?: ""
            bounds[0] = 0f; bounds[1] = -size * 0.8f; bounds[2] = t.length * size * 0.6f; bounds[3] = size * 0.2f
        }
        override fun drawTextRun(textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean) {
            draws += Draw(textId, x, y, context.getText(textId) ?: "", size)
        }
        override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) { lines++ }
    }

    private fun render(): FakeTextContext {
        Builtins.register()
        val ctx = RemoteContext()
        val rec = FakeTextContext(ctx)
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/attribute_string.rc"))
        RemoteComposePlayer(ctx).paint(doc, rec)
        return rec
    }

    /** Group consecutive draws sharing a baseline into visual rows (the AttributedString lines). */
    private fun rows(draws: List<Draw>): List<List<Draw>> {
        val out = ArrayList<MutableList<Draw>>()
        for (d in draws) {
            val last = out.lastOrNull()
            if (last != null && kotlin.math.abs(last[0].baseline - d.baseline) < 0.01f) last += d
            else out += mutableListOf(d)
        }
        return out
    }

    @Test
    fun allSpansRender_inDocumentOrder() {
        val draws = render().draws
        assertEquals(24, draws.size, "all 24 AttributedString spans must draw (was 0 pre-REM-134)")
        // Spot-check the order + content of the first visual line and a styled span.
        assertEquals("AttributedString Demo:", draws[0].text)
        assertTrue(draws.any { it.text == "Bold" } && draws.any { it.text == "Yellow Background" })
    }

    @Test
    fun spansAdvanceHorizontally_withinARow() {
        val rows = rows(render().draws)
        val multiSpan = rows.first { it.size > 1 } // e.g. "This is "/"Bold"/", this is "/"Italic"/"."
        for (i in 1 until multiSpan.size) {
            assertTrue(
                multiSpan[i].x > multiSpan[i - 1].x,
                "span ${i} (\"${multiSpan[i].text}\") must start right of the previous (x must advance)",
            )
        }
    }

    @Test
    fun mixedFontSizes_shareOneBaseline_viaAlignBy() {
        // The "Big" line mixes a 92px span with 46px spans; AlignBy(line=NaN) must put them on ONE baseline.
        val bigRow = rows(render().draws).first { row -> row.any { it.text == "Big" } }
        val sizes = bigRow.map { it.size }.toSet()
        assertTrue(sizes.size > 1, "precondition: the Big row mixes font sizes, got $sizes")
        val baselines = bigRow.map { it.baseline }.toSet()
        assertEquals(1, baselines.size, "all spans on a row must share a single text baseline (AlignBy)")
    }

    @Test
    fun rows_stackVertically_downTheColumn() {
        val rowBaselines = rows(render().draws).map { it[0].baseline }
        for (i in 1 until rowBaselines.size) {
            assertTrue(rowBaselines[i] > rowBaselines[i - 1], "row $i must sit below row ${i - 1}")
        }
    }

    @Test
    fun underlineAndStrike_drawLinesStillEmit() {
        // ComponentValue width/height now resolve (TextLayout content slot is sized) → the 2 decoration
        // DrawLines (Underlined / Strikethrough) keep emitting; they are no longer the ONLY thing drawn.
        assertTrue(render().lines >= 2, "underline + strikethrough DrawLines must emit")
    }

    @Test
    fun byteFormat_unchanged_forTheThreeOps() {
        // §2 by-construction: render-apply is additive; the 3 touched ops round-trip byte-identical.
        fun rt(op: Operation, reader: com.tneff.kmpremotecompose.remote.core.operations.OperationReader) {
            val bytes = WireBuffer().also { op.write(it) }.toByteArray()
            val buf = WireBuffer.fromBytes(bytes); assertEquals(op.opcode, buf.readByte())
            val decoded = ArrayList<Operation>(); reader.read(buf, decoded)
            assertEquals(op, decoded[0], "decoded equals original")
            assertContentEquals(bytes, WireBuffer().also { decoded[0].write(it) }.toByteArray(), "re-encode byte-identical")
        }
        rt(DrawContent(), DrawContent)
        rt(TextLayout(-9, -1, 42, -16777216, 46f, 0, 400f, -1, 1, 1, Int.MAX_VALUE), TextLayout)
        rt(AlignByModifier(Float.NaN, 0), AlignByModifier)
    }
}
