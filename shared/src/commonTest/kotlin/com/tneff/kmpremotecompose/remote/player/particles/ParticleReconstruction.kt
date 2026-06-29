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

/**
 * REM-143 §5b #3 — the **independent** per-frame particle-state reconstruction: the bug-independent oracle
 * the multi-frame gate compares the sim's draws against.
 *
 * **Independence (the gate's whole validity rests on this):** the particle *orchestration* below — the
 * seed loop, the per-frame update loop, restart, VAR1=particle-index, and the resulting **RAND consumption
 * order** — is re-derived DIRECTLY from the upstream spec, NOT copied from the player's particle sim:
 *  - seed: particle-major outer, var inner (upstream `ParticlesCreate.initializeParticle`, :251 calling
 *    loop + :257 inner var loop); VAR1 resolves to the particle index; init evals var-by-var with NO
 *    inter-var context load (upstream init writes no context between vars).
 *  - update: per particle, load current vars, then eval each update eq var-by-var loading the result
 *    immediately (upstream `ParticlesLoop.paint` :295-302), then the restart eq (>0 ⇒ re-seed that
 *    particle, upstream :305-317).
 *
 * It DOES reuse the separately-proven [RpnFloatEvaluator] (REM-109/127) for per-equation eval and the
 * byte-decoded equations (data) — those are not the sim's particle path, so reusing them is not circular.
 * The RNG is pinned to [RenderRngPins.PARTICLE_SEED] before frame 0 (same seam + pin the sim-capture run
 * uses), so both consume an identical RAND sequence; an orchestration/order bug in the sim diverges here.
 *
 * @param varIds the particle variable ids (the slots in each particle's state vector).
 * @param initEqs one init equation per var (RPN; may reference VAR1 = particle index and OP_RAND).
 * @param restart the restart equation (>0 ⇒ recycle the particle), or null if the system never restarts.
 * @param updateEqs one update equation per var (RPN; references the current vars via [varIds]).
 */
class ParticleReconstruction(
    val particleCount: Int,
    val varIds: IntArray,
    val initEqs: Array<FloatArray>,
    val restart: FloatArray?,
    val updateEqs: Array<FloatArray>,
    val seed: Long = RenderRngPins.PARTICLE_SEED,
) {
    val varCount: Int get() = varIds.size

    /** One frame of reconstructed state: `[particle][var]`. */
    private fun snapshot(state: Array<FloatArray>): Array<FloatArray> = Array(particleCount) { state[it].copyOf() }

    /**
     * Reconstruct the particle state for frames 0..[frameCount] (frame 0 = seed). Reseeds the pinned RNG
     * once before frame 0, then advances it continuously across frames — exactly the capture run's contract.
     * [seedFrame] is invoked before each frame so the caller can seed system vars (e.g. time = startAt+k·dt)
     * into [ctx] the same way the player does; it defaults to a no-op for self-contained (synthetic) systems.
     */
    fun evolve(
        frameCount: Int,
        ctx: RemoteContext = RemoteContext(),
        seedFrame: (frame: Int, ctx: RemoteContext) -> Unit = { _, _ -> },
    ): List<Array<FloatArray>> {
        RpnFloatEvaluator.seedRngForCapture(seed)
        val state = Array(particleCount) { FloatArray(varCount) }

        // SEED (frame 0): particle-major outer, var inner; VAR1 = particle index (no inter-var load).
        seedFrame(0, ctx)
        for (p in 0 until particleCount) seedParticle(p, state, ctx)
        val frames = mutableListOf(snapshot(state))

        // EVOLUTION (frames 1..frameCount): update then restart, per particle.
        for (k in 1..frameCount) {
            seedFrame(k, ctx)
            for (p in 0 until particleCount) {
                for (v in 0 until varCount) ctx.loadFloat(varIds[v], state[p][v])
                for (v in 0 until varCount) {
                    state[p][v] = RpnFloatEvaluator.eval(updateEqs[v], updateEqs[v].size, ctx)
                    ctx.loadFloat(varIds[v], state[p][v])
                }
                if (restart != null && RpnFloatEvaluator.eval(restart, restart.size, ctx) > 0f) {
                    seedParticle(p, state, ctx)
                }
            }
            frames += snapshot(state)
        }
        return frames
    }

    /** Seed/re-seed one particle: eval each init eq with VAR1 = [p] (no inter-var context load). */
    private fun seedParticle(p: Int, state: Array<FloatArray>, ctx: RemoteContext) {
        for (v in 0 until varCount) {
            state[p][v] = RpnFloatEvaluator.eval(initEqs[v], initEqs[v].size, ctx, p.toFloat())
        }
    }

    /** Per-frame expected draw positions: `frames[k][p] = (state[k][p][xVar], state[k][p][yVar])`. */
    fun expectedPositions(
        frames: List<Array<FloatArray>>,
        xVarIndex: Int,
        yVarIndex: Int,
    ): List<List<Pair<Float, Float>>> =
        frames.map { frame -> List(particleCount) { p -> frame[p][xVarIndex] to frame[p][yVarIndex] } }
}
