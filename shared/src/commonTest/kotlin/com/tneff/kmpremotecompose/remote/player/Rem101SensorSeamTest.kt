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

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Builtins
import com.tneff.kmpremotecompose.remote.player.core.NoOpSensorSource
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.SensorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-101 (D5) **S1 — the sensor seam** (capability-staggered, NoOp-default). Proves, headless, the four
 * load-bearing properties of the design (PO-approved 2026-06-28):
 *  1. the reserved sensor ids match upstream (17–26);
 *  2. [RemoteComposePlayer.sensorIdsUsed] correctly reports which sensor vars each corpus doc reads;
 *  3. **static mode ignores the SensorSource** (the invariant that keeps goldens/conformance deterministic);
 *  4. **live mode seeds exactly the used ids** from the source, leaving unavailable axes (read==null) at
 *     their static 0f default (the per-axis capability-floor) — and the [NoOpSensorSource] default seeds
 *     nothing (behaviour-identical to pre-REM-101).
 *
 * Per-target real providers (Android/iOS/wasmJs) + the live-loop wiring are later slices; this seam is the
 * thing they plug into, validated here with a fake source on the JVM.
 */
class Rem101SensorSeamTest {

    private class FakeSensorSource(private val values: Map<Int, Float?>) : SensorSource {
        val started = LinkedHashSet<Int>()
        var stopped = false
        override fun read(sensorId: Int): Float? = values[sensorId]
        override fun start(neededIds: Set<Int>) { started += neededIds }
        override fun stop() { stopped = true }
    }

    private fun doc(name: String): RemoteComposeDocument {
        Builtins.register()
        return DocumentReader.inflate(RcCorpus.readFixture("corpus/$name"))
    }

    /** Render [d] once and return the context so its float store can be inspected post-paint. */
    private fun render(d: RemoteComposeDocument, live: Boolean, source: SensorSource): RemoteContext {
        val ctx = RemoteContext()
        ctx.animationEnabled = live
        RemoteComposePlayer(ctx).paint(
            d, NoOpPaintContext(ctx),
            frameTimeSeconds = if (live) 1f else 0f,
            sensorSource = source,
        )
        return ctx
    }

    @Test fun reservedSensorIds_matchUpstream() {
        assertEquals(17, RemoteContext.ID_ACCELERATION_X)
        assertEquals(19, RemoteContext.ID_ACCELERATION_Z)
        assertEquals(20, RemoteContext.ID_GYRO_ROT_X)
        assertEquals(25, RemoteContext.ID_MAGNETIC_Z)
        assertEquals(26, RemoteContext.ID_LIGHT)
        assertEquals(17..26, RemoteContext.SENSOR_ID_RANGE)
    }

    @Test fun sensorIdsUsed_matchesCorpusDocs() {
        // Source-grounded against the decoded corpus (REM-101 probe): each demo reads exactly its sensor's
        // axes (compass derives a heading from magnetic X/Y, so it reads ⊇ {23,24}).
        assertEquals(setOf(17, 18, 19), RemoteComposePlayer.sensorIdsUsed(doc("sensor_demo_acc_sensor1.rc")))
        assertEquals(setOf(20, 21, 22), RemoteComposePlayer.sensorIdsUsed(doc("sensor_demo_gyro_sensor1.rc")))
        assertEquals(setOf(23, 24, 25), RemoteComposePlayer.sensorIdsUsed(doc("sensor_demo_mag_sensor1.rc")))
        assertEquals(setOf(26), RemoteComposePlayer.sensorIdsUsed(doc("sensor_demo_light_sensor1.rc")))
        assertTrue(RemoteComposePlayer.sensorIdsUsed(doc("sensor_demo_compass.rc")).containsAll(setOf(23, 24)))
        assertTrue(RemoteComposePlayer.isSensorDriven(doc("sensor_demo_acc_sensor1.rc")))
        // A non-sensor doc reads no sensor ids.
        assertFalse(RemoteComposePlayer.isSensorDriven(doc("procedure_simple1.rc")))
        assertEquals(emptySet(), RemoteComposePlayer.sensorIdsUsed(doc("procedure_simple1.rc")))
    }

    @Test fun staticMode_ignoresSensorSource_keepsZero() {
        // THE invariant (design F): even a source offering live values must NOT seed in static mode → the
        // sensor ids stay 0f → goldens / REM-78 sweep / 173-conformance are deterministic.
        val source = FakeSensorSource(mapOf(17 to 9f, 18 to 9f, 19 to 9f))
        val ctx = render(doc("sensor_demo_acc_sensor1.rc"), live = false, source = source)
        assertEquals(0f, ctx.getFloat(17), "static mode must not seed accel X")
        assertEquals(0f, ctx.getFloat(18))
        assertEquals(0f, ctx.getFloat(19))
    }

    @Test fun liveMode_seedsUsedIdsFromSource() {
        val source = FakeSensorSource(mapOf(17 to 1.5f, 18 to -2.5f, 19 to 9.81f))
        val ctx = render(doc("sensor_demo_acc_sensor1.rc"), live = true, source = source)
        assertEquals(1.5f, ctx.getFloat(17), "live mode seeds accel X from the source")
        assertEquals(-2.5f, ctx.getFloat(18))
        assertEquals(9.81f, ctx.getFloat(19))
    }

    @Test fun liveMode_nullAxis_staysAtStaticDefault() {
        // Per-axis capability-floor: an axis the source can't provide (null) is left at 0f, not failed.
        val source = FakeSensorSource(mapOf(17 to 4f, 18 to null, 19 to 6f))
        val ctx = render(doc("sensor_demo_acc_sensor1.rc"), live = true, source = source)
        assertEquals(4f, ctx.getFloat(17))
        assertEquals(0f, ctx.getFloat(18), "unavailable axis (read==null) ⇒ static 0f default")
        assertEquals(6f, ctx.getFloat(19))
    }

    @Test fun noOpSource_default_seedsNothing() {
        // The S1 default everywhere → behaviour-identical to pre-REM-101 (no id seeded, even live).
        val ctx = render(doc("sensor_demo_acc_sensor1.rc"), live = true, source = NoOpSensorSource)
        assertEquals(0f, ctx.getFloat(17))
        assertEquals(0f, ctx.getFloat(26))
    }
}
