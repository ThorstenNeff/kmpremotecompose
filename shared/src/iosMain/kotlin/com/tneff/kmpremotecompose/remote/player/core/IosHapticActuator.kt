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

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * REM-143 (S3b) — the iOS [HapticActuator], backed by UIKit's [UIImpactFeedbackGenerator] (the analog of
 * [IosSensorSource]). Constructed by the iOS entry (`MainViewController`) and passed into
 * `RemoteComposeApp(hapticActuator = …)`; `commonMain` stays platform-free.
 *
 * Capability-floor: a short impact pulse on each (re-)trigger. The player calls [perform] from the render
 * (main thread on iOS Compose), where UIKit feedback generators must run. `NO_HAPTICS` (type 0) is a no-op.
 *
 * **Fidelity note (flagged refinement):** upstream maps the doc's `hapticFeedbackType` to Android
 * `HapticFeedbackConstants`; iOS has only the coarse impact/notification/selection generators, so this maps
 * every (non-zero) type to a medium impact — the iOS-native capability-floor. A finer type→generator map is
 * a possible later refinement.
 */
class IosHapticActuator : HapticActuator {

    override fun perform(hapticFeedbackType: Int) {
        if (hapticFeedbackType == TYPE_NO_HAPTICS) return
        val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
        generator.prepare()
        generator.impactOccurred()
    }

    private companion object {
        const val TYPE_NO_HAPTICS = 0
    }
}
