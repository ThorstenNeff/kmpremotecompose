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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.FloatConstant
import com.tneff.kmpremotecompose.remote.core.operations.Header
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawRect
import com.tneff.kmpremotecompose.remote.core.operations.layout.ClipRectModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ColumnLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ComponentStart
import com.tneff.kmpremotecompose.remote.core.operations.layout.ContainerEnd
import com.tneff.kmpremotecompose.remote.core.operations.layout.DimensionType
import com.tneff.kmpremotecompose.remote.core.operations.layout.HeightModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutContent
import com.tneff.kmpremotecompose.remote.core.operations.layout.RootLayout
import com.tneff.kmpremotecompose.remote.core.operations.layout.ScrollModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-108 S3b — dev-2 layout half of interactive scroll. A scrollable component (Column + ClipRectModifier
 * + ScrollModifier) must, in the paint pass: publish its bounds (`applyScrollBounds`: maxScroll = content −
 * viewport, contentDimension), then **clip its content holder to the viewport and translate it by the live
 * offset** read from the paired position id (`scrollOffset` = −min(max, position)). Verified headlessly with
 * a synthetic doc of EXACT-sized children (no text renderer needed); the visual proof is the Maestro
 * live-drag gate. Byte-invariant: pure runtime — no op `write`/`read` touched (§6 / §2 intact).
 */
class Rem108S3bScrollLayoutTest {

    private val POS = 42; private val MAX = 43; private val NOTCH = 44

    private class Rec(ctx: RemoteContext) : NoOpPaintContext(ctx) {
        val log = mutableListOf<String>()
        override fun matrixSave() { log += "save" }
        override fun matrixRestore() { log += "restore" }
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) { log += "clip($left,$top,$right,$bottom)" }
        override fun translate(translateX: Float, translateY: Float) { log += "translate($translateX,$translateY)" }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) { log += "rect" }
    }

    /** A vertical Column(EXACT 200×200) with clip+scroll over three EXACT 100×100 children (content = 300). */
    private fun scrollDoc(): RemoteComposeDocument {
        val ops = ArrayList<Operation>()
        ops += Header.flat(200, 200)
        ops += FloatConstant(POS, 30f) // paired scroll position (no TE in the synthetic doc — seed directly)
        ops += RootLayout(-2)
        ops += ColumnLayout(-3, animationId = -1, horizontalPositioning = 1, verticalPositioning = 4, spacedBy = 0f)
        ops += WidthModifier(DimensionType.EXACT, 200f)
        ops += HeightModifier(DimensionType.EXACT, 200f)
        ops += ClipRectModifier()
        ops += ScrollModifier(ScrollModifier.VERTICAL, WireTypes.asNan(POS), WireTypes.asNan(MAX), WireTypes.asNan(NOTCH))
        ops += ContainerEnd() // closes the ScrollModifier container (wraps its TouchExpression upstream)
        ops += LayoutContent(-4)
        for (id in listOf(-5, -6, -7)) {
            ops += ComponentStart(0, id, 100f, 100f)
            ops += DrawRect(0f, 0f, 100f, 100f)
            ops += ContainerEnd()
        }
        ops += ContainerEnd() // closes LayoutContent -4
        ops += ContainerEnd() // closes Column -3
        ops += ContainerEnd() // closes Root -2
        return RemoteComposeDocument(ops)
    }

    @Test
    fun scrollComponent_clipsViewportAndTranslatesContentByOffset() {
        val ctx = RemoteContext()
        val rec = Rec(ctx)
        RemoteComposePlayer(ctx).paint(scrollDoc(), rec)

        // Bounds published before eval (§5): maxScroll = content(300) − viewport(200) = 100, contentDim = 300.
        assertEquals(100f, ctx.getFloat(MAX), 0.01f, "applyScrollBounds must publish maxScroll = content − viewport")
        assertEquals(300f, ctx.getFloat(NOTCH), 0.01f, "applyScrollBounds must publish contentDimension")

        // The content holder is bracketed: save → clip(viewport) → translate(0, offset) → [content] → restore.
        // offset = −min(max=100, position=30) = −30 (content shifted up by 30).
        val log = rec.log
        val save = log.indexOf("save")
        assertTrue(save >= 0, "a matrixSave must open the scroll bracket; log=$log")
        assertEquals("clip(0.0,0.0,200.0,200.0)", log[save + 1], "clip to the component viewport; log=$log")
        assertEquals("translate(0.0,-30.0)", log[save + 2], "translate content by −min(max,position); log=$log")
        // three children drawn inside the bracket, then a restore closes it
        val restore = log.indexOf("restore")
        assertTrue(restore > save, "a matrixRestore must close the bracket after content; log=$log")
        val rectsInside = log.subList(save, restore).count { it == "rect" }
        assertEquals(3, rectsInside, "all three child draws fall inside the clip+translate bracket; log=$log")
    }

    @Test
    fun staticZeroPosition_isANoOpTranslate() {
        // Position 0 (the static/golden default) → offset 0 → translate(0,0): goldens stay byte-stable.
        val ctx = RemoteContext()
        val rec = Rec(ctx)
        val ops = scrollDoc().operations.toMutableList()
        ops[1] = FloatConstant(POS, 0f) // position = 0
        RemoteComposePlayer(ctx).paint(RemoteComposeDocument(ops), rec)
        val save = rec.log.indexOf("save")
        assertEquals("translate(0.0,0.0)", rec.log[save + 2], "offset 0 at position 0 → no content shift; log=${rec.log}")
    }
}
