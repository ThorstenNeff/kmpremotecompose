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
import com.tneff.kmpremotecompose.remote.core.operations.draw.PaintData
import com.tneff.kmpremotecompose.remote.core.operations.layout.CoreText
import com.tneff.kmpremotecompose.remote.player.core.ComputedTextLayout
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-74 (FC-D1) — the **wrap decision** in [CoreText.paint] is the load-bearing seam of complex-text:
 * it routes to the multi-line `layoutComplexText`/`drawComplexText` path **only** when the text is wider
 * than its measured box and multi-line is allowed (upstream `forceComplex`: `width > maxWidth &&
 * maxLines != 1 && maxWidth > 0`); otherwise it keeps the exact single-line `drawTextRun` path so
 * non-wrapping component text stays pixel-identical (Bein-2). This is headless (no graphics backend) —
 * the real-Skiko multi-line render is proved in `Rem74ComplexTextIosTest` on the iOS gate.
 *
 * Plus a **corpus-reach guard**: D1 deliberately renders complex text through CMP-common text (not raw
 * Skiko `Paragraph`), so a few granular Android params (breakStrategy/hyphenationFrequency/
 * justificationMode levels, START/MIDDLE ellipsis, `TextAlign.Justify`) are CMP-limited. This test
 * proves — by decoding the whole corpus — that **no document exercises them**, so the limit is cosmetic.
 * If a future fixture starts using one, this fails loudly (no silent gap; PROJECT_CONTEXT §5/§6).
 */
class Rem74WrapDecisionTest {

    /** Records which text path [CoreText.paint] took, with a controllable single-line measure width. */
    private class RecordingTextPaintContext(
        context: RemoteContext,
        private val measuredWidth: Float,
    ) : NoOpPaintContext(context) {
        var complexCalls = 0
        var textRunCalls = 0

        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            bounds[0] = 0f; bounds[1] = -8f; bounds[2] = measuredWidth; bounds[3] = 2f
        }

        override fun layoutComplexText(
            textId: Int, start: Int, end: Int, alignment: Int, overflow: Int, maxLines: Int,
            maxWidth: Float, maxHeight: Float, letterSpacing: Float, lineHeightAdd: Float,
            lineHeightMultiplier: Float, lineBreakStrategy: Int, hyphenationFrequency: Int,
            justificationMode: Int, useUnderline: Boolean, strikethrough: Boolean, flags: Int,
        ): ComputedTextLayout? {
            complexCalls++
            return null // CoreText only needs the routing; the real layout is exercised on iOS.
        }

        override fun drawTextRun(
            textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int,
            x: Float, y: Float, rtl: Boolean,
        ) {
            textRunCalls++
        }

        override fun applyPaint(paint: PaintData) {} // CoreText.applyStyle → no-op here
    }

    /** Big-endian 4-byte int param value (wire order), for synthesising a TextStyle param. */
    private fun intParam(id: Int, value: Int): CoreText.Param =
        CoreText.Param(id, byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))

    /** Drive [CoreText.paint] once; returns (complexCalls, textRunCalls). */
    private fun route(boxW: Float, measuredWidth: Float, params: List<CoreText.Param> = emptyList()): Pair<Int, Int> {
        val ctx = RemoteContext()
        val textId = 42
        ctx.putText(textId, "component text")
        val op = CoreText(textId, params)
        op.setTextDraw(0f, 8f) // positioned
        op.setTextBox(0f, 0f, boxW, 100f)
        val paint = RecordingTextPaintContext(ctx, measuredWidth)
        op.paint(ctx, paint)
        return paint.complexCalls to paint.textRunCalls
    }

    @Test
    fun textWiderThanBox_routesToComplexMultiLine() {
        val (complex, run) = route(boxW = 100f, measuredWidth = 300f)
        assertEquals(1, complex, "wide text in a box ⇒ multi-line layoutComplexText")
        assertEquals(0, run, "must NOT also take the single-line drawTextRun path")
    }

    @Test
    fun textWithinBox_keepsSingleLineDrawTextRun() {
        val (complex, run) = route(boxW = 300f, measuredWidth = 100f)
        assertEquals(0, complex, "text that fits ⇒ no wrap")
        assertEquals(1, run, "fits ⇒ exact single-line drawTextRun (Bein-2 pixel-identity)")
    }

    @Test
    fun maxLines1_neverWraps_evenWiderThanBox() {
        // P_MAX_LINES = 11; value 1 ⇒ single line forced, no wrap regardless of width.
        val (complex, run) = route(boxW = 100f, measuredWidth = 300f, params = listOf(intParam(11, 1)))
        assertEquals(0, complex, "maxLines==1 ⇒ never the complex path (upstream forceComplex guard)")
        assertEquals(1, run)
    }

    @Test
    fun zeroBoxWidth_keepsSingleLine() {
        // No measured box (unpositioned-width / maxWidth<=0) ⇒ no wrap, exact single-line path.
        val (complex, run) = route(boxW = 0f, measuredWidth = 300f)
        assertEquals(0, complex, "boxW<=0 ⇒ no maxWidth ⇒ single-line (upstream forceComplex requires maxWidth>0)")
        assertEquals(1, run)
    }

    @Test
    fun unpositionedOrMissingText_drawsNothing() {
        val ctx = RemoteContext()
        ctx.putText(42, "x")
        val op = CoreText(42, emptyList()) // never setTextDraw ⇒ not positioned
        val paint = RecordingTextPaintContext(ctx, 300f)
        op.paint(ctx, paint)
        assertEquals(0, paint.complexCalls + paint.textRunCalls, "unpositioned ⇒ no draw")
    }

    /**
     * Corpus-reach guard for the D1 CMP-Text decision: the granular params CMP-common can't express
     * (breakStrategy=15, hyphenationFrequency=16, justificationMode=17 with a non-zero value, and
     * `TextAlign.Justify`=4) must NOT appear in any of the 173 corpus docs — so the CMP limit is cosmetic.
     */
    @Test
    fun corpusNeverExercisesCmpLimitedGranularParams() {
        Builtins.register()
        val offenders = mutableListOf<String>()
        for (name in RcCorpus.corpusNames()) {
            val ops = try {
                DocumentReader.inflate(RcCorpus.readFixture("corpus/$name")).operations
            } catch (t: Throwable) {
                continue
            }
            for (ct in ops.filterIsInstance<CoreText>()) for (p in ct.params) {
                if (p.value.size < 4) continue
                val v = (p.value[0].toInt() and 0xFF shl 24) or (p.value[1].toInt() and 0xFF shl 16) or
                    (p.value[2].toInt() and 0xFF shl 8) or (p.value[3].toInt() and 0xFF)
                when {
                    p.id == 15 && v != 0 -> offenders += "$name: breakStrategy=$v"
                    p.id == 16 && v != 0 -> offenders += "$name: hyphenationFrequency=$v"
                    p.id == 17 && v != 0 -> offenders += "$name: justificationMode=$v"
                    p.id == 9 && v == 4 -> offenders += "$name: align=Justify"
                    p.id == 10 && (v == 4 || v == 5) -> offenders += "$name: ellipsis=START/MIDDLE($v)"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Corpus now exercises a CMP-limited complex-text param — the D1 CMP-Text limit is no longer " +
                "cosmetic, escalate to PO (raw-Skiko Paragraph fallback): $offenders",
        )
    }

    @Test
    fun corpusHasComplexTextToRender() {
        Builtins.register()
        val docsWithText = RcCorpus.corpusNames().count { name ->
            val ops = try {
                DocumentReader.inflate(RcCorpus.readFixture("corpus/$name")).operations
            } catch (t: Throwable) {
                return@count false
            }
            ops.filterIsInstance<CoreText>().isNotEmpty()
        }
        // Anchors the guard above: it actually walked CORE_TEXT ops (16 docs at REM-74), not an empty set.
        assertTrue(docsWithText >= 16, "expected ≥16 corpus docs with CORE_TEXT (was $docsWithText)")
    }
}
