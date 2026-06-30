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
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchDownModifier
import com.tneff.kmpremotecompose.remote.core.operations.layout.ValueIntegerChangeAction
import com.tneff.kmpremotecompose.remote.core.operations.layout.WidthModifier
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-162 — the **render-walk regression gate** (dev-1's fix half; dev-3's `Rem162ClickableScrollFixtureTest`
 * covers the dispatch half). A clickable-in-scroll doc (scroll Column over two items, the 1st carrying a
 * `MODIFIER_TOUCH_DOWN`) must keep BOTH items inside the scroll bracket's `save…restore` span.
 *
 * **The bug:** `MODIFIER_TOUCH_DOWN/UP/CANCEL` were absent from `CONTAINER_OPENING_OPCODES` although they
 * carry a trailing `CONTAINER_END`. Inside a scroll holder, that stray `CONTAINER_END` decremented
 * `skipConditionalBlock`'s depth, so the bracket's `matchEnd` returned one child early → `restore` fired
 * after item 1 → item 2 rendered with NO clip/translate. Draw log pre-fix:
 * `save, clip, translate, rect, RESTORE, rect`; post-fix: `save, clip, translate, rect, rect, restore`.
 *
 * This FAILS pre-fix and PASSES post-fix, and guards against re-regression. §2-safe (no op bytes touched).
 */
class Rem162ScrollWalkTest {

    private val POS = 42; private val MAX = 43; private val NOTCH = 44

    private class Rec(ctx: RemoteContext) : NoOpPaintContext(ctx) {
        val log = mutableListOf<String>()
        override fun matrixSave() { log += "save" }
        override fun matrixRestore() { log += "restore" }
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) { log += "clip" }
        override fun translate(translateX: Float, translateY: Float) { log += "translate" }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) { log += "rect" }
    }

    /** A vertical scroll Column over two 100×100 children; [clickableFirst] gives item 1 a TOUCH_DOWN. */
    private fun doc(clickableFirst: Boolean): RemoteComposeDocument {
        val ops = ArrayList<Operation>()
        ops += Header.flat(200, 200)
        ops += FloatConstant(POS, 30f)
        ops += RootLayout(-2)
        ops += ColumnLayout(-3, -1, 1, 4, 0f)
        ops += WidthModifier(DimensionType.EXACT, 200f)
        ops += HeightModifier(DimensionType.EXACT, 200f)
        ops += ClipRectModifier()
        ops += ScrollModifier(ScrollModifier.VERTICAL, WireTypes.asNan(POS), WireTypes.asNan(MAX), WireTypes.asNan(NOTCH))
        ops += ContainerEnd()
        ops += LayoutContent(-4)
        ops += ComponentStart(0, -5, 100f, 100f)
        if (clickableFirst) {
            ops += TouchDownModifier()
            ops += ValueIntegerChangeAction(99, 1)
            ops += ContainerEnd() // closes the touch-down action block
        }
        ops += DrawRect(0f, 0f, 100f, 100f)
        ops += ContainerEnd() // component -5
        ops += ComponentStart(0, -6, 100f, 100f)
        ops += DrawRect(0f, 0f, 100f, 100f)
        ops += ContainerEnd() // component -6
        ops += ContainerEnd() // content
        ops += ContainerEnd() // column
        ops += ContainerEnd() // root
        return RemoteComposeDocument(ops)
    }

    /** Count the "rect" draws that fall between the (first) save and its matching restore. */
    private fun rectsInsideBracket(log: List<String>): Int {
        val save = log.indexOf("save")
        val restore = log.indexOf("restore")
        if (save < 0 || restore < 0 || restore < save) return -1
        return log.subList(save, restore).count { it == "rect" }
    }

    private fun render(d: RemoteComposeDocument): List<String> {
        val ctx = RemoteContext()
        val rec = Rec(ctx)
        RemoteComposePlayer(ctx).paint(d, rec)
        return rec.log
    }

    @Test fun clickableInScroll_bothItemsStayInsideScrollBracket() {
        val log = render(doc(clickableFirst = true))
        assertEquals(
            2, rectsInsideBracket(log),
            "REM-162: both items must render inside the scroll save/restore bracket — a touch-modifier on " +
                "item 1 must not make `restore` fire early. log=$log",
        )
    }

    @Test fun touchModifier_doesNotChangeBracketSpan() {
        // The scroll bracket must span both items identically whether or not item 1 is clickable.
        val withLog = render(doc(clickableFirst = true))
        val withoutLog = render(doc(clickableFirst = false))
        assertEquals(2, rectsInsideBracket(withoutLog), "baseline (no touch mod) must bracket both items; log=$withoutLog")
        assertEquals(
            rectsInsideBracket(withoutLog), rectsInsideBracket(withLog),
            "the touch modifier must not change the scroll-bracket span (with=$withLog vs without=$withoutLog)",
        )
    }
}
