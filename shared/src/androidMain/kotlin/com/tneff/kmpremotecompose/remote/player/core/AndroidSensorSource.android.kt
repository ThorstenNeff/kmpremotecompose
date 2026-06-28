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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * REM-101 (D5) S2 — the Android [SensorSource], backed by [SensorManager]. Constructed by the app entry
 * (`MainActivity`) with the application [Context] (the App-Shell-injection decision, TechSpec §5) and
 * passed into `RemoteComposeApp(sensorSource = …)`. `commonMain` stays platform-free.
 *
 * [start] registers **only** the sensors the live document actually reads (`neededIds` from
 * `RemoteComposePlayer.sensorIdsUsed`), mapping each reserved id-triple to one Android sensor type. An
 * absent sensor (`getDefaultSensor == null`) is simply not registered → its ids never get a reading →
 * [read] returns `null` → the player keeps them at the static `0f` default (the per-axis capability-floor,
 * never a hard fail). Values are **raw physical units** (m/s², rad/s, µT, lux) — no density scaling.
 *
 * Threading: `SensorManager` delivers callbacks on the main thread (no Handler given) and `read` is called
 * from the Compose render on the main thread, so the plain map is single-threaded; `@Volatile` guards
 * publication in case a host registers a background Handler later.
 */
class AndroidSensorSource(context: Context) : SensorSource {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @Volatile private var values: Map<Int, Float> = emptyMap()
    private val mutable = HashMap<Int, Float>()
    private var registered = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> put3(RemoteContext.ID_ACCELERATION_X, event)
                Sensor.TYPE_GYROSCOPE -> put3(RemoteContext.ID_GYRO_ROT_X, event)
                Sensor.TYPE_MAGNETIC_FIELD -> put3(RemoteContext.ID_MAGNETIC_X, event)
                Sensor.TYPE_LIGHT -> { mutable[RemoteContext.ID_LIGHT] = event.values[0]; publish() }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun put3(baseId: Int, event: SensorEvent) {
        mutable[baseId] = event.values[0]
        mutable[baseId + 1] = event.values[1]
        mutable[baseId + 2] = event.values[2]
        publish()
    }

    private fun publish() { values = HashMap(mutable) }

    override fun read(sensorId: Int): Float? = values[sensorId]

    override fun start(neededIds: Set<Int>) {
        val mgr = sensorManager ?: return
        if (registered) stop()
        // One Android sensor type per reserved-id triple; register only types the doc uses AND the device has.
        val types = buildSet {
            if (neededIds.any { it in RemoteContext.ID_ACCELERATION_X..RemoteContext.ID_ACCELERATION_Z }) add(Sensor.TYPE_ACCELEROMETER)
            if (neededIds.any { it in RemoteContext.ID_GYRO_ROT_X..RemoteContext.ID_GYRO_ROT_Z }) add(Sensor.TYPE_GYROSCOPE)
            if (neededIds.any { it in RemoteContext.ID_MAGNETIC_X..RemoteContext.ID_MAGNETIC_Z }) add(Sensor.TYPE_MAGNETIC_FIELD)
            if (RemoteContext.ID_LIGHT in neededIds) add(Sensor.TYPE_LIGHT)
        }
        for (type in types) {
            val sensor = mgr.getDefaultSensor(type) ?: continue // absent hardware → ids stay null → static
            mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        registered = true
    }

    override fun stop() {
        sensorManager?.unregisterListener(listener)
        registered = false
        mutable.clear()
        values = emptyMap() // fresh start begins from "no reading" (null → static) until the first event
    }
}
