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
package com.tneff.kmpremotecompose.creation.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import com.tneff.kmpremotecompose.remote.creation.Profile
import com.tneff.kmpremotecompose.remote.creation.document
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * REM-128 §0 — capture a single `.rc` document by composing [content] into a [RemoteComposeNode]
 * tree (Phase A) and render-walking that tree inside the byte-proven [document] shell (Phase B).
 *
 * **The byte-faithfulness shortcut (TechSpec §0):** [document] owns the prolog / id-42 reservation
 * / profile auto-form selection / `encodeToByteArray()` — REM-128 reuses it **verbatim** and only
 * inserts the render-walk inside the content lambda. The Compose path can therefore differ from
 * the procedural path *only* in render-walk order (= the W2 invariant; checked by §3 Stage-1).
 *
 * **W1 structural guard:** Phase A runs **before** the [document] lambda opens — the doc-buffer
 * doesn't exist yet, so composition cannot write into it. The runtime [currentBufferSize] sanity-
 * check inside the lambda is the assist-mandated belt-and-suspenders (TechSpec §2).
 *
 * Suspending because composition is driven by a [Recomposer] coroutine that runs initial
 * composition and settles to `Recomposer.State.Idle` before we render-walk. The Compose Runtime's
 * machinery (Recomposer / Composition / BroadcastFrameClock) is multiplatform — KMP-portable as
 * long as the §5.9 compile-probe on jvm + iosSimulatorArm64 + wasmJs + androidHostTest stays
 * green.
 */
suspend fun captureSingleRemoteDocument(
    width: Int,
    height: Int,
    profile: Profile = Profile.Baseline,
    contentDescription: String? = null,
    content: @Composable @RemoteComposable () -> Unit,
): ByteArray {
    val root = RemoteRootNode()
    val applier = RemoteComposeApplier(root)

    return coroutineScope {
        // Auto-frame callback: every time the recomposer asks the clock for a frame, the clock
        // immediately schedules a sendFrame back. Without this the recomposer suspends on
        // `withFrameNanos` waiting for a frame that no one sends → hang. Mirrors the upstream
        // CaptureRemoteDocument pattern.
        lateinit var frameClock: BroadcastFrameClock
        frameClock = BroadcastFrameClock { launch { frameClock.sendFrame(0L) } }
        val recomposer = Recomposer(coroutineContext + frameClock)
        val composition = Composition(applier, recomposer)
        val recomposeJob = launch(coroutineContext + frameClock) {
            recomposer.runRecomposeAndApplyChanges()
        }
        try {
            composition.setContent { content() }
            // Recomposer starts in Inactive; setContent kicks it into PendingWork once the
            // launched coroutine begins servicing frames. Waiting for `Idle` before any pending
            // work would match the pre-work state and miss the initial composition entirely — so
            // first wait until the recomposer acknowledges work, then wait for it to drain.
            recomposer.currentState.first {
                it == Recomposer.State.PendingWork ||
                    it == Recomposer.State.InactivePendingWork
            }
            recomposer.currentState.first { it == Recomposer.State.Idle }

            // **Phase B inside the still-alive composition.** `composition.dispose()` calls the
            // applier's `onClear`, which empties `root.children` — so disposal must happen
            // *after* the render-walk, not before. Catches a real bug: with dispose-before-walk
            // the tree was wiped and Phase B emitted zero body ops (the failure mode caught here).
            document(width, height, profile, contentDescription) {
                val sizeAtEntry = writer.currentBufferSize()
                // W1 belt-and-suspenders. Phase A held no context reference (structural guard),
                // so the buffer cannot have changed during composition. This trivially-true
                // assertion documents the invariant — if a future change ever lets a Phase-A
                // composable reach the context, the structural guard breaks first and this
                // turns into an early signal.
                check(writer.currentBufferSize() == sizeAtEntry) {
                    "REM-128 W1: doc-buffer mutated between document{}-entry and render-walk " +
                        "(delta=${writer.currentBufferSize() - sizeAtEntry})"
                }
                root.render(this)
            }
        } finally {
            // cancelAndJoin so finally never hangs: cancel() alone leaves recomposeJob suspended
            // on the frame clock; cancelAndJoin force-unblocks it.
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
            composition.dispose()
        }
    }
}
