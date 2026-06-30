/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.NoOpPaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RenderRngPins
import com.tneff.kmpremotecompose.remote.player.core.RpnFloatEvaluator
import com.tneff.kmpremotecompose.remote.player.core.TouchState
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-143 §5b S3-Re-Confirm — the **LIVE multi-frame Re-Trigger** gate (extends [Rem143EvolutionGateTest]
 * for the S3a `mInitialPass` re-arm path): sim's evolution + tap-after-elapse re-burst == the independent
 * particle reconstruction re-seeded at the same tap-frame T.
 *
 * Re-Trigger sim-lifecycle (per `RemoteComposePlayer.runImpulse`):
 *  - Phase 1 (k=0..90, the standard EvolutionGateTest window): evolve.
 *  - Phase 2 (elapse jump, 1 paint at `t = duration + 0.5`): `now > startAt+duration` → `resetSeed()` fires
 *    on the body's ParticlesCreate + `lastFrameTime` cleared to NaN. No body paint, NO RAND consumed
 *    (elapse-branch returns before Δt-load / body walk).
 *  - Phase 3 (tap, 1 paint at `t = tap_t` with `TouchState.down()` injected): `dispatchTouch` consumes the
 *    DOWN → sets `touchEventTime = tap_t` → id29 (TOUCH_EVENT_TIME) reloaded; `runImpulse` resolves
 *    `startAt = id29 = tap_t` → window shifts to `[tap_t, tap_t+duration]`, not elapsed; `isInitialPass=true`
 *    (lastFrameTime was NaN) → Phase-A's `ParticlesCreate.apply` re-seeds (consumes RAND for init eqs from
 *    the CURRENT RNG state — continuous through Re-Trigger per dev-1 triage point #1). ParticlesLoop draws
 *    the new seed at Δt=0 (dev-1 triage point #3: first Re-Burst frame = Δt=0, like the initial seed).
 *  - Phase 4 (post-tap, k=1..M at `t = tap_t + k·DT`): standard evolution from the new seed.
 *
 * Recon mirror per [ParticleReconstruction] (re-seeded at T, NOT a new oracle path per PO routing):
 *  - Phase 1: standard `seedAll`@k=0 + `stepFrame`@k=1..90 (consumes the same RAND as the sim's 91 paints).
 *  - Phase 2: no recon action (sim consumes no RAND).
 *  - Phase 3: `seedAllParticles(ctx)` (consumes init RAND from current RNG state, identical to sim's apply).
 *  - Phase 4: `stepFrame` per frame.
 *
 * Comparison: every expected reconstruction position at frames {Phase 3, Phase 4-each} must be ε-matched by
 * some captured sim anchor at the same frame (RecordingParticlePaintContext, matrix-aware — NOT a white-box
 * `op.mParticles` readback, which would share the sim path → false-green). Non-vacuity meta: a +50px-shifted
 * expected MUST fail (a vacuous oracle would still match).
 *
 * Scope: confetti / hearts / particle — the 3 docs with realistic duration (<=20s) where impulse-elapse
 * actually triggers in a tractable schedule. maze docs carry duration=20000s (never elapse in any practical
 * window), so Re-Trigger is not exercisable on them — explicitly out of scope here.
 */
class Rem143S3RetriggerGateTest {

    private val DT: Float = ParticleFrameSchedule.DT
    private val BASELINE_FRAMES: Int = 90                  // mirrors EvolutionGateTest pre-elapse window
    private val POST_TAP_FRAMES: Int = 10                  // small window to verify post-burst evolution
    private val TOL: Float = 0.1f                          // matches EvolutionGateTest tolerance
    private val INJECTED_SHIFT_PX: Float = 50f             // non-vacuity meta

    /** Doc-specific Re-Trigger schedule (per dev-1 triage #2: tap must land in the post-elapse re-arm path). */
    private data class DocSchedule(val name: String, val duration: Float) {
        val elapseT: Float get() = duration + 0.5f                         // > startAt(=0)+duration → elapse-branch
        val tapT: Float get() = duration + 1.0f                            // tap injects DOWN here → re-burst
        fun postTapTimes(n: Int): List<Float> = (1..n).map { tapT + it * (1f / 30f) }
    }

    @Test fun confetti_reTrigger_simEqualsReconReseededAtT() = runRetriggerGate(DocSchedule("impulse_demo_confetti_demo.rc", duration = 20f))
    @Test fun hearts_reTrigger_simEqualsReconReseededAtT()   = runRetriggerGate(DocSchedule("impulse_demo_hearts_demo.rc",   duration = 7.9f))
    @Test fun particle_reTrigger_simEqualsReconReseededAtT() = runRetriggerGate(DocSchedule("particle.rc",                   duration = 10f))

    private fun runRetriggerGate(spec: DocSchedule) {
        Builtins.register()
        val bytes = RcCorpus.readFixture("corpus/${spec.name}")
        // Separate inflates: sim's full-paint walks mutate doc-level op-fields (ParticlesCreate.particles,
        // ParticlesLoop op-state, layout-measure caches) which influence the recon-side env-eval pass's
        // computed env vars (id43/id58 etc.). A fresh recon-inflate runs env-eval against pristine
        // doc-state — identical to an isolated EvolutionGateTest run (verified via
        // zz_diagnostic_isolated_hearts_evoGate_replicate). Recon-side anchor computation also uses
        // the recon-doc instance to avoid state shared with sim.
        val docSim = DocumentReader.inflate(bytes)
        val docRecon = DocumentReader.inflate(bytes)
        val systems = ParticleSystemDecoder.decode(docRecon)
        assertTrue(systems.isNotEmpty(), "${spec.name}: no particle systems decoded")
        val docW = docSim.width.toFloat(); val docH = docSim.height.toFloat()

        // ===== SIM-CAPTURE — frame-major one-pin schedule with tap-injection at spec.tapT =====
        val captured = captureSimFrames(docSim, spec)

        // ===== RECON-MIRROR — same one-pin, re-seed at the same tap-frame =====
        val expected = reconExpected(docRecon, systems, spec, docW, docH)

        // ===== COMPARE — re-burst (Phase 3) + post-burst (Phase 4) frames must match =====
        // Phase 1 is already covered by EvolutionGateTest; here we focus on Re-Trigger correctness.
        val phase34FrameCount = 1 + POST_TAP_FRAMES                       // tap + post-tap
        val capturedR = captured.takeLast(phase34FrameCount)
        val phase34Times = listOf(spec.tapT) + spec.postTapTimes(POST_TAP_FRAMES)
        var totalUnmatched = 0
        var rebuurstUnmatched = 0
        var anyExpected = false
        var injectedAllDetected = true
        for (si in systems.indices) {
            val system = systems[si]
            val sysExpected = expected[si].takeLast(phase34FrameCount)
            for (k in capturedR.indices) {
                val capFrame = capturedR[k]
                val expFrame = sysExpected[k]
                val expectedAnchors = expectedAnchorsForFrame(docRecon, system, expFrame, dtForFrame(k), phase34Times[k])
                val unmatchedHere = unmatched(expectedAnchors, capFrame, TOL)
                totalUnmatched += unmatchedHere
                if (k == 0) rebuurstUnmatched += unmatchedHere
                if (expectedAnchors.isNotEmpty()) {
                    anyExpected = true
                    val shifted = expectedAnchors.map { RecordingParticlePaintContext.Draw(it.cx + INJECTED_SHIFT_PX, it.cy + INJECTED_SHIFT_PX) }
                    if (unmatched(shifted, capFrame, TOL) == 0) injectedAllDetected = false
                }
            }
        }
        println("[REM-143 S3-Re-Confirm ${spec.name}] reBurstUnmatched=$rebuurstUnmatched postTapUnmatched=${totalUnmatched - rebuurstUnmatched} injectedShiftDetected=$injectedAllDetected anyExpected=$anyExpected")

        assertTrue(anyExpected, "${spec.name}: recon must produce expected anchors at re-burst (zero-expected = vacuous)")
        assertEquals(0, rebuurstUnmatched, "${spec.name}: re-burst frame (Phase 3) sim must match recon-post-Re-Trigger seed")
        assertEquals(0, totalUnmatched, "${spec.name}: every post-tap frame (Phase 4) sim must match recon-post-Re-Trigger evolution")
        assertTrue(injectedAllDetected, "${spec.name}: oracle must flag a +${INJECTED_SHIFT_PX.toInt()}px injected divergence (non-vacuous meta)")
    }

    /** Sim-side capture: 91 baseline + 1 elapse-jump + 1 tap (with TouchState.down) + POST_TAP_FRAMES evolve.
     *  Pass touchState=null for Phase 1/2 (mirrors ParticleGateHarness.captureFrames exactly — never load id29
     *  unless we actually need it), touchState=ts on Phase 3/4 (so the tap-DOWN registers and id29 stays set). */
    private fun captureSimFrames(doc: RemoteComposeDocument, spec: DocSchedule): List<List<RecordingParticlePaintContext.Draw>> {
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)             // ONE pin BEFORE Phase 1
        val out = ArrayList<List<RecordingParticlePaintContext.Draw>>(BASELINE_FRAMES + 2 + POST_TAP_FRAMES)
        // Phase 1: standard EvolutionGateTest window (k=0..BASELINE_FRAMES at t=k·DT, no tap → touchState=null)
        for (k in 0..BASELINE_FRAMES) out += paintAndRecord(doc, touch = null, t = k * DT)
        // Phase 2: elapse-jump — one paint with t > startAt(=0)+duration. The elapse-branch in runImpulse
        // returns before any body paint → no captured draws this frame (impulse silent) and NO RAND consumed.
        out += paintAndRecord(doc, touch = null, t = spec.elapseT)
        // Phase 3: tap injection — `touch.down(...)` BEFORE paint → dispatchTouch consumes DOWN → ts.touchEventTime
        // = tap_t → id29 reloaded → impulse re-enters [tap_t, tap_t+duration] → isInitialPass=true →
        // ParticlesCreate.apply re-seeds (consumes init RAND from CURRENT RNG state).
        val touch = TouchState().also { it.down(150f, 150f) }
        out += paintAndRecord(doc, touch = touch, t = spec.tapT)
        // Phase 4: post-tap evolution at standard DT — touchState.phase advanced to DRAG, no new DOWN.
        for (t in spec.postTapTimes(POST_TAP_FRAMES)) out += paintAndRecord(doc, touch = touch, t = t)
        return out
    }

    private fun paintAndRecord(doc: RemoteComposeDocument, touch: TouchState?, t: Float): List<RecordingParticlePaintContext.Draw> {
        val ctx = RemoteContext().also { it.setDensity(1f); it.animationEnabled = true; it.seedHostPalette() }
        val rec = RecordingParticlePaintContext(ctx)
        RemoteComposePlayer(ctx).paint(doc, rec, frameTimeSeconds = t, touchState = touch)
        return rec.draws.toList()
    }

    /** Recon-side mirror: env-eval once + one pin + Phase 1 evolve + skip elapse + re-seed at tap + Phase 4. */
    private fun reconExpected(
        doc: RemoteComposeDocument, systems: List<ParticleSystemDecoder.System>, spec: DocSchedule, docW: Float, docH: Float,
    ): List<List<Array<FloatArray>>> {
        val reconCtx = RemoteContext().also { it.setDensity(1f); it.animationEnabled = true; it.seedHostPalette() }
        reconCtx.seedSystemVariables(docW, docH, 0f, 1f)
        // env-eval pass at startAt — id43/id58/canvas-dims populated; env-RAND discarded by the re-pin below.
        RemoteComposePlayer(reconCtx).paint(doc, NoOpPaintContext(reconCtx), frameTimeSeconds = 0f)
        RpnFloatEvaluator.seedRngForCapture(RenderRngPins.PARTICLE_SEED)             // pin AFTER env-eval
        val framesPerSystem: List<MutableList<Array<FloatArray>>> = systems.map { mutableListOf() }
        // Phase 1: seed @ k=0, step @ k=1..BASELINE_FRAMES
        reconCtx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, 0f)
        for (system in systems) system.reconstruction.seedAllParticles(reconCtx)
        systems.forEachIndexed { si, system -> framesPerSystem[si].add(system.reconstruction.snapshotState()) }
        for (k in 1..BASELINE_FRAMES) {
            reconCtx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, DT)
            for (system in systems) system.reconstruction.stepFrame(reconCtx)
            systems.forEachIndexed { si, system -> framesPerSystem[si].add(system.reconstruction.snapshotState()) }
        }
        // Phase 2: elapse-jump — sim consumed no RAND → recon does nothing. (We still emit a frame snapshot
        // for index-alignment with sim-capture, but those positions are unused — Phase 2 sim drew no anchors.)
        systems.forEachIndexed { si, system -> framesPerSystem[si].add(system.reconstruction.snapshotState()) }
        // Phase 3: re-seed (sim's ParticlesCreate.apply re-runs on tap-frame, consuming init RAND from
        // CURRENT continuous RNG — same byte-order as recon's seedAllParticles now). The sim's `paint()`
        // re-seeds system vars EVERY live frame from `frameTimeSeconds` (TIME_IN_SEC/CONTINUOUS_SEC change
        // per frame), and an init eq that references them (e.g. hearts) needs the SAME time-seed on recon
        // → re-call seedSystemVariables at tap_t before the recon re-seed (mirrors sim's per-frame seed).
        reconCtx.seedSystemVariables(docW, docH, spec.tapT, 1f)
        reconCtx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, 0f)
        for (system in systems) system.reconstruction.seedAllParticles(reconCtx)
        systems.forEachIndexed { si, system -> framesPerSystem[si].add(system.reconstruction.snapshotState()) }
        // Phase 4: standard evolution from new seed; re-seed time-vars per frame so update eqs see the same
        // TIME_IN_SEC/CONTINUOUS_SEC as the sim does (defensive — EvolutionGateTest works without this, but
        // S3 Re-Trigger jumps time non-monotonically so any time-var ref will already differ from Phase-1).
        for (k in 1..POST_TAP_FRAMES) {
            val tk = spec.tapT + k * DT
            reconCtx.seedSystemVariables(docW, docH, tk, 1f)
            reconCtx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, DT)
            for (system in systems) system.reconstruction.stepFrame(reconCtx)
            systems.forEachIndexed { si, system -> framesPerSystem[si].add(system.reconstruction.snapshotState()) }
        }
        return framesPerSystem
    }

    /** Replay the recon state through the body ops once (mirroring `Rem143EvolutionGateTest.expectedAnchors`)
     *  to convert per-var state into rendered anchor positions. */
    private fun expectedAnchorsForFrame(
        doc: RemoteComposeDocument, system: ParticleSystemDecoder.System, frame: Array<FloatArray>, dt: Float,
        timeSeconds: Float,
    ): List<RecordingParticlePaintContext.Draw> {
        val docW = doc.width.toFloat(); val docH = doc.height.toFloat()
        val ctx = RemoteContext().also { it.setDensity(1f); it.animationEnabled = true; it.seedHostPalette() }
        // Mirror the sim's per-frame system-var re-seed (paint() does this every live frame from
        // frameTimeSeconds — hearts/confetti body draws may reference TIME_IN_SEC for animation).
        ctx.seedSystemVariables(docW, docH, timeSeconds, 1f)
        ctx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, dt)
        for (op in doc.operations) if (op is VariableSupport) { op.updateVariables(ctx); op.apply(ctx) }
        val anchors = mutableListOf<RecordingParticlePaintContext.Draw>()
        for (p in 0 until system.create.particleCount) {
            for (v in system.create.varIds.indices) ctx.loadFloat(system.create.varIds[v], frame[p][v])
            val rec = RecordingParticlePaintContext(ctx)
            for (op in system.body) {
                if (op is VariableSupport) { op.updateVariables(ctx); op.apply(ctx) }
                if (op is PaintOperation) op.paint(ctx, rec)
            }
            rec.draws.firstOrNull()?.let { anchors += it }
        }
        return anchors
    }

    /** Δt for the k-th Phase-3+4 frame in the capture vs recon: k=0 is the re-burst (Δt=0), k≥1 is DT. */
    private fun dtForFrame(k: Int): Float = if (k == 0) 0f else DT

    /** Every expected anchor must be ε-matched by some captured anchor (mirrors EvolutionGateTest helper). */
    private fun unmatched(expected: List<RecordingParticlePaintContext.Draw>, captured: List<RecordingParticlePaintContext.Draw>, tol: Float): Int =
        expected.count { e -> captured.none { c -> abs(c.cx - e.cx) <= tol && abs(c.cy - e.cy) <= tol } }
}
