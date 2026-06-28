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
 * REM-101 (D5) — the **capability-staggered** host sensor seam. A live document reads the reserved
 * sensor float ids ([RemoteContext.SENSOR_ID_RANGE], 17–26) directly inside its float expressions; the
 * player seeds the *current* value of each id the document uses **once per live frame** from a
 * `SensorSource` (the analog of how the frame time is injected — see `RemoteComposePlayer.paint`).
 *
 * **Capability-floor (PROJECT_CONTEXT §0, PO-anchored 2026-06-28):** *available → live, otherwise a
 * static render frame, never a hard fail, per-axis.* [read] returns `null` for any axis this target can't
 * provide; the player then leaves that id at its `0f` default (a static value for that axis) — exactly
 * what upstream does when a sensor is absent. So a target with no sensors at all (desktop) renders a
 * static frame, and a partial target (e.g. iOS has no public ambient-light API) is static for just the
 * missing axis.
 *
 * **Static-mode invariant:** the player consults a `SensorSource` **only when animation is enabled**
 * (live). In static mode the sensor ids stay `0f`, so goldens / the REM-78 desktop sweep / the 173-doc
 * conformance corpus are deterministic and untouched. This seam adds **no** serialized bytes (§2 safe).
 *
 * S1 ships the seam with [NoOpSensorSource] as the default everywhere → behaviour-identical to today (no
 * id is ever seeded). Per-target real providers (Android `SensorManager`, iOS CoreMotion, wasmJs
 * DeviceMotion/Orientation) land in later slices and only swap in a real implementation.
 */
interface SensorSource {

    /**
     * The current value of reserved sensor id [sensorId] (∈ [RemoteContext.SENSOR_ID_RANGE]), or `null`
     * if this target/axis cannot provide it (→ the player keeps the id at its static default). Called once
     * per live frame, per used id; implementations return the latest cached reading (non-blocking).
     */
    fun read(sensorId: Int): Float?

    /**
     * Begin delivering values for exactly [neededIds] (the sensor ids the current document actually reads,
     * from [sensorIdsUsed]). Lets a provider register only the hardware the document needs (battery/perf),
     * mirroring upstream's `copySensorListeners`. Idempotent; safe to call on doc/live change.
     */
    fun start(neededIds: Set<Int>)

    /** Stop delivering values and release any platform listeners (call on leaving live / disposal). */
    fun stop()
}

/**
 * The capability-floor at its extreme: provides nothing. Every [read] is `null`, so every sensor id stays
 * at its static default. This is the S1 default on all targets (behaviour-identical to pre-REM-101) and
 * the permanent provider for a target with no sensors (e.g. headless/server, desktop without input).
 */
object NoOpSensorSource : SensorSource {
    override fun read(sensorId: Int): Float? = null
    override fun start(neededIds: Set<Int>) {}
    override fun stop() {}
}
