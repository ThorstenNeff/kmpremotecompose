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

    @Test
    fun haptic_firesOncePerTrigger_notOnProcessFrames() {
        Builtins.register()
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/haptic_demo_demo_haptic1.rc"))
        val touch = TouchState(); val haptic = FakeHaptic()
        frame(doc, touch, haptic, 0f)                  // initial pass (Δt=0) → fire once (type 8)
        assertEquals(listOf(8), haptic.fired, "haptic must fire once on the impulse initial pass")
        frame(doc, touch, haptic, 0.05f)               // process frame within [0,0.1] → must NOT refire
        assertEquals(listOf(8), haptic.fired, "haptic must NOT refire on process frames")
        frame(doc, touch, haptic, 0.2f)                // 0.2 > 0.1 → window elapsed → re-arm, no fire
        assertEquals(listOf(8), haptic.fired, "elapsed window must not fire haptic")
        frame(doc, touch, haptic, 0.3f, tapAt = 150f to 150f) // tap re-triggers (id29=0.3) → fire again
        assertEquals(listOf(8, 8), haptic.fired, "a re-trigger tap must fire the haptic again (one per trigger)")
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
