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
import com.tneff.kmpremotecompose.remote.player.core.HapticActuator
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.TouchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-143 S3b self-check (necessary-not-sufficient — the device gate is a Maestro live haptic check on
 * Android/iOS; Desktop/Web assert no-op). Validates the HapticFeedback firing MECHANISM on
 * haptic_demo (startAt=id29, duration=0.1, body = HapticFeedback(type 8) before ImpulseProcess): the
 * impulse fires its body HapticFeedback via [RemoteContext.hapticEffect] exactly ONCE per (re-)trigger
 * (the mInitialPass gate), never on process frames, never in static mode (golden-safe).
 *
 * Fresh ctx + player per frame (live-app reality); the SAME inflated doc + [TouchState] carry the
 * cross-frame op-field state (impulse lastFrameTime, touchEventTime).
 */
class Rem143S3bTest {
    private class FakeHaptic : HapticActuator {
        val fired = ArrayList<Int>()
        override fun perform(hapticFeedbackType: Int) { fired += hapticFeedbackType }
    }

    private fun frame(
        doc: RemoteComposeDocument, touch: TouchState, haptic: HapticActuator, t: Float,
        tapAt: Pair<Float, Float>? = null, animate: Boolean = true,
    ) {
        if (tapAt != null) touch.down(tapAt.first, tapAt.second)
        val ctx = RemoteContext(); ctx.animationEnabled = animate
        RemoteComposePlayer(ctx).paint(
            doc, NoOpPaintContext(ctx), frameTimeSeconds = t,
            touchState = if (animate) touch else null, hapticActuator = haptic,
        )
    }

    /**
     * REM-152: the t=0 auto-start (our `id29=0` §0 *visual*-floor default) must NOT auto-buzz at launch —
     * the haptic fire is gated on a real touch (upstream waits for touch). This is the cold-launch-flakiness
     * fix: no race-prone auto-fire at all.
     */
    @Test
    fun haptic_doesNotAutoFireAtLaunch_withoutTouch() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/haptic_demo_demo_haptic1.rc"))
        val touch = TouchState(); val haptic = FakeHaptic()
        // Live cold-launch frames, NO touch — the impulse auto-starts (id29=0) but the haptic must stay silent.
        for (t in listOf(0f, 0.05f, 0.2f, 1.0f)) frame(doc, touch, haptic, t)
        assertTrue(haptic.fired.isEmpty(), "no auto-buzz at launch without a touch (REM-152), got ${haptic.fired}")
    }

    @Test
    fun haptic_firesOnTouch_oncePerTrigger_notOnProcessFrames() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/haptic_demo_demo_haptic1.rc"))
        val touch = TouchState(); val haptic = FakeHaptic()
        frame(doc, touch, haptic, 0f)                  // launch, no touch → silent
        frame(doc, touch, haptic, 0.2f)                // window elapsed → re-arm, still silent
        assertTrue(haptic.fired.isEmpty(), "pre-touch must be silent, got ${haptic.fired}")
        frame(doc, touch, haptic, 0.3f, tapAt = 150f to 150f) // tap (id29=0.3) → touch-triggered fire
        assertEquals(listOf(8), haptic.fired, "a touch must fire the haptic once (type 8)")
        frame(doc, touch, haptic, 0.35f)               // process frame within [0.3,0.4] → must NOT refire
        assertEquals(listOf(8), haptic.fired, "haptic must NOT refire on process frames")
        frame(doc, touch, haptic, 0.5f)                // 0.5 > 0.4 → window elapsed → re-arm, no fire
        assertEquals(listOf(8), haptic.fired, "elapsed window must not fire haptic")
        frame(doc, touch, haptic, 0.6f, tapAt = 150f to 150f) // second tap → fire again (one per trigger)
        assertEquals(listOf(8, 8), haptic.fired, "a re-trigger tap must fire the haptic again")
    }

    @Test
    fun haptic_neverFiresInStaticMode() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/haptic_demo_demo_haptic1.rc"))
        val haptic = FakeHaptic()
        for (t in listOf(0f, 0.05f, 0.1f)) frame(doc, TouchState(), haptic, t, animate = false)
        assertTrue(haptic.fired.isEmpty(), "static/golden renders must never buzz, got ${haptic.fired}")
    }
}
