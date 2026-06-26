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

import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * An **optional** monotonic time source for the animation loop (REM-36 E-D1). The player itself stays
 * **time-injected** — `RemoteComposePlayer.paint(…, frameTimeSeconds = …)` — so goldens/tests pin a
 * fixed frame (t = 0) deterministically. A host that wants live animation can drive the frame time
 * from its own frame callback (CMP `withFrameNanos`) **or** from this clock: `paint(…, clock.elapsed())`.
 *
 * Common (no `expect`/`actual`): `kotlin.time.TimeSource.Monotonic` is multiplatform.
 */
class FrameClock(private val mark: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()) {

    /** Seconds elapsed since this clock was created. */
    fun elapsed(): Float = mark.elapsedNow().toDouble(DurationUnit.SECONDS).toFloat()
}
