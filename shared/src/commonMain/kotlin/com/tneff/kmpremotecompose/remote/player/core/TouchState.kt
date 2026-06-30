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

    /**
     * REM-143 S3a: the frame-time (seconds) of the last touch-down — the impulse `startAt` (= the
     * `ID_TOUCH_EVENT_TIME` system var, id 29) is seeded from this **each live frame** by
     * [RemoteComposePlayer.paint], because the [RemoteContext] is rebuilt per frame (the same persistence
     * pattern as `ImpulseStart.lastFrameTime`). Upstream sets id29 in the platform view's `onTouchEvent`;
     * we hold it here and re-seed. Default **0f** keeps the §0 auto-animation floor: with no touch,
     * id29 = 0 ⇒ a `startAt`=id29 impulse is active from t=0 (the S1/S2 behaviour), rather than upstream's
     * `-Float.MAX_VALUE` (touch-only) default — a deliberate, §0-sanctioned divergence. Set by
     * [RemoteComposePlayer]'s touch-down dispatch, which has the frame time. */
    var touchEventTime: Float = 0f

    /**
     * REM-152: set `true` on the first real touch-down and latched thereafter. Gates the **haptic** fire so
     * it never auto-buzzes at launch — our `id29=0` default starts the impulse at t=0 (the §0 *visual* auto-
     * animation floor), but a haptic pulse at launch with no interaction is an unintended side-effect of that
     * default (upstream uses `id29=-Float.MAX_VALUE` → impulse waits for touch). The player seeds this onto
     * the per-frame [RemoteContext] so [RemoteComposePlayer.runImpulse] fires `HapticFeedback` only once a
     * real touch has triggered the impulse; the visual auto-animation does NOT consult it (floor unchanged).
     * Also fixes the ~25% cold-launch flakiness: the touch-fire is in-window/same-paint (reliable), whereas
     * the t=0 auto-fire raced the view/haptic readiness.
     */
    var hasTouched: Boolean = false

    /** Pointer pressed at doc-space ([px], [py]). */
    fun down(px: Float, py: Float) { x = px; y = py; phase = TouchPhase.DOWN; hasTouched = true }

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
