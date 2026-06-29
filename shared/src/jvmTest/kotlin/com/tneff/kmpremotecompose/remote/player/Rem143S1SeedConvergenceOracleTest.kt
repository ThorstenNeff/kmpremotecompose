/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 *
 * REM-143-S1 Seed-Daten-Orakel — independent convergence gate (test-3 lane).
 *
 * Spec source: upstream `androidx/compose/remote/remote-core/.../ParticlesCreate.java:250-266`
 *   paint():            for i in 0..count: initializeParticle(i)
 *   initializeParticle: for j in 0..varCount: { inject VAR1=i at mIndexeVars positions; eval(eq[j]) }
 *
 * Independence-discipline (PO `1521181920355024966` + `1521184992179257555`):
 *   - Reuse: RpnFloatEvaluator.eval() (Player-Primitive), byte-decoded pc.equations (input-data),
 *     byte-decoded ctx system + DATA_FLOAT vars (populated by player.paint()).
 *   - Reimplement: orchestration (i × j loop + VAR1-mutate-in-place via own NaN-scan + literal-Float
 *     substitution; NOT dev-2's t-overload simplification).
 *   - Reseed [RenderRngPins.PARTICLE_SEED] BEFORE EACH pass (Sim + Oracle) — no shared global RNG.
 *   - Divergence = ESCALATE to PO, NOT silent oracle-massage (memory: feedback_oracle_sim_divergence_escalate).
 *
 * Result: 6/6 bit-exact convergence (282 particles total). dev-2's t-overload-simplification IS
 * order-equivalent to upstream-spec VAR1-mutate-in-place — RAND-consumption-order convergent.
 */
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCreate
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RenderRngPins
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class Rem143S1SeedConvergenceOracleTest {

    /** VAR1 NaN-encoded token bits per RpnFloatEvaluator.OP_VAR1 (= OFFSET + 70). */
    private val VAR1_NAN_BITS: Int = WireTypes.asNan(RpnFloatEvaluator.OFFSET + 70).toRawBits()

    /**
     * Independent reconstruction per upstream spec (ParticlesCreate.java:251/257).
     * Particle-major × var-major × VAR1-mutate-in-place × eval-without-t-overload.
     *
     * NOTE: caller MUST reseed RpnFloatEvaluator.rng to RenderRngPins.PARTICLE_SEED IMMEDIATELY before
     * calling this (independent RNG state from any prior sim pass).
     */
    private fun reconstructSeedPositions(pc: ParticlesCreate, ctx: RemoteContext): Array<FloatArray> {
        val out = Array(pc.particleCount) { FloatArray(pc.varIds.size) }
        for (i in 0 until pc.particleCount) {                       // SPEC step 1: particle-major (outer)
            for (j in pc.varIds.indices) {                          // SPEC step 2: var-major (inner)
                val mutated = pc.equations[j].copyOf()              // SPEC step 3: in-place mutate (on copy)
                for (k in mutated.indices) {
                    val v = mutated[k]
                    if (v.isNaN() && v.toRawBits() == VAR1_NAN_BITS) {
                        mutated[k] = i.toFloat()                    // VAR1 → literal-float(i)
                    }
                }
                // SPEC step 4: eval the mutated equation; VAR1 is now a literal-float, so the
                // 3-arg eval (which passes t=NaN) never triggers OP_VAR1 — RAND-consumption-order
                // is byte-token-order, unaffected by t.
                out[i][j] = RpnFloatEvaluator.eval(mutated, mutated.size, ctx)
            }
        }
        return out
    }

    @Test fun rem143_s1_confetti() = assertConvergence("impulse_demo_confetti_demo")
    @Test fun rem143_s1_hearts()   = assertConvergence("impulse_demo_hearts_demo")
    @Test fun rem143_s1_particle() = assertConvergence("particle")
    @Test fun rem143_s1_maze()     = assertConvergence("maze")
    @Test fun rem143_s1_maze1()    = assertConvergence("maze1")
    @Test fun rem143_s1_maze2()    = assertConvergence("maze2")

    private fun assertConvergence(docName: String) {
        Builtins.register()
        val bytes = RcCorpus.readFixture("corpus/$docName.rc")
        val doc = DocumentReader.inflate(bytes)
        val pc = doc.operations.filterIsInstance<ParticlesCreate>().firstOrNull()
            ?: fail("$docName has no ParticlesCreate op in the inflated document")

        // ===== PASS 1: Sim-Capture (dev-2's ParticlesCreate.apply via player paint pass) =====
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)
        val ctxSim = RemoteContext().apply { animationEnabled = false; seedHostPalette() }
        val paintSim = NoOpPaintContext(ctxSim)
        val w = (if (doc.width > 0) doc.width else 300).toFloat()
        val h = (if (doc.height > 0) doc.height else 300).toFloat()
        RemoteComposePlayer(ctxSim).paint(
            document = doc, paint = paintSim,
            surfaceWidth = w, surfaceHeight = h,
            frameTimeSeconds = 0f, staticTimeSeconds = 0f,
        )
        val simParticles: Array<FloatArray> = Array(pc.particles.size) { pc.particles[it].copyOf() }

        // ===== PASS 2: Independent Oracle Reconstruction =====
        // Pass 2 uses a FRESH inflate of the same .rc bytes — separate ParticlesCreate instance with its
        // own particles[] (untouched by Pass 1), separate RemoteContext. The doc has DATA_FLOAT ops that
        // load values into ctx before PARTICLE_DEFINE (e.g. hearts var[47]=150). These must be populated
        // for the equations to evaluate correctly — running player.paint() does that (its Phase-A apply
        // walks DATA_FLOAT/ANIMATED_FLOAT ops which load the ctx). seedSystemVariables alone is
        // insufficient because it only covers WINDOW_*/DENSITY/clock, not doc-declared DataVariables.
        //
        // Sequence: reseed PIN → paint (populates ctx + dev-2's apply consumes RNG) → reseed PIN AGAIN
        // (clean state for my orchestration) → my orchestration on populated ctx.
        // ctx + equations are byte-decoded input-data (reusable per independence-discipline); only the
        // ORCHESTRATION (i × j loop, VAR1-mutate, no-t-overload eval) is my reimplementation.
        val docB = DocumentReader.inflate(bytes)                                     // fresh inflate
        val pcB = docB.operations.filterIsInstance<ParticlesCreate>().first()
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)
        val ctxOracle = RemoteContext().apply { animationEnabled = false; seedHostPalette() }
        val paintOracle = NoOpPaintContext(ctxOracle)
        RemoteComposePlayer(ctxOracle).paint(
            document = docB, paint = paintOracle,
            surfaceWidth = w, surfaceHeight = h,
            frameTimeSeconds = 0f, staticTimeSeconds = 0f,
        )
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)             // INDEPENDENT reseed
        val myParticles: Array<FloatArray> = reconstructSeedPositions(pcB, ctxOracle)

        // ===== COMPARE (bit-exact) =====
        assertEquals(pc.particleCount, simParticles.size, "$docName: sim particleCount mismatch")
        assertEquals(pc.particleCount, myParticles.size, "$docName: oracle particleCount mismatch")
        assertTrue(simParticles.size > 0, "$docName: zero particles — sanity check")
        for (i in 0 until pc.particleCount) {
            assertEquals(pc.varIds.size, simParticles[i].size, "$docName particle[$i] sim var-count")
            assertEquals(pc.varIds.size, myParticles[i].size, "$docName particle[$i] oracle var-count")
            for (j in pc.varIds.indices) {
                val sim = simParticles[i][j]
                val orc = myParticles[i][j]
                // Bit-exact: same equations + same RAND sequence + same RNG state ⇒ identical Float bits.
                // Use toRawBits to treat NaN-equality and signed-zero correctly.
                if (sim.toRawBits() != orc.toRawBits()) {
                    fail(
                        "$docName divergence at particle[$i].var[$j] (varId=${pc.varIds[j]}): " +
                            "oracle=$orc (bits=0x${orc.toRawBits().toString(16)}) vs " +
                            "sim=$sim (bits=0x${sim.toRawBits().toString(16)}). " +
                            "Hypothese A: dev-2's t-overload-simplification is NOT order-equivalent to " +
                            "upstream-spec VAR1-mutate-in-place (RAND-consumption-order divergent). " +
                            "Hypothese B: this oracle has a bug (VAR1-detection / eval-order / reseed). " +
                            "ESCALATE to PO for cross-routing (per memory feedback_oracle_sim_divergence_escalate)."
                    )
                }
            }
        }
        println("[REM-143-S1] $docName: ${pc.particleCount} particles × ${pc.varIds.size} vars — bit-exact convergence ✅")
    }
}
