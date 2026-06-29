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
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-140 — guard that the REM-134 measure changes (row-wrap default, AlignBy-baseline) stay SCOPED to
 * TextLayout spans. `text_baseline` carries 18 `MODIFIER_ALIGN_BY` on **non-span** components; broad-REM-134
 * baseline-collapsed them (~60% render loss). The fix scopes baseline-align to `TEXT_LAYOUT` nodes, so
 * text_baseline's text must stay spread across many distinct baselines (not collapsed onto a shared one).
 *
 * Uses a realistic-ascent fake context (jvmTest has no real text renderer; ascent=0 would make AlignBy a
 * headless no-op and mask the regression — the REM-134/140 lesson). Real pixels = test-3's full-render gate.
 */
class Rem140ScopeTest {
    private class AscentText(context: RemoteContext) : NoOpPaintContext(context) {
        var tx = 0f; var ty = 0f
        private val st = ArrayDeque<Pair<Float, Float>>()
        val baselines = ArrayList<Float>()
        override fun matrixSave() { st.addLast(tx to ty) }
        override fun matrixRestore() { st.removeLastOrNull()?.let { tx = it.first; ty = it.second } }
        override fun translate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun matrixTranslate(translateX: Float, translateY: Float) { tx += translateX; ty += translateY }
        override fun getTextBounds(textId: Int, start: Int, end: Int, flags: Int, bounds: FloatArray) {
            val t = context.getText(textId) ?: ""; bounds[0] = 0f; bounds[1] = -11f; bounds[2] = t.length * 9f; bounds[3] = 3f
        }
        override fun drawTextRun(textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean) {
            baselines += y + ty
        }
    }

    @Test
    fun textBaseline_alignByOnNonSpans_isNotBaselineCollapsed() {
        Builtins.register()
        val ctx = RemoteContext(); val rec = AscentText(ctx)
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/text_baseline.rc"))
        RemoteComposePlayer(ctx).paint(doc, rec)
        assertTrue(rec.baselines.size >= 5, "text_baseline must render its text runs, got ${rec.baselines.size}")
        val distinct = rec.baselines.map { kotlin.math.round(it) }.toSet().size
        // Broad-REM-134 collapsed these AlignBy components toward a shared span-baseline; scoped fix keeps
        // them on their independent (cross-axis) positions → several distinct baselines.
        assertTrue(distinct >= 3, "non-span AlignBy text must keep distinct baselines (not span-collapsed), got $distinct")
    }
}
