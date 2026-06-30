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

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-108 (Epic-F) **S1 — the touch foundation** (NoOp, render-invariant). Proves, headless:
 *  1. reserved touch ids match upstream (13–16, 29);
 *  2. [RemoteComposePlayer.touchIdsUsed] reports which touch vars each corpus doc reads (only POS 13/14);
 *  3. the **corpus stop-mode reach guard** (assist Q2 scope correction): every `TouchExpression` uses a
 *     mode in 0–6; **mode 7 (SINGLE_EVEN) is corpus-absent**; mode 3 (NOTCHES_EVEN) is present (most common);
 *  4. the touch-dispatch seam loads the touch position;
 *  5. **render-invariance**: S1 has no consumer of the touch position, so a static render is identical
 *     whether or not a touch position is seeded (the static==baseline determinism pin — goldens/conformance
 *     immune to touch; the eval that consumes the position lands in S2).
 */
class Rem108TouchSeamTest {

    private class RecordingDrawPaintContext(context: RemoteContext) : NoOpPaintContext(context) {
        val log = mutableListOf<String>()
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) { log += "rect($left,$top,$right,$bottom)" }
        override fun drawCircle(centerX: Float, centerY: Float, radius: Float) { log += "circle($centerX,$centerY,$radius)" }
        override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) { log += "line($x1,$y1,$x2,$y2)" }
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) { log += "oval($left,$top,$right,$bottom)" }
        override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) { log += "arc($left,$top,$right,$bottom,$startAngle,$sweepAngle)" }
        override fun drawSector(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) { log += "sector($left,$top,$right,$bottom,$startAngle,$sweepAngle)" }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float) { log += "roundRect($left,$top,$right,$bottom,$radiusX,$radiusY)" }
        override fun drawPath(id: Int, start: Float, end: Float) { log += "path($id,$start,$end)" }
        override fun matrixTranslate(translateX: Float, translateY: Float) { log += "mTranslate($translateX,$translateY)" }
        override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) { log += "mRotate($rotate,$pivotX,$pivotY)" }
    }

    private fun doc(name: String): RemoteComposeDocument {
        Builtins.register()
        return DocumentReader.inflate(RcCorpus.readFixture("corpus/$name"))
    }

    @Test fun reservedTouchIds_matchUpstream() {
        assertEquals(13, RemoteContext.ID_TOUCH_POS_X)
        assertEquals(14, RemoteContext.ID_TOUCH_POS_Y)
        assertEquals(15, RemoteContext.ID_TOUCH_VEL_X)
        assertEquals(16, RemoteContext.ID_TOUCH_VEL_Y)
        assertEquals(29, RemoteContext.ID_TOUCH_EVENT_TIME)
        assertEquals(13..16, RemoteContext.TOUCH_ID_RANGE)
    }

    @IgnoreOnWasm
    @Test fun touchIdsUsed_matchesCorpusDocs() {
        assertEquals(setOf(13), RemoteComposePlayer.touchIdsUsed(doc("touch1.rc")))
        assertEquals(setOf(14), RemoteComposePlayer.touchIdsUsed(doc("touch2.rc")))
        assertEquals(setOf(13, 14), RemoteComposePlayer.touchIdsUsed(doc("touch_wrap.rc")))
        assertEquals(setOf(13), RemoteComposePlayer.touchIdsUsed(doc("c_modifier_horizontal_scroll.rc")))
        assertTrue(RemoteComposePlayer.isTouchDriven(doc("touch1.rc")))
        assertFalse(RemoteComposePlayer.isTouchDriven(doc("procedure_simple1.rc")))
        assertEquals(emptySet(), RemoteComposePlayer.touchIdsUsed(doc("procedure_simple1.rc")))
    }

    @IgnoreOnWasm
    @Test fun corpusStopModeReach_isWithinSupportedScope() {
        // assist Q2 scope correction made into a guard: every TouchExpression stop-mode the corpus uses must
        // be in the supported 0..6 set; mode 7 (SINGLE_EVEN) must stay corpus-absent; mode 3 (the most common)
        // must be present. If a future fixture uses mode 7, this fails loudly (no silent unsupported gap).
        Builtins.register()
        val all = mutableSetOf<Int>()
        for (name in RcCorpus.corpusNames()) {
            val d = try { DocumentReader.inflate(RcCorpus.readFixture("corpus/$name")) } catch (t: Throwable) { continue }
            all += RemoteComposePlayer.touchStopModesUsed(d)
        }
        assertTrue(all.all { it in 0..6 }, "corpus touch stop-modes must be within supported 0..6 — found $all")
        assertTrue(3 in all, "expected NOTCHES_EVEN(3) in the corpus (the most common touch stop-mode)")
        assertFalse(7 in all, "SINGLE_EVEN(7) must remain corpus-absent (the only legitimately unsupported mode)")
    }

    @IgnoreOnWasm
    @Test fun touchDispatch_loadsPosition() {
        val ctx = RemoteContext()
        val d = doc("touch1.rc")
        val player = RemoteComposePlayer(ctx)
        player.touchDown(d, ctx, 120f, 240f)
        assertEquals(120f, ctx.getFloat(RemoteContext.ID_TOUCH_POS_X))
        assertEquals(240f, ctx.getFloat(RemoteContext.ID_TOUCH_POS_Y))
        player.touchDrag(d, ctx, 121f, 241f)
        assertEquals(121f, ctx.getFloat(RemoteContext.ID_TOUCH_POS_X))
        player.touchUp(d, ctx, 122f, 242f)
        assertEquals(242f, ctx.getFloat(RemoteContext.ID_TOUCH_POS_Y))
    }

    @IgnoreOnWasm
    @Test fun staticRender_touchPositionHasNoEffectYet_conformancePin() {
        // S1 has no consumer of the touch position (TouchExpression is still a carrier), so seeding a touch
        // position must NOT change the render. This pins that S1 is render-invariant → goldens/REM-78/173
        // immune to touch. (The eval that actually consumes the position arrives in S2.)
        for (name in listOf("touch1.rc", "touch2.rc", "touch_wrap.rc")) {
            val baseline = render(name) { }
            val withTouch = render(name) { ctx ->
                ctx.loadFloat(RemoteContext.ID_TOUCH_POS_X, 999f)
                ctx.loadFloat(RemoteContext.ID_TOUCH_POS_Y, 999f)
            }
            assertTrue(baseline.isNotEmpty(), "$name should draw something (guard vs vacuous equality)")
            assertEquals(baseline, withTouch, "S1: seeding a touch position must not change the render of $name")
        }
    }

    private fun render(name: String, preSeed: (RemoteContext) -> Unit): List<String> {
        val ctx = RemoteContext()
        val d = doc(name)
        // Seed the touch position BEFORE paint; paint re-seeds system vars but never touch (S1), and
        // resetPass keeps the float store, so the seeded value survives into the walk — proving no op
        // consumes it yet (render-invariant).
        preSeed(ctx)
        val rec = RecordingDrawPaintContext(ctx)
        RemoteComposePlayer(ctx).paint(d, rec, frameTimeSeconds = 0f)
        return rec.log
    }
}
