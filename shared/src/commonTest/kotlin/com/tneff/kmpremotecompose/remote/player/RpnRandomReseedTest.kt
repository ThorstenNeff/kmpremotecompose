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

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RenderRngPins
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-143 — the [RpnFloatEvaluator.seedRngForCapture] pin seam + the shared [RenderRngPins.PARTICLE_SEED]
 * constant. The particle gate needs a reproducible RAND sequence (the corpus particle docs carry no
 * RAND_SEED). This proves the seam makes OP_RAND deterministic per-run and reseedable before each run — the
 * foundation of the multi-frame data-oracle gate AND test-3's reseed-before-paint 173-sweep. Additive: a
 * doc that never reseeds is unaffected (RAND stays Random.Default).
 */
class RpnRandomReseedTest {

    private val ctx = RemoteContext()
    // a single-token RPN expression: just OP_RAND (OFFSET+39), NaN-encoded.
    private val randExp = floatArrayOf(WireTypes.asNan(RpnFloatEvaluator.OFFSET + 39))

    private fun drawN(n: Int): List<Float> = (0 until n).map { RpnFloatEvaluator.eval(randExp, 1, ctx) }

    @Test fun samePin_reproducesTheSameSequence() {
        RpnFloatEvaluator.seedRngForCapture(0x5EEDL)
        val first = drawN(8)
        RpnFloatEvaluator.seedRngForCapture(0x5EEDL)
        val second = drawN(8)
        assertEquals(first, second, "same pin ⇒ identical RAND sequence (deterministic capture)")
        // all in [0,1)
        for (v in first) assertTrue(v >= 0f && v < 1f, "RAND in [0,1): $v")
    }

    @Test fun differentPin_divergesQuickly() {
        RpnFloatEvaluator.seedRngForCapture(1L)
        val a = drawN(8)
        RpnFloatEvaluator.seedRngForCapture(2L)
        val b = drawN(8)
        assertTrue(a != b, "different pins ⇒ different sequences")
    }

    @Test fun reseedBeforeEachRun_isolatesRuns() {
        // Mirrors the gate: reseed before the (sim-capture) run, then reseed before the (reconstruction)
        // run → both see the identical sequence even though the singleton rng is shared and sequential.
        RpnFloatEvaluator.seedRngForCapture(42L)
        val simRun = drawN(5)
        RpnFloatEvaluator.seedRngForCapture(42L)
        val reconstructionRun = drawN(5)
        assertEquals(simRun, reconstructionRun)
    }

    @Test fun sharedParticleSeedConstant_isDeterministic() {
        // Both harnesses pin to the single-source RenderRngPins.PARTICLE_SEED → identical sequences.
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)
        val a = drawN(6)
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)
        val b = drawN(6)
        assertEquals(a, b, "shared PARTICLE_SEED ⇒ both harnesses get the same RAND sequence")
    }
}
