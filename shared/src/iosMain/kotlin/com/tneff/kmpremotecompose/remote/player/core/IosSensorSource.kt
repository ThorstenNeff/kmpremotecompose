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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager

/**
 * REM-101 (D5) S3 — the iOS [SensorSource], backed by CoreMotion's [CMMotionManager]. Constructed by the
 * iOS entry (`MainViewController`) and passed into `RemoteComposeApp(sensorSource = …)` (App-Shell
 * injection, TechSpec §5). Uses CoreMotion's **pull** model (start updates, then read the latest sample
 * each frame) — a perfect fit for the per-frame `read()` seam, no queue/handler/threading.
 *
 * Coverage (TechSpec §3): accelerometer 17–19, gyroscope 20–22, magnetometer 23–25. **Ambient light (26)
 * has no public iOS API → `read` returns null → static** (per-axis capability-floor). A sensor the device
 * lacks (`…Available == false`) is never started → null → static, never a hard fail.
 *
 * **Units (TechSpec §3c, raw physical):** gyro is rad/s and magnetometer µT — both match the upstream
 * (Android) convention directly. CoreMotion's accelerometer is in **G**, so it is scaled to m/s² by
 * [G_TO_MS2] to match upstream `TYPE_ACCELEROMETER` (m/s²).
 *
 * **⚠️ Parity watchpoint (test-1 device validation):** iOS raw accelerometer uses the **opposite sign**
 * convention to Android (at rest, iOS z ≈ -1G where Android z ≈ +9.81 m/s²). We negate to match Android
 * (the reference the docs were authored against). If test-1's on-device Maestro shows inverted/odd motion
 * on iOS, the fix is a localized per-axis sign tweak HERE — never in the shared seam.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSensorSource : SensorSource {

    private val mgr = CMMotionManager()

    override fun start(neededIds: Set<Int>) {
        if (neededIds.any { it in RemoteContext.ID_ACCELERATION_X..RemoteContext.ID_ACCELERATION_Z } && mgr.accelerometerAvailable) {
            mgr.accelerometerUpdateInterval = UPDATE_INTERVAL
            mgr.startAccelerometerUpdates()
        }
        if (neededIds.any { it in RemoteContext.ID_GYRO_ROT_X..RemoteContext.ID_GYRO_ROT_Z } && mgr.gyroAvailable) {
            mgr.gyroUpdateInterval = UPDATE_INTERVAL
            mgr.startGyroUpdates()
        }
        if (neededIds.any { it in RemoteContext.ID_MAGNETIC_X..RemoteContext.ID_MAGNETIC_Z } && mgr.magnetometerAvailable) {
            mgr.magnetometerUpdateInterval = UPDATE_INTERVAL
            mgr.startMagnetometerUpdates()
        }
        // Ambient light (26): no public iOS API → never started → read() returns null → static.
    }

    override fun read(sensorId: Int): Float? = when (sensorId) {
        // Accelerometer: G → m/s², negated to match Android's sign convention (see parity watchpoint).
        RemoteContext.ID_ACCELERATION_X -> mgr.accelerometerData?.acceleration?.useContents { x }?.let { (-it * G_TO_MS2).toFloat() }
        RemoteContext.ID_ACCELERATION_Y -> mgr.accelerometerData?.acceleration?.useContents { y }?.let { (-it * G_TO_MS2).toFloat() }
        RemoteContext.ID_ACCELERATION_Z -> mgr.accelerometerData?.acceleration?.useContents { z }?.let { (-it * G_TO_MS2).toFloat() }
        // Gyroscope: rad/s, matches upstream directly.
        RemoteContext.ID_GYRO_ROT_X -> mgr.gyroData?.rotationRate?.useContents { x }?.toFloat()
        RemoteContext.ID_GYRO_ROT_Y -> mgr.gyroData?.rotationRate?.useContents { y }?.toFloat()
        RemoteContext.ID_GYRO_ROT_Z -> mgr.gyroData?.rotationRate?.useContents { z }?.toFloat()
        // Magnetometer: µT, matches upstream directly.
        RemoteContext.ID_MAGNETIC_X -> mgr.magnetometerData?.magneticField?.useContents { x }?.toFloat()
        RemoteContext.ID_MAGNETIC_Y -> mgr.magnetometerData?.magneticField?.useContents { y }?.toFloat()
        RemoteContext.ID_MAGNETIC_Z -> mgr.magnetometerData?.magneticField?.useContents { z }?.toFloat()
        else -> null // light (26) + anything else: unavailable → static
    }

    override fun stop() {
        mgr.stopAccelerometerUpdates()
        mgr.stopGyroUpdates()
        mgr.stopMagnetometerUpdates()
    }

    private companion object {
        const val UPDATE_INTERVAL = 1.0 / 60.0 // seconds (NSTimeInterval) — ~60 Hz for live interactivity
        const val G_TO_MS2 = 9.80665 // standard gravity, CoreMotion G → m/s² (upstream unit)
    }
}
