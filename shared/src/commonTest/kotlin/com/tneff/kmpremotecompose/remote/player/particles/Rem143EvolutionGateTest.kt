/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCompare
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCreate
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesLoop
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-143 §5b — the LIVE multi-frame EVOLUTION gate: dev-2's S2 sim (full-doc paint, draw-capture) vs the
 * independent per-frame reconstruction. (i) seed-frame matches + (ii) frame-k evolution converges.
 */
@IgnoreOnWasm
class Rem143EvolutionGateTest {

    private val DT = ParticleFrameSchedule.DT

    /**
     * Run a system's body ops with reconstruction vars for frame [k] → the expected per-particle anchors.
     * The body's matrix ops reference DOC-level vars (sizes, scales), so the context is seeded exactly as
     * the player does — system vars + the doc's variable phase — then MY independent particle vars are
     * overlaid (the doc Phase-A is generic setup, not the particle orchestration → independence holds).
     */
    private fun expectedAnchors(
        doc: com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument,
        system: ParticleSystemDecoder.System,
        frames: List<Array<FloatArray>>,
        schedule: ParticleFrameSchedule.Schedule,
    ): List<List<RecordingParticlePaintContext.Draw>> {
        val docW = doc.width.toFloat(); val docH = doc.height.toFloat()
        return frames.mapIndexed { k, frame ->
            val ctx = RemoteContext().also { it.setDensity(1f); it.animationEnabled = true; it.seedHostPalette() }
            ctx.seedSystemVariables(docW, docH, schedule.times[k], 1f)
            ctx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, if (k == 0) 0f else DT)
            // doc variable phase (generic doc vars; particle ops are seeded-once → harmless no-op here).
            for (op in doc.operations) if (op is VariableSupport) { op.updateVariables(ctx); op.apply(ctx) }
            val anchors = mutableListOf<RecordingParticlePaintContext.Draw>()
            for (p in 0 until system.create.particleCount) {
                for (v in system.create.varIds.indices) ctx.loadFloat(system.create.varIds[v], frame[p][v])
                val rec = RecordingParticlePaintContext(ctx)
                for (op in system.body) {
                    if (op is VariableSupport) { op.updateVariables(ctx); op.apply(ctx) }
                    if (op is PaintOperation) op.paint(ctx, rec)
                }
                // ONE anchor per particle = the body's first draw (the placement = particle position).
                // Later body draws are decoration/shape (e.g. a DRAW_LINE offset by the per-particle scale),
                // which encode geometry, not position, and must not pollute the position-evolution match.
                rec.draws.firstOrNull()?.let { anchors += it }
            }
            anchors
        }
    }

    /** Every expected anchor must be ε-matched by some captured anchor in the same frame. */
    private fun unmatched(expected: List<RecordingParticlePaintContext.Draw>, captured: List<RecordingParticlePaintContext.Draw>, tol: Float): Int =
        expected.count { e -> captured.none { c -> abs(c.cx - e.cx) <= tol && abs(c.cy - e.cy) <= tol } }

    /**
     * A per-doc oracle result. [anyExpected] is a non-vacuity PRECONDITION: a doc whose reconstruction
     * produces ZERO expected anchors validates nothing (0 expected ⇒ 0 unmatched trivially), so it must
     * FAIL, not pass — and [injectedShiftDetected] must NOT short-circuit on it (that hole vacuous-passed
     * hearts; REM-147 assist review).
     */
    data class Result(val seedUnmatched: Int, val totalUnmatched: Int, val injectedShiftDetected: Boolean, val anyExpected: Boolean)

    /** @return real (un)matched + the META-TEST: a +50px-shifted "wrong sim" MUST yield unmatched>0 in every
     *  frame that has expected anchors — an oracle that does not flag a known divergence is vacuous. */
    private fun runDoc(name: String): Result {
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/$name"))
        val systems = ParticleSystemDecoder.decode(doc)
        val schedule = ParticleFrameSchedule.fromDoc(doc) ?: return Result(0, 0, true, true)
        val captured = ParticleGateHarness.captureFrames(doc, schedule)
        var totalUnmatched = 0
        var seedUnmatched = 0
        var injectedAllDetected = true
        var anyExpected = false
        val docW = doc.width.toFloat(); val docH = doc.height.toFloat()
        // ONE reconCtx + ONE env-seed + ONE RNG pin for the WHOLE doc, then evolve all systems FRAME-MAJOR
        // in doc order. A multi-system doc (e.g. particle.rc, 2 systems) shares the sim's single continuous
        // RNG drawn frame-major across systems (f0: sys0-seed, sys1-seed; f1: sys0-RAND, sys1-RAND; …); a
        // per-system re-pin (the old `for system { evolve() }`) desyncs that order → only the RAND-driven
        // vars diverge. Resolve the doc-var env (canvas dims id5/6, layout-resolved floats id43/id58) via
        // ONE player eval-pass at startAt — done BEFORE the RNG pin so its RAND is discarded by the re-pin,
        // leaving the pinned PARTICLE-RAND untouched (the §6/REM-123 independence boundary; id43/id58 are
        // generic doc-float data, not the particle orchestration).
        val reconCtx = RemoteContext().also { it.setDensity(1f); it.animationEnabled = true; it.seedHostPalette() }
        reconCtx.seedSystemVariables(docW, docH, schedule.startAt, 1f)
        RemoteComposePlayer(reconCtx).paint(doc, com.tneff.kmpremotecompose.remote.player.NoOpPaintContext(reconCtx), frameTimeSeconds = schedule.startAt)
        com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator.seedRngForCapture(com.tneff.kmpremotecompose.remote.player.core.RenderRngPins.PARTICLE_SEED)
        val framesPerSystem: List<MutableList<Array<FloatArray>>> = systems.map { mutableListOf() }
        reconCtx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, 0f) // seed frame (Δt=0)
        for (system in systems) system.reconstruction.seedAllParticles(reconCtx)
        systems.forEachIndexed { si, system -> framesPerSystem[si].add(system.reconstruction.snapshotState()) }
        for (k in 1..schedule.frameCount) {
            reconCtx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, DT)
            for (system in systems) system.reconstruction.stepFrame(reconCtx)
            systems.forEachIndexed { si, system -> framesPerSystem[si].add(system.reconstruction.snapshotState()) }
        }
        systems.forEachIndexed { si, system ->
            val expected = expectedAnchors(doc, system, framesPerSystem[si], schedule)
            for (k in expected.indices) {
                val cap = captured.getOrElse(k) { emptyList() }
                totalUnmatched += unmatched(expected[k], cap, tol = 0.1f)
                if (k == 0) seedUnmatched += unmatched(expected[k], cap, tol = 0.1f)
                if (expected[k].isNotEmpty()) {
                    anyExpected = true
                    // META: shift every expected anchor +50px → a vacuous oracle would still match.
                    val shifted = expected[k].map { RecordingParticlePaintContext.Draw(it.cx + 50f, it.cy + 50f) }
                    if (unmatched(shifted, cap, tol = 0.1f) == 0) injectedAllDetected = false
                }
            }
        }
        // injectedShiftDetected is the RAW detection result — NOT short-circuited on anyExpected (the
        // short-circuit vacuous-passed a zero-expected doc). anyExpected is asserted separately as a
        // precondition, so a doc that produces nothing to validate fails loudly.
        return Result(seedUnmatched, totalUnmatched, injectedAllDetected, anyExpected)
    }

    @Test fun allSixParticleDocs_evolutionGate() {
        val docs = listOf(
            "particle.rc", "impulse_demo_confetti_demo.rc", "impulse_demo_hearts_demo.rc",
            "maze.rc", "maze1.rc", "maze2.rc",
        )
        println("===== REM-143 EVOLUTION GATE — all 6 particle docs =====")
        val results = docs.map { d -> d to runDoc(d) }
        for ((d, r) in results) println("  $d: seed=${r.seedUnmatched} evolution=${r.totalUnmatched} injectedShiftDetected=${r.injectedShiftDetected} anyExpected=${r.anyExpected}")
        println("===== END GATE =====")
        // A doc is VALIDATED only if ALL of: (pre) the reconstruction actually PRODUCED expected anchors —
        // zero-expected validates nothing and is vacuous by definition; (a) real evolution converges (0
        // unmatched, seed + every frame); (b) the META-TEST holds (a +50px-shifted "wrong sim" is flagged).
        // The three REM-147 false-greens (origin-capture, zero-expected, vacuous short-circuit) all die here.
        for ((d, r) in results) {
            assertTrue(r.anyExpected, "$d: reconstruction must PRODUCE expected anchors — zero-expected validates nothing (vacuous)")
            assertEquals(0, r.seedUnmatched, "$d: seed-frame must match the independent reconstruction (Δt=0 seed)")
            assertEquals(0, r.totalUnmatched, "$d: every frame's evolution must match the independent reconstruction")
            assertTrue(r.injectedShiftDetected, "$d: oracle must FLAG a +50px injected divergence (non-vacuous)")
        }
    }
}
