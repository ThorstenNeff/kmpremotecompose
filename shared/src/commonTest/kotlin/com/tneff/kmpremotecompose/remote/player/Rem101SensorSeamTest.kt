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
import kotlin.test.assertNotEquals
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

    /**
     * Records the geometry + matrix draw stream so two renders can be compared for byte-for-byte identity.
     * Sensor values drive coordinates / rotations / translations, so an identical stream proves the sensor
     * input had no effect on the render (the static==baseline conformance pin).
     */
    private class RecordingDrawPaintContext(context: RemoteContext) : NoOpPaintContext(context) {
        val log = mutableListOf<String>()
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float) { log += "rect($left,$top,$right,$bottom)" }
        override fun drawCircle(centerX: Float, centerY: Float, radius: Float) { log += "circle($centerX,$centerY,$radius)" }
        override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) { log += "line($x1,$y1,$x2,$y2)" }
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float) { log += "oval($left,$top,$right,$bottom)" }
        override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) { log += "arc($left,$top,$right,$bottom,$startAngle,$sweepAngle)" }
        override fun drawSector(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) { log += "sector($left,$top,$right,$bottom,$startAngle,$sweepAngle)" }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float) { log += "roundRect($left,$top,$right,$bottom,$radiusX,$radiusY)" }
        override fun drawPath(id: Int, start: Float, end: Float) { log += "path($id,$start,$end)" }
        override fun drawTweenPath(path1Id: Int, path2Id: Int, tween: Float, start: Float, end: Float) { log += "tweenPath($path1Id,$path2Id,$tween,$start,$end)" }
        override fun drawTextRun(textId: Int, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, rtl: Boolean) { log += "text($textId,$x,$y)" }
        override fun matrixTranslate(translateX: Float, translateY: Float) { log += "mTranslate($translateX,$translateY)" }
        override fun matrixRotate(rotate: Float, pivotX: Float, pivotY: Float) { log += "mRotate($rotate,$pivotX,$pivotY)" }
        override fun matrixScale(scaleX: Float, scaleY: Float, centerX: Float, centerY: Float) { log += "mScale($scaleX,$scaleY,$centerX,$centerY)" }
        override fun matrixSkew(skewX: Float, skewY: Float) { log += "mSkew($skewX,$skewY)" }
    }

    private fun drawStream(name: String, live: Boolean, source: SensorSource): List<String> {
        val ctx = RemoteContext()
        ctx.animationEnabled = live
        val rec = RecordingDrawPaintContext(ctx)
        RemoteComposePlayer(ctx).paint(doc(name), rec, frameTimeSeconds = if (live) 1f else 0f, sensorSource = source)
        return rec.log
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

    @Test fun staticRender_identicalRegardlessOfSource_conformancePin() {
        // assist TechSpec addition (a): the design-(F) guarantee at RENDER level — a sensor doc in static
        // mode draws an identical geometry/matrix stream whether the source offers live values or nothing.
        // This pins that goldens / the REM-78 desktop sweep / the 173-doc conformance corpus are immune to
        // sensors (they only ever run static). Sensor values drive coords/rotations, so an identical stream
        // is the byte-for-byte determinism proof.
        val liveValued = FakeSensorSource(
            mapOf(17 to 7f, 18 to -3f, 19 to 9.8f, 20 to 1f, 21 to -1f, 22 to 0.5f, 23 to 40f, 24 to -10f, 25 to 5f, 26 to 500f),
        )
        for (name in listOf(
            "sensor_demo_acc_sensor1.rc", "sensor_demo_gyro_sensor1.rc", "sensor_demo_mag_sensor1.rc",
            "sensor_demo_compass.rc", "sensor_demo_light_sensor1.rc",
        )) {
            val baseline = drawStream(name, live = false, source = NoOpSensorSource)
            val withLiveSource = drawStream(name, live = false, source = liveValued)
            assertTrue(baseline.isNotEmpty(), "$name should draw something (guard vs vacuous equality)")
            assertEquals(baseline, withLiveSource, "static render of $name must be identical regardless of sensor source")
        }
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

    @Test fun liveMode_valueChangeBetweenFrames_changesRender() {
        // assist TechSpec addition (b) — live re-eval: the player re-evaluates Phase A on every paint(), so
        // a new sensor value on the next live frame changes the render (no dirty-tracking needed; the
        // RemoteComposeApp live loop drives a paint per frame). Proven via the geometry/matrix draw stream.
        val ctx = RemoteContext()
        ctx.animationEnabled = true
        val d = doc("sensor_demo_acc_sensor1.rc")
        fun frame(v: Float): List<String> {
            val rec = RecordingDrawPaintContext(ctx)
            RemoteComposePlayer(ctx).paint(
                d, rec, frameTimeSeconds = 1f,
                sensorSource = FakeSensorSource(mapOf(17 to v, 18 to v, 19 to v)),
            )
            return rec.log
        }
        val flat = frame(0f)
        val tilted = frame(8f)
        assertTrue(flat.isNotEmpty(), "acc demo should draw something")
        assertNotEquals(flat, tilted, "a changed accel value must change the live render (per-frame re-eval)")
    }

    @Test fun noOpSource_default_seedsNothing() {
        // The S1 default everywhere → behaviour-identical to pre-REM-101 (no id seeded, even live).
        val ctx = render(doc("sensor_demo_acc_sensor1.rc"), live = true, source = NoOpSensorSource)
        assertEquals(0f, ctx.getFloat(17))
        assertEquals(0f, ctx.getFloat(26))
    }
}
