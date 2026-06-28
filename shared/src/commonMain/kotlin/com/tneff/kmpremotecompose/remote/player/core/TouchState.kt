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

/** Lifecycle phase of the live pointer gesture (REM-108 S2b). */
enum class TouchPhase { IDLE, DOWN, DRAG, UP, CANCEL }

/**
 * REM-108 (Epic-F) S2b — the **persistent** pointer-gesture holder that bridges the per-frame-fresh
 * [RemoteContext]. The CMP `pointerInput` on the `rc-canvas` (in `RemoteComposeApp`) writes the current
 * doc-space position + [phase] here; the player **consumes one transition per frame** in
 * [RemoteComposePlayer.paint] (DOWN→dispatch touchDown then DRAG; DRAG→touchDrag each frame; UP→touchUp
 * then IDLE; CANCEL→touchCancel then IDLE). Because this object is `remember`ed (and the stateful
 * `TouchExpression` instances live in the remembered document), the gesture state survives even though a
 * brand-new `RemoteContext` is built for every Canvas draw.
 *
 * Single-threaded (Compose UI thread for both the pointer handler and the render) → plain `var`s, no sync.
 * Not Compose state — the live `frameTime` loop already recomposes every frame, so the player re-reads it.
 */
class TouchState {
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    var phase: TouchPhase = TouchPhase.IDLE

    /** Pointer pressed at doc-space ([px], [py]). */
    fun down(px: Float, py: Float) { x = px; y = py; phase = TouchPhase.DOWN }

    /**
     * Pointer moved to doc-space ([px], [py]) while pressed — updates the position only.
     *
     * **REM-108 S2b Fix#2 (on-device):** this must NOT collapse an unconsumed `DOWN` into `DRAG`. On a real
     * device `onDragStart`→`onDrag` both fire before the next frame, so without this a press+drag would reach
     * [RemoteComposePlayer] already in `DRAG` → `dispatchTouch` skips the DOWN branch → `touchDown` never
     * runs → `TouchExpression.touchDrag`'s `if (touchActive)` guard no-ops → the output never moves (the 0%
     * bug). The `DOWN→DRAG` transition is owned solely by `dispatchTouch` (DOWN → touchDown, then DRAG), so
     * the press edge is always consumed first. While `IDLE`/`UP`/`CANCEL` (no active press) a move is ignored.
     */
    fun move(px: Float, py: Float) {
        if (phase == TouchPhase.IDLE || phase == TouchPhase.UP || phase == TouchPhase.CANCEL) return
        x = px; y = py
    }

    /** Pointer released at doc-space ([px], [py]). */
    fun up(px: Float, py: Float) { x = px; y = py; phase = TouchPhase.UP }

    /** Gesture cancelled (e.g. pointer left the surface) — abandon the drag, keep the value. */
    fun cancel() { phase = TouchPhase.CANCEL }
}
