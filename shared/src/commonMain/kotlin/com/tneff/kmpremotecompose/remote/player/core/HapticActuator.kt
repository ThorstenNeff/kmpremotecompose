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

/**
 * REM-143 (S3b) — the **capability-staggered** host haptic seam, the analog of [SensorSource] for the
 * `HAPTIC_FEEDBACK` op. A live document fires a haptic pulse from inside an impulse: upstream calls
 * `RemoteContext.hapticEffect(type)` on the impulse's *initial pass* (the trigger frame). Our player
 * mirrors that — [RemoteComposePlayer.runImpulse] performs the body's `HapticFeedback` ops via
 * [RemoteContext.hapticEffect] exactly once per (re-)trigger (the mInitialPass gate, B2-ratified), which
 * delegates here.
 *
 * **Capability-floor (PROJECT_CONTEXT §0):** haptics is available → fire; otherwise a clean no-op, never a
 * hard fail. Desktop and Web have no haptic hardware → [NoOpHapticActuator] (the default). Android/iOS
 * inject a real provider from their platform entry point (the same injection path as the sensor source).
 *
 * **Static-mode invariant:** the player performs haptics **only when animation is enabled** (live) — never
 * during a static/golden render — so goldens, the desktop sweep and the 173-doc conformance corpus are
 * deterministic and untouched. This seam adds **no** serialized bytes (§2 safe): the `HapticFeedback` op's
 * `write`/`read`/`equals` are unchanged; firing is an additive, render-invariant platform call.
 *
 * [hapticFeedbackType] is the doc's raw int (upstream maps it to platform `HapticFeedbackConstants`); the
 * mapping is the platform actual's concern.
 */
interface HapticActuator {
    /** Perform the haptic effect for the doc's [hapticFeedbackType]. Non-blocking; a no-op if unsupported. */
    fun perform(hapticFeedbackType: Int)
}

/**
 * The capability-floor at its extreme: performs nothing. The default on every target (behaviour-identical
 * to pre-S3b) and the permanent provider for a target with no haptic hardware (desktop/jvm, web/wasmJs).
 */
object NoOpHapticActuator : HapticActuator {
    override fun perform(hapticFeedbackType: Int) {}
}
