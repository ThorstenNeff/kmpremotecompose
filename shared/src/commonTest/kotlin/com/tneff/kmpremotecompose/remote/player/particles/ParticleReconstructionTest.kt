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
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RenderRngPins
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-143 §5b #3 — validates the independent reconstruction orchestration on a synthetic particle system
 * with known equations: VAR1 = particle index, particle-major RAND order, per-frame evolution, and
 * determinism under the pinned seed. (Integration against the real sim's draw-capture runs once S2 lands.)
 */
class ParticleReconstructionTest {

    private val off = RpnFloatEvaluator.OFFSET
    private fun op(code: Int) = WireTypes.asNan(off + code)
    private fun varRef(id: Int) = WireTypes.asNan(id)
    private val VAR1 = op(70)
    private val RAND = op(39)
    private val ADD = op(1)
    private val MUL = op(3)

    private val X = 200
    private val Y = 201

    // x init = VAR1 (particle index); y init = RAND*100. x update = x+1; y update = y (unchanged). No restart.
    private fun system() = ParticleReconstruction(
        particleCount = 3,
        varIds = intArrayOf(X, Y),
        initEqs = arrayOf(
            floatArrayOf(VAR1),
            floatArrayOf(RAND, 100f, MUL),
        ),
        restart = null,
        updateEqs = arrayOf(
            floatArrayOf(varRef(X), 1f, ADD),
            floatArrayOf(varRef(Y)),
        ),
    )

    /** The first n RAND draws for the pinned seed, via the same eval path the reconstruction uses. */
    private fun firstRands(n: Int): List<Float> {
        val ctx = RemoteContext()
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)
        return (0 until n).map { RpnFloatEvaluator.eval(floatArrayOf(RAND), 1, ctx) }
    }

    @Test fun seedFrame_var1IsParticleIndex_andRandIsParticleMajor() {
        val frames = system().evolve(frameCount = 0)
        val seed = frames[0]
        // x = particle index 0,1,2.
        assertEquals(0f, seed[0][0]); assertEquals(1f, seed[1][0]); assertEquals(2f, seed[2][0])
        // y = RAND*100 consumed particle-major (p0 then p1 then p2 — only y uses RAND).
        val r = firstRands(3)
        assertEquals(r[0] * 100f, seed[0][1], "p0.y = rand0*100")
        assertEquals(r[1] * 100f, seed[1][1], "p1.y = rand1*100 (particle-major order)")
        assertEquals(r[2] * 100f, seed[2][1], "p2.y = rand2*100")
    }

    @Test fun evolution_xIncrementsPerFrame_yHolds() {
        val recon = system()
        val frames = recon.evolve(frameCount = 3)
        // x[p] at frame k = p + k.
        for (k in 0..3) for (p in 0..2) assertEquals((p + k).toFloat(), frames[k][p][0], "x[p=$p] @ frame $k")
        // y holds its seed value across frames.
        for (k in 0..3) for (p in 0..2) assertEquals(frames[0][p][1], frames[k][p][1], "y[p=$p] holds @ frame $k")
    }

    @Test fun deterministic_sameSeedSameResult() {
        val a = system().evolve(frameCount = 4).map { it.map { p -> p.toList() } }
        val b = system().evolve(frameCount = 4).map { it.map { p -> p.toList() } }
        assertEquals(a, b, "pinned seed ⇒ identical reconstruction (deterministic oracle)")
    }

    @Test fun expectedPositions_mapsVarsToXY() {
        val recon = system()
        val frames = recon.evolve(frameCount = 2)
        val pos = recon.expectedPositions(frames, xVarIndex = 0, yVarIndex = 1)
        assertEquals(3, pos[0].size)
        assertEquals(0f to frames[0][0][1], pos[0][0])
        assertEquals(2f to frames[2][0][1], pos[2][0], "x evolved to 2 by frame 2")
    }
}
