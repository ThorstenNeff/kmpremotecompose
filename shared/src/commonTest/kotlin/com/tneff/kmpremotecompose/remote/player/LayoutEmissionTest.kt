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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-37 E-Layout-2 — end-to-end component-draw emission: the measure pass positions components and sets
 * absolute bounds on their Background/Border modifiers, then the Phase-B walk emits them. Verified on the
 * real corpus `c_modifier_background` (400×400): a Column (FILL, START/TOP) of two 100×100 boxes, each
 * with a background → two stacked 100×100 background rects at y=0 and y=100. This is the c_* flip the
 * whole slice targets (visual golden = test-2 sweep; this asserts the geometry headlessly).
 */
class LayoutEmissionTest {

    private class RecordingPaintContext(context: RemoteContext) : NoOpPaintContext(context) {
        val rects = mutableListOf<FloatArray>()
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) {
            rects += floatArrayOf(left, top, right, bottom)
        }
    }

    @Test
    fun cModifierBackground_emitsStackedComponentBackgrounds() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/c_modifier_background.rc"))
        val ctx = RemoteContext()
        val rec = RecordingPaintContext(ctx)
        RemoteComposePlayer(ctx).paint(doc, rec, frameTimeSeconds = 0f, surfaceWidth = 400f, surfaceHeight = 400f)

        val squares = rec.rects.filter { (it[2] - it[0]) in 99f..101f && (it[3] - it[1]) in 99f..101f }
        assertTrue(squares.size >= 2, "≥2 100×100 component backgrounds emitted (was 0 before E-L2), got ${squares.size}")
        val tops = squares.map { it[1] }.sorted()
        assertEquals(0f, tops[0], 1f, "first background at y=0")
        assertEquals(100f, tops[1], 1f, "second background stacked at y=100 (Column vertical layout)")
    }
}
