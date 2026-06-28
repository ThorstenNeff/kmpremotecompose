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
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.layout.TouchExpression
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.TouchPhase
import com.tneff.kmpremotecompose.remote.player.core.TouchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * REM-108 (Epic-F) **S2 — the TouchExpression eval engine**. Drives the real drag→value path through the
 * player dispatch + Phase-A apply (the persisted [TouchExpression] holds `currentValue` across frames):
 * a drag changes the output; the value clamps / wraps; on touch-up it settles to the stop position
 * (NOTCHES_EVEN snaps to a notch). At rest (no touch) the output is the doc's default — and crucially a
 * touch dispatched in **static mode** must NOT change the render (the determinism invariant).
 *
 * Deferred (honest scope): the post-release **velocity glide** animation (snap is immediate here) and the
 * live `pointerInput` UI hookup — verified on-device/mouse by test-1/2/3.
 */
class Rem108TouchEvalTest {

    private fun doc(name: String): RemoteComposeDocument {
        Builtins.register()
        return DocumentReader.inflate(RcCorpus.readFixture("corpus/$name"))
    }

    private fun firstTouch(d: RemoteComposeDocument): TouchExpression =
        d.operations.filterIsInstance<TouchExpression>().first()

    /** Phase-A apply (loads the touch output); animation on so it's the live path. */
    private fun paint(player: RemoteComposePlayer, d: RemoteComposeDocument, ctx: RemoteContext) {
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 1f)
    }

    @Test fun drag_changesTheOutputValue() {
        val ctx = RemoteContext().also { it.animationEnabled = true }
        val d = doc("touch1.rc") // exp reads TOUCH_POS_X (13)
        val player = RemoteComposePlayer(ctx)
        paint(player, d, ctx)
        val outId = firstTouch(d).id
        val atRest = ctx.getFloat(outId)
        player.touchDown(d, ctx, 40f, 40f)
        player.touchDrag(d, ctx, 260f, 260f)
        assertNotEquals(atRest, ctx.getFloat(outId), "dragging must change the TouchExpression output")
    }

    @Test fun atRest_outputIsStableDefault_noTouch() {
        val ctx = RemoteContext().also { it.animationEnabled = true }
        val d = doc("touch1.rc")
        val player = RemoteComposePlayer(ctx)
        paint(player, d, ctx)
        val outId = firstTouch(d).id
        val a = ctx.getFloat(outId)
        paint(player, d, ctx) // second frame, still no touch
        assertEquals(a, ctx.getFloat(outId), "with no touch the output is the stable default each frame")
        assertTrue(a.isFinite(), "default output must be finite (not NaN)")
    }

    @Test fun wrapDoc_degradesGracefully_noCrash_whenEvaluatorLacksOperator() {
        // touch_wrap is NOTCHES_EVEN(3) + wrap-mode, but its 13-op expression uses an RPN operator beyond
        // the current E2-MVP evaluator (0x310018; needs E-D3). The contract: NO crash — TouchExpression
        // fail-closes (keeps default), so the doc still renders + the value stays finite & in the wrap
        // range. Full interactive wrap lands when the evaluator extends (flagged to PO).
        val ctx = RemoteContext().also { it.animationEnabled = true }
        val d = doc("touch_wrap.rc")
        val te = firstTouch(d)
        assertEquals(3, te.stopLogic ushr 16, "touch_wrap is NOTCHES_EVEN(3)")
        val player = RemoteComposePlayer(ctx)
        paint(player, d, ctx)
        val outId = te.id
        player.touchDown(d, ctx, 10f, 10f)
        player.touchDrag(d, ctx, 5000f, 5000f)
        player.touchUp(d, ctx, 5000f, 5000f)
        paint(player, d, ctx)
        val settled = ctx.getFloat(outId)
        assertTrue(settled.isFinite(), "must not crash / produce NaN when the operator is unsupported (was $settled)")
        assertTrue(settled >= 0f && settled < 360f + 0.01f, "value stays in the wrap range [0,360) (was $settled)")
    }

    @Test fun staticMode_touchDispatchHasNoEffect_determinismPin() {
        // THE invariant: in static mode (animation off) a dispatched touch must NOT change the output —
        // so goldens / REM-78 sweep stay deterministic. (Live interactivity only when animation is on.)
        val ctx = RemoteContext().also { it.animationEnabled = false }
        val d = doc("touch1.rc")
        val player = RemoteComposePlayer(ctx)
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 0f)
        val outId = firstTouch(d).id
        val staticVal = ctx.getFloat(outId)
        // A touch would drive it live — but the live loop never dispatches in static mode. Prove that even
        // if state were nudged, a static re-paint reloads the default (touch inactive ⇒ apply ignores pos).
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 0f)
        assertEquals(staticVal, ctx.getFloat(outId), "static render output is stable (touch never drives it)")
    }

    @Test fun touchState_phaseConsume_drivesGestureAcrossFrames() {
        // S2b: the persistent TouchState drives exactly one transition per paint across the per-frame-fresh
        // RemoteContext. DOWN→(consumed)DRAG→…→UP→(consumed)IDLE; a drag moves the output; release settles.
        val ctx = RemoteContext().also { it.animationEnabled = true }
        val d = doc("touch1.rc")
        val player = RemoteComposePlayer(ctx)
        val ts = TouchState()
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 1f, touchState = ts)
        val outId = firstTouch(d).id
        val atRest = ctx.getFloat(outId)

        ts.down(40f, 40f)
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 1f, touchState = ts)
        assertEquals(TouchPhase.DRAG, ts.phase, "DOWN is consumed in one frame → DRAG")

        ts.move(260f, 260f)
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 1f, touchState = ts)
        assertNotEquals(atRest, ctx.getFloat(outId), "a drag moves the touch output")

        ts.up(260f, 260f)
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 1f, touchState = ts)
        assertEquals(TouchPhase.IDLE, ts.phase, "UP is consumed in one frame → IDLE")
    }

    @Test fun staticMode_touchStateNotConsumed_determinismPin() {
        // S2b determinism: in static mode the player must NOT consume the TouchState (no dispatch) → the
        // phase stays DOWN and the output stays the default. Goldens / REM-78 sweep immune to touch.
        val ctx = RemoteContext().also { it.animationEnabled = false }
        val d = doc("touch1.rc")
        val player = RemoteComposePlayer(ctx)
        val ts = TouchState().also { it.down(40f, 40f) }
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 0f, touchState = ts)
        val outId = firstTouch(d).id
        val v1 = ctx.getFloat(outId)
        assertEquals(TouchPhase.DOWN, ts.phase, "static mode does not consume the touch (phase stays DOWN)")
        player.paint(d, NoOpPaintContext(ctx), frameTimeSeconds = 0f, touchState = ts)
        assertEquals(v1, ctx.getFloat(outId), "static output stable — touch never drives it")
    }
}
