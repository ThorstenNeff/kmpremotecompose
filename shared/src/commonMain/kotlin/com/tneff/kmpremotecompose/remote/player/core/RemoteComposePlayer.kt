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
package com.tneff.kmpremotecompose.remote.player.core

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument

/**
 * The Layer 2 player op-walk skeleton (REM-30, L2-S1): renders a decoded [RemoteComposeDocument] by
 * walking its operations in order and dispatching every [PaintOperation] into a [PaintContext].
 *
 * S1 establishes the walk + dispatch seam only. The geometry adapter (L2-S2, dev-2) and text adapter
 * (L2-S3, dev-1) provide the real [PaintContext] primitives and mark the draw ops as [PaintOperation];
 * the walk itself stays unchanged as that coverage grows. Variable evaluation / animation clocking
 * (the upstream two-phase `apply` then `paint`) are a later milestone — see STATUS `declareId` note.
 */
class RemoteComposePlayer(val context: RemoteContext = RemoteContext()) {

    /**
     * Render [document] into [paint] for the single frame at [frameTimeSeconds].
     *
     * The frame time is **injected**, never hardcoded in the walk (PO 2026-06-26 frame-render ↔
     * time-source seam): the MVP renders one static frame at the default `t = 0`; a continuous
     * animation loop attaches by calling this again with an advancing time — the walk is unchanged.
     * Time-driven variables (`ANIMATED_FLOAT`, …) evaluate against [RemoteContext.frameTimeSeconds]
     * once that evaluation slice lands.
     *
     * Binds [paint] to the [context], resets per-pass state, resets the paint defaults, then walks the
     * ops in order, calling [PaintOperation.paint] for each draw op. Returns the player's requested
     * next-frame delay in seconds ([RemoteContext.wakeInSeconds]; -1 = no repaint requested) so a host
     * render loop can schedule the next pass.
     */
    fun paint(
        document: RemoteComposeDocument,
        paint: PaintContext,
        frameTimeSeconds: Float = 0f,
    ): Float {
        context.paintContext = paint
        context.resetPass(frameTimeSeconds)
        paint.reset()
        // Phase A (REM-36 Eval-Engine E1): resolve + evaluate variables BEFORE painting, so draw ops
        // read already-resolved values (the long-flagged "deferred apply-phase"). MVP evaluates every
        // VariableSupport op each frame (no dirty tracking). updateVariables (resolve NaN refs) then
        // apply (evaluate + load into the store).
        for (op in document.operations) {
            if (op is VariableSupport) {
                op.updateVariables(context)
                op.apply(context)
            }
        }
        // Phase B: paint — draw ops now see resolved coords.
        for (op in document.operations) {
            if (op is PaintOperation) {
                op.paint(context, paint)
            }
        }
        return context.wakeInSeconds
    }
}
