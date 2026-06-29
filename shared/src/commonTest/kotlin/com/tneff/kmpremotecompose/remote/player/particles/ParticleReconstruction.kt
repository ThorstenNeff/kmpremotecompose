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
    val compares: List<Compare> = emptyList(),
    val seed: Long = RenderRngPins.PARTICLE_SEED,
) {
    val varCount: Int get() = varIds.size

    /**
     * REM-143 (iii): a `PARTICLE_COMPARE` condition1Body spec (eq2 empty) — for each particle in
     * `[min,max]` (`<0` ⇒ full range), if `eval(expr) > 0` apply [eq1] to the particle's vars. Decoded
     * from the upstream op (data); the conditional-evolution semantics are re-derived from the upstream
     * spec (ParticlesCompare.condition1Body), not the sim.
     */
    class Compare(val min: Float, val max: Float, val expr: FloatArray, val eq1: Array<FloatArray>)

    /** One frame of reconstructed state: `[particle][var]`. */
    private fun snapshot(state: Array<FloatArray>): Array<FloatArray> = Array(particleCount) { state[it].copyOf() }

    /** Mutable per-particle state `[particle][var]`, advanced by [seedAllParticles]/[stepFrame]. */
    private var state: Array<FloatArray> = Array(particleCount) { FloatArray(varCount) }

    /** An immutable snapshot of the current state. */
    fun snapshotState(): Array<FloatArray> = snapshot(state)

    /**
     * Seed every particle (frame 0): particle-major outer, var inner; VAR1 = particle index (no inter-var
     * load). Does NOT pin the RNG — a multi-system doc must pin ONCE in the driver so its single continuous
     * RNG is consumed frame-major across systems (sys0-seed, sys1-seed, …), exactly like the sim. [ctx] must
     * already carry the doc-var env + this frame's Δt.
     */
    fun seedAllParticles(ctx: RemoteContext) {
        state = Array(particleCount) { FloatArray(varCount) }
        for (p in 0 until particleCount) seedParticle(p, state, ctx)
    }

    /**
     * Advance one evolution frame: per particle, update var-major in-place (later var sees earlier via ctx)
     * then the restart eq (`>0` ⇒ re-seed/recycle), then the conditional compares. Draws RAND in the spec
     * order; does NOT touch the RNG pin (the driver owns it). [ctx] must carry this frame's Δt.
     */
    fun stepFrame(ctx: RemoteContext) {
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
        // PARTICLE_COMPARE (condition1Body): after the loop update, each compare conditionally mutates a
        // particle's vars (maze wall collision). Runs per-compare in doc order, like the sim's walk.
        for (cmp in compares) {
            val start = if (cmp.min < 0f) 0 else cmp.min.toInt()
            val end = if (cmp.max < 0f) particleCount else cmp.max.toInt()
            for (p in start until minOf(end, particleCount)) {
                for (v in 0 until varCount) ctx.loadFloat(varIds[v], state[p][v])
                if (RpnFloatEvaluator.eval(cmp.expr, cmp.expr.size, ctx) > 0f) {
                    for (v in cmp.eq1.indices) {
                        if (v >= varCount) break
                        state[p][v] = RpnFloatEvaluator.eval(cmp.eq1[v], cmp.eq1[v].size, ctx)
                        ctx.loadFloat(varIds[v], state[p][v])
                    }
                }
            }
        }
    }

    /**
     * Single-system convenience: pin the RNG, seed frame 0, then step [frameCount] frames. Multi-system
     * docs must instead use [seedAllParticles]/[stepFrame] via a driver that pins the RNG ONCE across all
     * systems (see Rem143EvolutionGateTest) — otherwise a per-system re-pin desyncs the shared RAND order.
     * [seedFrame] is invoked before each frame so the caller can seed Δt / env into [ctx] like the player.
     */
    fun evolve(
        frameCount: Int,
        ctx: RemoteContext = RemoteContext(),
        seedFrame: (frame: Int, ctx: RemoteContext) -> Unit = { _, _ -> },
    ): List<Array<FloatArray>> {
        RpnFloatEvaluator.seedRngForCapture(seed)
        seedFrame(0, ctx)
        seedAllParticles(ctx)
        val frames = mutableListOf(snapshotState())
        for (k in 1..frameCount) {
            seedFrame(k, ctx)
            stepFrame(ctx)
            frames += snapshotState()
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
