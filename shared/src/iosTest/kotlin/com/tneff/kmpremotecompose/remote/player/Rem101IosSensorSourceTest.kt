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
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.player.core.IosSensorSource
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * REM-101 (D5) S3 — the iOS [IosSensorSource] on the **simulator** (no sensor hardware): proves the
 * capability-floor end of the contract — `start`/`read`/`stop` never crash and every axis reads `null`
 * (→ the player keeps the id at its static 0f default). Real on-device values (CoreMotion delivering
 * accel/gyro/mag) are test-1's Maestro gate; this is the JVM-of-iOS no-crash + null-floor proof.
 */
class Rem101IosSensorSourceTest {

    @Test fun simulatorHasNoSensors_everyAxisNull_noCrash() {
        val source = IosSensorSource()
        source.start(RemoteContext.SENSOR_ID_RANGE.toSet())
        for (id in RemoteContext.SENSOR_ID_RANGE) {
            assertNull(source.read(id), "simulator has no sensor $id → read must be null (static floor)")
        }
        source.stop()
    }

    @Test fun ambientLight_alwaysNull_noIosApi() {
        val source = IosSensorSource()
        source.start(setOf(RemoteContext.ID_LIGHT))
        assertNull(source.read(RemoteContext.ID_LIGHT), "ambient light (26) has no public iOS API → always null/static")
        source.stop()
    }
}
