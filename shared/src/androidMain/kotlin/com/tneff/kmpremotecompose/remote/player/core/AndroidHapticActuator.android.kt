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

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * REM-143 (S3b) — the Android [HapticActuator], backed by the platform [Vibrator] (the analog of
 * [AndroidSensorSource]). Constructed by the app entry (`MainActivity`) with the application [Context] and
 * passed into `RemoteComposeApp(hapticActuator = …)`; `commonMain` stays platform-free.
 *
 * Capability-floor: if the device has no vibrator, [perform] is a clean no-op (never a crash). Requires the
 * `android.permission.VIBRATE` permission in the app manifest.
 *
 * **Fidelity note (flagged refinement, not capability-floor):** upstream `HapticSupport` maps the doc's
 * `hapticFeedbackType` to a `View.performHapticFeedback(HapticFeedbackConstants.*)` constant (which needs a
 * `View` and honours system haptic settings). This Context-injected `Vibrator` path delivers a short pulse
 * (a click effect where the platform supports predefined effects, else a one-shot) — correct at the
 * capability-floor ("a haptic pulse on trigger"), with the exact-constant fidelity deferred to a possible
 * later `View`-based path. `NO_HAPTICS` (type 0) is honoured as a no-op.
 */
class AndroidHapticActuator(context: Context) : HapticActuator {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun perform(hapticFeedbackType: Int) {
        if (hapticFeedbackType == TYPE_NO_HAPTICS) return // upstream HapticFeedbackConstants.NO_HAPTICS
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                v.vibrate(VibrationEffect.createOneShot(PULSE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> @Suppress("DEPRECATION") v.vibrate(PULSE_MS)
        }
    }

    private companion object {
        const val TYPE_NO_HAPTICS = 0
        const val PULSE_MS = 20L
    }
}
