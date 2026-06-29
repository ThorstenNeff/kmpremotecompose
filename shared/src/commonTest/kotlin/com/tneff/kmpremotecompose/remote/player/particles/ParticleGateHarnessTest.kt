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

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.player.core.PaintContext
import com.tneff.kmpremotecompose.remote.player.core.PaintOperation
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-143 — driver mechanics ([ParticleGateHarness.captureFrames] decode-once→paint-N + per-frame
 * draw-capture) and the compare. Uses a fake draw op so the mechanics are validated independent of the S2
 * sim (full sim-vs-reconstruction integration runs once S2 lands). The fake draws a fixed circle, so every
 * frame captures the same draw — proving N+1 frames are painted on the SAME doc with per-frame capture.
 */
class ParticleGateHarnessTest {

    /**
     * A minimal fake particle body: matrix-translate to (px,py) then draw a LOCAL circle — mirrors the
     * real corpus bodies (MATRIX_TRANSLATE placement + local shape), so the matrix-aware capture must
     * record (px,py) as the anchor, NOT the local draw coords.
     */
    private class FakeBodyOp(val px: Float, val py: Float) : PaintOperation {
        override val opcode: Int get() = -1
        override fun write(buffer: WireBuffer) {}
        override fun dump(): String = "FAKE_BODY($px,$py)"
        override fun paint(context: RemoteContext, paint: PaintContext) {
            paint.matrixSave()
            paint.matrixTranslate(px, py)
            paint.drawCircle(0f, 0f, 5f) // local origin — anchor comes from the matrix
            paint.matrixRestore()
        }
    }

    @Test fun captureFrames_paintsNPlus1Frames_capturingMatrixAnchorPerFrame() {
        val doc = RemoteComposeDocument(listOf(FakeBodyOp(10f, 20f), FakeBodyOp(30f, 40f)))
        val schedule = ParticleFrameSchedule.of(startAtRaw = 0f, durationRaw = 0.1f) // small N
        val frames = ParticleGateHarness.captureFrames(doc, schedule)

        assertEquals(schedule.times.size, frames.size, "one capture per scheduled frame (N+1, incl. seed)")
        for ((k, f) in frames.withIndex()) {
            assertEquals(2, f.size, "frame $k captured both bodies")
            // anchor = the matrix translate (NOT the local (0,0) draw coords), and save/restore isolates them.
            assertEquals(RecordingParticlePaintContext.Draw(10f, 20f), f[0])
            assertEquals(RecordingParticlePaintContext.Draw(30f, 40f), f[1])
        }
    }

    @Test fun compare_matchWithinTolerance_yieldsNoMismatch() {
        val captured = listOf(
            listOf(RecordingParticlePaintContext.Draw(10f, 20f), RecordingParticlePaintContext.Draw(30f, 40f)),
        )
        val expected = listOf(listOf(10.2f to 20.1f, 29.8f to 40.3f)) // within 0.5 tol
        assertTrue(ParticleGateHarness.compare(captured, expected, tolerance = 0.5f).isEmpty())
    }

    @Test fun compare_divergenceBeyondTolerance_isReported() {
        val captured = listOf(listOf(RecordingParticlePaintContext.Draw(10f, 20f)))
        val expected = listOf(listOf(50f to 20f)) // cx off by 40
        val mm = ParticleGateHarness.compare(captured, expected, tolerance = 0.5f)
        assertEquals(1, mm.size)
        assertEquals(0, mm[0].frame)
    }

    @Test fun compare_frameCountMismatch_isReported() {
        val mm = ParticleGateHarness.compare(emptyList(), listOf(listOf(1f to 1f)))
        assertTrue(mm.any { it.frame == -1 }, "frame-count mismatch flagged")
    }
}
