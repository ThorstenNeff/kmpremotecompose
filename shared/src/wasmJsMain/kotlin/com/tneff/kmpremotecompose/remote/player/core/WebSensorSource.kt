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

import kotlin.math.PI

/**
 * REM-101 (D5) S4 — the wasmJs [SensorSource], backed by the browser `DeviceMotionEvent`. Constructed by
 * the web entry (`webApp/main.kt`) and passed into `RemoteComposeApp(sensorSource = …)` (App-Shell
 * injection, TechSpec §5). A single passive `devicemotion` listener caches the latest sample into a JS
 * global; [read] pulls it per frame (fits the per-frame seam, no Kotlin/JS callback bridging).
 *
 * Coverage (capability-floor, TechSpec §3/§0):
 *  - **accelerometer 17–19** ← `event.accelerationIncludingGravity` (already m/s², incl. gravity like
 *    Android — matches upstream units directly);
 *  - **gyroscope 20–22** ← `event.rotationRate` (deg/s → rad/s via [DEG_TO_RAD]; axes beta/gamma/alpha →
 *    x/y/z);
 *  - **magnetometer 23–25**: no reliable raw web API (Generic Sensor `Magnetometer` is flag-gated, scarce
 *    support) → `read` null → static;
 *  - **ambient light 26**: `AmbientLightSensor` flag-gated/rare → null → static.
 *
 * **Permission/HTTPS reality:** `devicemotion` needs a secure context; iOS Safari additionally requires
 * `DeviceMotionEvent.requestPermission()` from a user gesture (out of this slice — no gesture in our flow).
 * Chromium (test-2's Maestro/Playwright target) delivers/injects motion without that prompt, so the sweep
 * is addressable. Where motion never arrives, every axis stays `null` → static (never a hard fail).
 *
 * **🩹 wasm read-path fix (the S4 web blocker):** the cached value reached `globalThis.__rcSensor` but never
 * reached the render. Cause: the per-field reader used a Kotlin/Wasm `js("{ … return X; }")` **block body
 * that returns a value** — a form with NO working precedent in this module (every working value-returning
 * `js()` here — `performanceNowMs`, `webLocationSearch` — is a single **expression**; the block-with-return
 * `js()`s all return `Unit`). The readers below are now single-expression, parameterless `js()` (the proven
 * idiom): no block-return and no `String`-param→property-index, removing both interop variables at once.
 * Definitive browser confirmation = test-2's Chromium sweep.
 *
 * **⚠️ Parity watchpoint (test-2):** the `devicemotion` sign/axis convention may differ from the Android
 * reference; if test-2's browser sweep shows inverted/swapped motion, the fix is a localized per-axis tweak
 * HERE, never in the shared seam.
 */
class WebSensorSource : SensorSource {

    override fun start(neededIds: Set<Int>) {
        // accel (17-19) + gyro (20-22) both come from the one devicemotion listener.
        if (neededIds.any { it in RemoteContext.ID_ACCELERATION_X..RemoteContext.ID_GYRO_ROT_Z }) {
            installMotionListener()
        }
        // mag (23-25) + light (26): no reliable raw web API → never wired → read null → static.
    }

    override fun read(sensorId: Int): Float? {
        val v = when (sensorId) {
            RemoteContext.ID_ACCELERATION_X -> rcMotionAx()
            RemoteContext.ID_ACCELERATION_Y -> rcMotionAy()
            RemoteContext.ID_ACCELERATION_Z -> rcMotionAz()
            RemoteContext.ID_GYRO_ROT_X -> rcMotionGx() * DEG_TO_RAD
            RemoteContext.ID_GYRO_ROT_Y -> rcMotionGy() * DEG_TO_RAD
            RemoteContext.ID_GYRO_ROT_Z -> rcMotionGz() * DEG_TO_RAD
            else -> Double.NaN // mag/light/unknown → unavailable → static
        }
        return if (v.isNaN()) null else v.toFloat()
    }

    override fun stop() {
        // The devicemotion listener is a passive, idempotent global; left installed (cheap). The player
        // only read()s in live mode (DisposableEffect stops on leaving live), so stale values are never read.
    }

    private companion object {
        const val DEG_TO_RAD = PI / 180.0 // DeviceMotion rotationRate is deg/s; upstream gyro is rad/s
    }
}

/** Install a one-time passive `devicemotion` listener that caches the latest sample into a JS global. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun installMotionListener(): Unit = js(
    """{
        if (typeof window === 'undefined') return;
        if (globalThis.__rcSensorInstalled) return;
        globalThis.__rcSensorInstalled = true;
        globalThis.__rcSensor = {};
        window.addEventListener('devicemotion', function(e) {
            var a = e.accelerationIncludingGravity;
            if (a) { globalThis.__rcSensor.ax = a.x; globalThis.__rcSensor.ay = a.y; globalThis.__rcSensor.az = a.z; }
            var r = e.rotationRate;
            if (r) { globalThis.__rcSensor.gx = r.beta; globalThis.__rcSensor.gy = r.gamma; globalThis.__rcSensor.gz = r.alpha; }
        });
    }"""
)

// Per-field motion readers. Each is a single-expression, parameterless `js()` (the proven value-returning
// idiom on Kotlin/Wasm in this module) returning the cached number or `NaN` when no sample has arrived.
// `(g && g.X != null)` short-circuits to a falsy value → ternary yields NaN before the listener fires.
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun rcMotionAx(): Double = js("(globalThis.__rcSensor && globalThis.__rcSensor.ax != null) ? globalThis.__rcSensor.ax : NaN")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun rcMotionAy(): Double = js("(globalThis.__rcSensor && globalThis.__rcSensor.ay != null) ? globalThis.__rcSensor.ay : NaN")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun rcMotionAz(): Double = js("(globalThis.__rcSensor && globalThis.__rcSensor.az != null) ? globalThis.__rcSensor.az : NaN")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun rcMotionGx(): Double = js("(globalThis.__rcSensor && globalThis.__rcSensor.gx != null) ? globalThis.__rcSensor.gx : NaN")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun rcMotionGy(): Double = js("(globalThis.__rcSensor && globalThis.__rcSensor.gy != null) ? globalThis.__rcSensor.gy : NaN")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun rcMotionGz(): Double = js("(globalThis.__rcSensor && globalThis.__rcSensor.gz != null) ? globalThis.__rcSensor.gz : NaN")
