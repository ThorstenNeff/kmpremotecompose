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
package com.tneff.kmpremotecompose.remote.player.core.utilities

import kotlin.test.Test
import kotlin.test.assertEquals

class CivilFromDaysTest {

    @Test
    fun unixEpoch_isThursday_Jan1_1970() {
        // The reference anchor — Hinnant's algorithm + the dayOfWeek formula both pivot off this.
        val (year, month, day) = CivilFromDays.civilFromDays(0L)
        assertEquals(1970L, year)
        assertEquals(1, month)
        assertEquals(1, day)
        assertEquals(1, CivilFromDays.dayOfYear(0L))
        // 1970-01-01 was a Thursday → upstream convention 1=Mon..7=Sun → Thu = 4.
        assertEquals(4, CivilFromDays.dayOfWeek(0L))
    }

    @Test
    fun epochSafePin_actualDate_is_2025_07_03_UTC() {
        // REM-177 calendar-correctness pin for EPOCH_SAFE_PIN = 1751529600L (the value REM-176
        // introduced — and the value REM-177 corrected the KDoc labels for after this Hinnant
        // port caught the miscalculation). The actual epoch-second-to-civil-date conversion
        // (verified independently via this port AND any standard epoch converter) is
        // **2025-07-03 00:00:00 UTC** (Thursday). The original mislabel as "2026-07-03" did not
        // break the REM-176 goldens — both 2025-07-03 and 2026-07-03 are non-leap July 3s with
        // day-of-year 184 and effectively identical Anchorage solar parameters — but the labels
        // are now corrected in RenderTimePins / DesktopRenderSweep / Rem176OracleTest KDocs to
        // match the algorithmic truth pinned here.
        val days = 1751529600L / 86400L // = 20272
        val (year, month, day) = CivilFromDays.civilFromDays(days)
        assertEquals(2025L, year, "EPOCH_SAFE_PIN's actual year is 2025 (REM-176-KDoc-Korrektur via REM-177)")
        assertEquals(7, month)
        assertEquals(3, day)
        assertEquals(184, CivilFromDays.dayOfYear(days)) // Jan=31, Feb=28, Mar=31, Apr=30, May=31, Jun=30, Jul=3 → 184
        // 2025-07-03 was a Thursday → 4 (1=Mon..7=Sun convention).
        assertEquals(4, CivilFromDays.dayOfWeek(days))
    }

    @Test
    fun leapYear_feb29_2024() {
        // 2024 is a leap year (divisible by 4, not by 100). Feb 29 must be reachable + the
        // day-of-year formula must use the leap-cumulative table.
        // 2024-02-29 = days since epoch = (2024-1970)*365 + leap-days + day-of-year offset.
        // Compute via the algorithm itself to verify round-trip:
        val daysToFeb29_2024 = epochDaysForCivil(2024, 2, 29)
        val (year, month, day) = CivilFromDays.civilFromDays(daysToFeb29_2024)
        assertEquals(2024L, year)
        assertEquals(2, month)
        assertEquals(29, day)
        // Day-of-year for Feb 29 in a leap year = 31 (Jan) + 29 = 60.
        assertEquals(60, CivilFromDays.dayOfYear(daysToFeb29_2024))
    }

    @Test
    fun leapYear_dec31_2024_is_day366() {
        // The last day of a leap year is day-of-year 366.
        val daysToDec31_2024 = epochDaysForCivil(2024, 12, 31)
        val (year, month, day) = CivilFromDays.civilFromDays(daysToDec31_2024)
        assertEquals(2024L, year)
        assertEquals(12, month)
        assertEquals(31, day)
        assertEquals(366, CivilFromDays.dayOfYear(daysToDec31_2024))
    }

    @Test
    fun nonLeapYear_dec31_2023_is_day365() {
        val daysToDec31_2023 = epochDaysForCivil(2023, 12, 31)
        val (year, month, day) = CivilFromDays.civilFromDays(daysToDec31_2023)
        assertEquals(2023L, year)
        assertEquals(12, month)
        assertEquals(31, day)
        assertEquals(365, CivilFromDays.dayOfYear(daysToDec31_2023))
    }

    @Test
    fun centuryNonLeap_1900() {
        // 1900 was NOT a leap year (divisible by 100, not by 400) — Gregorian rule. Feb 28 1900
        // is the last day of Feb; "Feb 29 1900" doesn't exist, so day-of-year for Mar 1 1900 must
        // be 60 (not 61 as in a hypothetical leap year).
        val daysToMar1_1900 = epochDaysForCivil(1900, 3, 1)
        val (year, month, day) = CivilFromDays.civilFromDays(daysToMar1_1900)
        assertEquals(1900L, year)
        assertEquals(3, month)
        assertEquals(1, day)
        assertEquals(60, CivilFromDays.dayOfYear(daysToMar1_1900))
    }

    @Test
    fun centuryLeap_2000() {
        // 2000 IS a leap year (divisible by 400). Feb 29 2000 exists; Mar 1 2000 day-of-year = 61.
        val daysToMar1_2000 = epochDaysForCivil(2000, 3, 1)
        val (year, month, day) = CivilFromDays.civilFromDays(daysToMar1_2000)
        assertEquals(2000L, year)
        assertEquals(3, month)
        assertEquals(1, day)
        assertEquals(61, CivilFromDays.dayOfYear(daysToMar1_2000))
    }

    @Test
    fun dayOfWeek_acrossWeek() {
        // Anchor: 1970-01-01 = Thursday (4). Verify each day of the following week.
        assertEquals(4, CivilFromDays.dayOfWeek(0L)) // Thu
        assertEquals(5, CivilFromDays.dayOfWeek(1L)) // Fri
        assertEquals(6, CivilFromDays.dayOfWeek(2L)) // Sat
        assertEquals(7, CivilFromDays.dayOfWeek(3L)) // Sun
        assertEquals(1, CivilFromDays.dayOfWeek(4L)) // Mon (wraps to 1)
        assertEquals(2, CivilFromDays.dayOfWeek(5L)) // Tue
        assertEquals(3, CivilFromDays.dayOfWeek(6L)) // Wed
        assertEquals(4, CivilFromDays.dayOfWeek(7L)) // Thu (next week)
    }

    @Test
    fun preEpoch_negativeDays() {
        // The algorithm must also handle dates before 1970-01-01 (negative daysSinceEpoch).
        // 1969-12-31 (one day before epoch) = Wednesday.
        val (year, month, day) = CivilFromDays.civilFromDays(-1L)
        assertEquals(1969L, year)
        assertEquals(12, month)
        assertEquals(31, day)
        assertEquals(365, CivilFromDays.dayOfYear(-1L))
        assertEquals(3, CivilFromDays.dayOfWeek(-1L)) // Wed
    }

    // --- helper: forward Gregorian (year, month, day) → daysSinceEpoch (inverse of civilFromDays) -

    /** Inverse of [CivilFromDays.civilFromDays] for testing — also Hinnant's `days_from_civil`. */
    private fun epochDaysForCivil(year: Long, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1L else year
        val era = if (y >= 0L) y / 400L else (y - 399L) / 400L
        val yoe = (y - era * 400L).toInt() // [0, 399]
        val mp = if (month > 2) month - 3 else month + 9 // [0, 11]
        val doy = (153 * mp + 2) / 5 + day - 1 // [0, 365]
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy // [0, 146096]
        return era * 146097L + doe.toLong() - 719468L
    }
}
