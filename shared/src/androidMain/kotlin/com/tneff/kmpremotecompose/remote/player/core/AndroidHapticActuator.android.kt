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

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * REM-151 (S3b fidelity) — the Android [HapticActuator], backed by [View.performHapticFeedback] with the
 * **exact upstream `HapticSupport` mapping**: the doc's `hapticFeedbackType` indexes a 21-entry
 * [HapticFeedbackConstants] table (`type % length`, upstream-faithful wrap), and the resolved constant is
 * performed on the host [View]. Constructed by the app entry (`MainActivity`) with the Compose root view
 * (`LocalView.current`) and passed into `RemoteComposeApp(hapticActuator = …)`.
 *
 * Replaces the original S3b `Vibrator`/`EFFECT_CLICK` capability-floor with full upstream fidelity:
 * - **No VIBRATE permission** — `performHapticFeedback` routes through the system haptics service and
 *   honours the user's touch-haptics setting (a clean no-op if haptics are off).
 * - **No androidx dependency** — uses the framework [HapticFeedbackConstants] directly. The table compiles
 *   against `compileSdk 36` (all constants exist); on the project's `minSdk 24` the newer constants are
 *   inlined `int` values (no runtime field access → no `NewApi` crash), and `performHapticFeedback` returns
 *   `false` (no-op) for any constant the running platform doesn't support — graceful per-API degradation
 *   (the capability-floor), so explicit `Build.VERSION` guards are unnecessary. (Upstream's
 *   `HapticFeedbackConstantsCompat` does the same fallback; we replicate it without the extra dep.)
 *
 * `NO_HAPTICS` (table index 0) performs no effect. Render-invariant; no serialized bytes (§2 safe).
 */
class AndroidHapticActuator(private val view: View) : HapticActuator {

    override fun perform(hapticFeedbackType: Int) {
        val index = ((hapticFeedbackType % HAPTIC_TABLE.size) + HAPTIC_TABLE.size) % HAPTIC_TABLE.size
        view.performHapticFeedback(HAPTIC_TABLE[index])
    }

    @Suppress("NewApi") // constants are inlined ints; performHapticFeedback no-ops on unsupported (see kdoc)
    private companion object {
        /**
         * Upstream `HapticSupport.sHapticTable` (verbatim order) — the doc's `hapticFeedbackType` indexes
         * this. Newer constants (API 27/30/34) degrade to a no-op on older devices via performHapticFeedback.
         */
        val HAPTIC_TABLE = intArrayOf(
            HapticFeedbackConstants.NO_HAPTICS,
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.CONTEXT_CLICK,
            HapticFeedbackConstants.KEYBOARD_PRESS,
            HapticFeedbackConstants.KEYBOARD_RELEASE,
            HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,
            HapticFeedbackConstants.TEXT_HANDLE_MOVE,
            HapticFeedbackConstants.GESTURE_START,
            HapticFeedbackConstants.GESTURE_END,
            HapticFeedbackConstants.CONFIRM,
            HapticFeedbackConstants.REJECT,
            HapticFeedbackConstants.TOGGLE_ON,
            HapticFeedbackConstants.TOGGLE_OFF,
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE,
            HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE,
            HapticFeedbackConstants.DRAG_START,
            HapticFeedbackConstants.SEGMENT_TICK,
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK,
        )
    }
}
