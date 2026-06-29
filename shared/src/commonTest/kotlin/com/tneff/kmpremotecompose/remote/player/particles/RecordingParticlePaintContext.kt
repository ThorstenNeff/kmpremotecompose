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

import com.tneff.kmpremotecompose.remote.player.NoOpPaintContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext

/**
 * REM-143 §5b #5 — the black-box **draw-capture** PaintContext: records each particle body's draw position
 * (`drawCircle` centre, or `drawBitmap` dst centre) without rendering pixels. This is what the gate
 * compares against the independent reconstruction — it tests what the sim ACTUALLY draws (output), NOT a
 * white-box `op.mParticles` readback (which would share the sim path → false-green). A fresh instance is
 * used per frame (the driver creates one per paint), so [draws] holds exactly that frame's body draws.
 */
class RecordingParticlePaintContext(context: RemoteContext) : NoOpPaintContext(context) {

    /** A captured body draw: the resolved centre the sim drew this particle at. */
    data class Draw(val cx: Float, val cy: Float)

    val draws: MutableList<Draw> = mutableListOf()

    override fun drawCircle(centerX: Float, centerY: Float, radius: Float) {
        draws += Draw(centerX, centerY)
    }

    override fun drawBitmap(id: Int, left: Float, top: Float, right: Float, bottom: Float) {
        draws += Draw((left + right) / 2f, (top + bottom) / 2f)
    }

    override fun drawBitmap(
        imageId: Int,
        srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int,
        dstLeft: Int, dstTop: Int, dstRight: Int, dstBottom: Int,
        cdId: Int,
    ) {
        draws += Draw((dstLeft + dstRight) / 2f, (dstTop + dstBottom) / 2f)
    }
}
