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
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext.Companion.ID_CALENDAR_MONTH
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext.Companion.ID_DAY_OF_MONTH
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext.Companion.ID_DAY_OF_YEAR
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext.Companion.ID_WEEK_DAY
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext.Companion.ID_YEAR
import com.tneff.kmpremotecompose.remote.player.core.VariableSupport
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-177 — **Daten-Orakel** for `clock_demo2_jclock2.rc`'s 8 sub-city clocks
 * (assist 2026-06-30 found via REM-147 text-value-capture: REM-177 IS render-active —
 * sunrise/sunset shift ~1min per city when ID_DAY_OF_YEAR seeds correctly; geometry hash
 * is identical so a plain PNG diff misses the shift, hence this independent oracle).
 *
 * **Why text/oracle, not image-diff** (REM-123 [[cross-target-third-oracle-required]], same as
 * REM-176):
 *  - The defect would live in shared `commonMain` Phase-A seeding logic — a cross-target self-
 *    compare would render identically-wrong on all 3 targets and pass.
 *  - The doc's per-city sunrise/sunset RPN evaluates a port of upstream's Spencer-style solar
 *    declination + hour-angle formula in our `commonMain` FloatExpression / IntegerExpression
 *    evaluators. The oracle below re-computes the same human-intent quantities (per-city local
 *    sunrise/sunset hour) via the **same standard astronomical formula** in straight Kotlin/JVM
 *    `Double` math — a different code path, so a hypothetical bug in the doc-RPN doesn't share
 *    with the oracle. We assert against the resolved REM-177 calendar seed (the new + critical
 *    surface area) **and** plausibility of the per-city oracle hours.
 *
 * **Pinned epoch:** `EPOCH_SAFE_PIN = 1751529600L` = **2025-07-03 00:00:00 UTC (Thursday)**, day
 * of year 184. Polar-safe at all 8 cities (none above 61.2°N, July sun stays above horizon
 * everywhere). At Anchorage (61.2°N) the day is close to polar-summer max: civil sunrise ~03:30
 * local, sunset ~22:15 local — very different from a pre-fix 1970-Jan-1 render (sunrise ~10:00,
 * sunset ~16:00), which is precisely the surface this test pins.
 *
 * **The 8 cities** (matches upstream `ClockDemo2.Locations[0..7]`, the first 8 entries of the
 * 20-city array the jclock2 demo's `drawTimeZones` loop reads at indices 0..7):
 *  1. Hawaii       (-10 UTC, 21.3°N, -157.9°W)
 *  2. Anchorage    ( -9 UTC, 61.2°N, -149.9°W) — high-latitude polar-summer canary
 *  3. Los_Angeles  ( -8 UTC, 37.8°N, -122.4°W)
 *  4. Denver       ( -7 UTC, 39.7°N, -104.9°W)
 *  5. Mexico       ( -6 UTC, 19.4°N,  -99.1°W)
 *  6. New_York     ( -5 UTC, 40.7°N,  -74.0°W)
 *  7. Caracas      ( -4 UTC, 10.5°N,  -66.9°W)
 *  8. Buenos_Aires ( -3 UTC, -34.6°S, -58.4°W) — Southern-hemisphere winter canary
 */
class Rem177OracleTest {

    companion object {
        /** REM-176/177 pinned epoch — 2025-07-03 00:00:00 UTC (Thursday), day-of-year 184. */
        const val EPOCH_SAFE_PIN: Long = 1751529600L
    }

    private data class City(
        val name: String,
        val tzOffsetHours: Double,
        val lat: Double,
        val lon: Double,
    )

    /** Mirror of upstream `ClockDemo2.Locations[0..7]` — the 8 sub-clocks jclock2 actually renders. */
    private val cities: List<City> = listOf(
        City("Hawaii",        -10.0,  21.3, -157.9),
        City("Anchorage",      -9.0,  61.2, -149.9),
        City("Los_Angeles",    -8.0,  37.8, -122.4),
        City("Denver",         -7.0,  39.7, -104.9),
        City("Mexico",         -6.0,  19.4,  -99.1),
        City("New_York",       -5.0,  40.7,  -74.0),
        City("Caracas",        -4.0,  10.5,  -66.9),
        City("Buenos_Aires",   -3.0, -34.6,  -58.4),
    )

    @Test
    fun clock_demo2_jclock2_oracle_run() {
        // ---- (1) Load + Phase-A apply with the pinned epoch ----------------------------------
        val bytes = RcCorpus.readFixture("corpus/clock_demo2_jclock2.rc")
        val doc = DocumentReader.inflate(bytes)
        val ctx = RemoteContext().also { it.animationEnabled = false }
        ctx.seedSystemVariables(
            windowWidth = 800f,
            windowHeight = 800f,
            timeSeconds = 0f,
            genDensity = 1f,
            epochSeconds = EPOCH_SAFE_PIN,
        )
        for (op in doc.operations) if (op is VariableSupport) {
            op.updateVariables(ctx)
            op.apply(ctx)
        }

        // ---- (2) REM-177 seed anchor: the 5 calendar vars must reflect 2025-07-03 Thursday ----
        // The render-defect was that these were unseeded → 0 → cumulative-day-table look-up
        // returned Jan-1 values → all 8 sub-clocks rendered Jan-1 sunrise/sunset instead of Jul-3.
        // Cross-store (INT + FLOAT) is mandatory: clock_demo2 reads id=34 via FLOAT-NaN-ref.
        assertEquals(184, ctx.getInt(ID_DAY_OF_YEAR),
            "ID_DAY_OF_YEAR (id=$ID_DAY_OF_YEAR) must equal 184 for 2025-07-03 UTC — the doc's per-city sunrise/sunset RPN dereferences this. Pre-fix: 0 → Jan-1 collapse.")
        assertEquals(184.0f, ctx.getFloat(ID_DAY_OF_YEAR),
            "Cross-store FLOAT mirror for id=$ID_DAY_OF_YEAR must match (jclock2 reads via FLOAT-NaN-ref).")
        assertEquals(2025, ctx.getInt(ID_YEAR),
            "ID_YEAR (id=$ID_YEAR) must equal 2025 for EPOCH_SAFE_PIN")
        assertEquals(7, ctx.getInt(ID_CALENDAR_MONTH),
            "ID_CALENDAR_MONTH (id=$ID_CALENDAR_MONTH) must equal 7 (July)")
        assertEquals(3, ctx.getInt(ID_DAY_OF_MONTH),
            "ID_DAY_OF_MONTH (id=$ID_DAY_OF_MONTH) must equal 3")
        assertEquals(4, ctx.getInt(ID_WEEK_DAY),
            "ID_WEEK_DAY (id=$ID_WEEK_DAY) must equal 4 (Thursday, 1=Mon..7=Sun)")

        // ---- (3) Per-city Daten-Orakel: independent NOAA/Spencer sunrise/sunset --------------
        // Computes the local-hour values the doc's RPN SHOULD render. Validates both the math
        // (no NaN, in-plausible range) AND documents the per-city target for test-3's golden
        // capture so a future regression that shifts day_of_year is caught at the value level.
        println("[REM-177-Oracle clock_demo2_jclock2] EPOCH_SAFE_PIN = $EPOCH_SAFE_PIN (2025-07-03 UTC Thu, day_of_year=184)")
        for (city in cities) {
            val oracle = computeSolarOracle(EPOCH_SAFE_PIN, city.lat, city.lon)
            val sunriseLocal = oracle.sunriseHourGMT + city.tzOffsetHours
            val sunsetLocal = oracle.sunsetHourGMT + city.tzOffsetHours
            println("[REM-177-Oracle] ${city.name.padEnd(13)} tz=%+d lat=%+6.2f lon=%+7.2f sunriseUTC=%6.3f sunsetUTC=%6.3f sunriseLOC=%6.3f sunsetLOC=%6.3f"
                .format(city.tzOffsetHours.toInt(), city.lat, city.lon,
                    oracle.sunriseHourGMT, oracle.sunsetHourGMT, sunriseLocal, sunsetLocal))
            assertTrue(oracle.sunriseHourGMT.isFinite() && oracle.sunsetHourGMT.isFinite(),
                "${city.name}: oracle returned non-finite hours (lat/lon out of polar-safe range?)")
            // Plausibility: sunrise = morning, sunset = afternoon/evening.
            // Range is intentionally wide to cover high-latitude summers (Anchorage sunset ~22:15
            // local, sunrise ~03:30 local) and Southern-winter (Buenos Aires sunrise ~08:00).
            // Pre-fix Jan-1-collapse would give Anchorage sunrise ~10:00, sunset ~16:00 — both
            // INSIDE the loose range, so the value-level test-3 golden is the real regression
            // gate. This range catches the gross NaN / wraparound class of bugs.
            assertTrue(sunriseLocal in -2.0..13.0,
                "${city.name}: sunrise local hour out of plausible window: $sunriseLocal")
            assertTrue(sunsetLocal in 11.0..30.0,
                "${city.name}: sunset local hour out of plausible window: $sunsetLocal")
        }
    }

    /**
     * **REM-177 follow-up to test-3's 2026-06-30 broader-impact sweep.** The decode-triage probe
     * (transient `Rem177CalendarVarConsumerProbe`) classified 4 more docs as date-driven beyond
     * `clock_demo2_jclock2`: `clock`, `experimental_gmt`, `experimental_solar_gmt` (which was
     * already epoch-pinned for REM-176, but ALSO reads WEEK_DAY + DAY_OF_MONTH), and `player_info`
     * (system-var debug-dashboard reading 4 of the 5 REM-177 calendar vars directly).
     *
     * This test pins the seed-anchor invariant for **all 4**: load each doc, seed with
     * EPOCH_SAFE_PIN, Phase-A apply, verify the calendar vars they consume actually carry the
     * 2025-07-03 values after the walk (not 0 = the pre-fix Jan-1-collapse). A regression that
     * breaks the seed for any one of them shows up here at the value level — without needing
     * to capture per-doc PNG goldens.
     */
    @Test
    fun additional_date_driven_docs_seed_anchor() {
        val docs = listOf("clock", "experimental_gmt", "experimental_solar_gmt", "player_info")
        for (name in docs) {
            val bytes = RcCorpus.readFixture("corpus/$name.rc")
            val doc = DocumentReader.inflate(bytes)
            val ctx = RemoteContext().also { it.animationEnabled = false }
            ctx.seedSystemVariables(
                windowWidth = 800f,
                windowHeight = 800f,
                timeSeconds = 0f,
                genDensity = 1f,
                epochSeconds = EPOCH_SAFE_PIN,
            )
            for (op in doc.operations) if (op is VariableSupport) {
                op.updateVariables(ctx)
                op.apply(ctx)
            }
            // The 5 REM-177 calendar vars must all carry the 2025-07-03 values regardless of
            // which subset each doc actually reads — the seed is the same for all. Cross-store
            // (INT + FLOAT) because some docs dereference via FLOAT-NaN-ref (e.g. clock_demo2)
            // and others via direct id arg (player_info's TextFromFloat).
            assertEquals(2025, ctx.getInt(ID_YEAR), "$name: ID_YEAR (id=$ID_YEAR) must equal 2025")
            assertEquals(2025.0f, ctx.getFloat(ID_YEAR), "$name: cross-store FLOAT id=$ID_YEAR")
            assertEquals(7, ctx.getInt(ID_CALENDAR_MONTH), "$name: ID_CALENDAR_MONTH (id=$ID_CALENDAR_MONTH) must equal 7 (July)")
            assertEquals(7.0f, ctx.getFloat(ID_CALENDAR_MONTH), "$name: cross-store FLOAT id=$ID_CALENDAR_MONTH")
            assertEquals(3, ctx.getInt(ID_DAY_OF_MONTH), "$name: ID_DAY_OF_MONTH (id=$ID_DAY_OF_MONTH) must equal 3")
            assertEquals(3.0f, ctx.getFloat(ID_DAY_OF_MONTH), "$name: cross-store FLOAT id=$ID_DAY_OF_MONTH")
            assertEquals(184, ctx.getInt(ID_DAY_OF_YEAR), "$name: ID_DAY_OF_YEAR (id=$ID_DAY_OF_YEAR) must equal 184")
            assertEquals(184.0f, ctx.getFloat(ID_DAY_OF_YEAR), "$name: cross-store FLOAT id=$ID_DAY_OF_YEAR")
            assertEquals(4, ctx.getInt(ID_WEEK_DAY), "$name: ID_WEEK_DAY (id=$ID_WEEK_DAY) must equal 4 (Thursday)")
            assertEquals(4.0f, ctx.getFloat(ID_WEEK_DAY), "$name: cross-store FLOAT id=$ID_WEEK_DAY")
            println("[REM-177-Oracle] $name: seed-anchor OK (Y=2025 M=7 D=3 DoY=184 WeekDay=Thu)")
        }
    }

    // ---- ORACLE (independent solar formula, not from the doc-RPN) ------------------------------

    private data class SolarOracle(val sunriseHourGMT: Double, val sunsetHourGMT: Double)

    /**
     * NOAA-style sunrise/sunset for [lat]/[lon] at the local-noon nearest [epochSec]. Uses Cooper-
     * style declination + Spencer-style EoT. Returns hour-of-day in GMT (UTC); the caller adds the
     * city's tz offset to get local hours. Same algorithm as Rem176OracleTest.computeSolarOracle —
     * duplicated here to keep each oracle test self-contained (Daten-Orakel hygiene: a shared
     * helper would couple the two tests). Accuracy ~few minutes — far inside any plausible-range
     * tolerance; the test-3 golden capture will pin the exact doc-RPN value.
     */
    private fun computeSolarOracle(epochSec: Long, lat: Double, lon: Double): SolarOracle {
        // Day-of-year from epoch days.
        val date = java.time.LocalDate.ofEpochDay(epochSec / 86400L)
        val n = date.dayOfYear.toDouble()
        // Solar declination (Cooper 1969): δ = 23.44° * sin(360 * (284 + n) / 365)
        val declRad = Math.toRadians(23.44 * sin(2.0 * PI * (284.0 + n) / 365.0))
        val latRad = Math.toRadians(lat)
        // Hour angle for civil sunrise (0° elevation): cos H = -tan(lat) * tan(decl)
        val cosH = -kotlin.math.tan(latRad) * kotlin.math.tan(declRad)
        if (cosH > 1.0 || cosH < -1.0) return SolarOracle(0.0, 24.0) // polar fallback (none expected here)
        val hourAngleHours = Math.toDegrees(acos(cosH)) / 15.0
        // Equation-of-time correction (Spencer ~few-min).
        val B = 2.0 * PI * (n - 81.0) / 364.0
        val eot = 9.87 * sin(2.0 * B) - 7.53 * cos(B) - 1.5 * sin(B)  // minutes
        // Solar noon in GMT hours: 12 - (lon/15) + EoT/60
        val solarNoonGMT = 12.0 - (lon / 15.0) + (eot / 60.0)
        return SolarOracle(
            sunriseHourGMT = solarNoonGMT - hourAngleHours,
            sunsetHourGMT = solarNoonGMT + hourAngleHours,
        )
    }
}
