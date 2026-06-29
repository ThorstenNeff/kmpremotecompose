/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-143 §5b — the LIVE multi-frame EVOLUTION gate: dev-2's S2 sim (full-doc paint, draw-capture) vs the
 * independent per-frame reconstruction. (i) seed-frame matches + (ii) frame-k evolution converges.
 */
class Rem143EvolutionGateTest {

    private val DT = ParticleFrameSchedule.DT

    /** Run a system's body ops with reconstruction vars for frame [k] → the expected per-particle anchors. */
    private fun expectedAnchors(system: ParticleSystemDecoder.System, frames: List<Array<FloatArray>>): List<List<RecordingParticlePaintContext.Draw>> {
        return frames.mapIndexed { k, frame ->
            val anchors = mutableListOf<RecordingParticlePaintContext.Draw>()
            for (p in 0 until system.create.particleCount) {
                val ctx = RemoteContext().also { it.setDensity(1f); it.animationEnabled = true; it.seedHostPalette() }
                ctx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, if (k == 0) 0f else DT)
                for (v in system.create.varIds.indices) ctx.loadFloat(system.create.varIds[v], frame[p][v])
                val rec = RecordingParticlePaintContext(ctx)
                for (op in system.body) {
                    if (op is VariableSupport) { op.updateVariables(ctx); op.apply(ctx) }
                    if (op is PaintOperation) op.paint(ctx, rec)
                }
                anchors += rec.draws
            }
            anchors
        }
    }

    /** Every expected anchor must be ε-matched by some captured anchor in the same frame. */
    private fun unmatched(expected: List<RecordingParticlePaintContext.Draw>, captured: List<RecordingParticlePaintContext.Draw>, tol: Float): Int =
        expected.count { e -> captured.none { c -> abs(c.cx - e.cx) <= tol && abs(c.cy - e.cy) <= tol } }

    /** @return (seedUnmatched, totalUnmatched) — both 0 ⇒ the doc passes (i)+(ii). */
    private fun runDoc(name: String): Pair<Int, Int> {
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/$name"))
        val systems = ParticleSystemDecoder.decode(doc)
        val schedule = ParticleFrameSchedule.fromDoc(doc) ?: return 0 to 0
        val captured = ParticleGateHarness.captureFrames(doc, schedule)
        var totalUnmatched = 0
        var seedUnmatched = 0
        for (system in systems) {
            val frames = system.reconstruction.evolve(schedule.frameCount) { k, ctx ->
                ctx.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, if (k == 0) 0f else DT)
            }
            val expected = expectedAnchors(system, frames)
            for (k in expected.indices) {
                val u = unmatched(expected[k], captured.getOrElse(k) { emptyList() }, tol = 1.0f)
                if (k == 0) seedUnmatched += u
                totalUnmatched += u
            }
        }
        return seedUnmatched to totalUnmatched
    }

    @Test fun allSixParticleDocs_evolutionGate() {
        val docs = listOf(
            "particle.rc", "impulse_demo_confetti_demo.rc", "impulse_demo_hearts_demo.rc",
            "maze.rc", "maze1.rc", "maze2.rc",
        )
        println("===== REM-143 EVOLUTION GATE — all 6 particle docs =====")
        val results = docs.map { d -> d to runDoc(d) }
        for ((d, r) in results) println("  $d: seed=${r.first} evolution=${r.second}")
        println("===== END GATE =====")
        // (i) seed-frame matches + (ii) frame-k evolution converges, for every doc, vs the independent
        // reconstruction (draw-capture, NOT op-readback). Any unmatched anchor ⇒ a sim/spec divergence.
        for ((d, r) in results) {
            assertEquals(0, r.first, "$d: seed-frame must match seed positions (Δt=0 seed property)")
            assertEquals(0, r.second, "$d: frame-k evolution must converge with the independent reconstruction")
        }
    }
}
