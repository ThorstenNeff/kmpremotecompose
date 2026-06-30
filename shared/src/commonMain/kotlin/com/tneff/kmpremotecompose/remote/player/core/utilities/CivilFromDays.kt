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

/**
 * REM-177 — pure-int Gregorian calendar conversions for the player's Time/Date system-var seed,
 * derived **purely** from a Unix-epoch-seconds value (no platform clock dep, no `java.time`, no
 * `kotlinx-datetime`). Faithful port of Howard Hinnant's `civil_from_days` algorithm (public
 * domain, http://howardhinnant.github.io/date_algorithms.html#civil_from_days); valid for every
 * Gregorian year. Why we own it:
 *  - Upstream seeds these vars from a platform `RemoteClock.TimeSnapshot` (`java.time.LocalDate` on
 *    JVM, `NSCalendar` on iOS). Our commonMain port must work identically on jvm + Android + iOS +
 *    wasmJs; an `expect/actual` over a platform calendar would add 4 implementations to keep in
 *    sync.
 *  - The algorithm is deterministic in lockstep with `EPOCH_SAFE_PIN` for golden captures, and
 *    re-usable when REM-178-S2 wires `epochNow()` for live mode (same code path; only the seed
 *    source differs).
 *  - ~30 LOC, well-tested historical algorithm; cheaper than the 4-platform alternative.
 *
 * The companion derives `dayOfYear` and `dayOfWeek` directly from `daysSinceEpoch` so we don't
 * iterate `civilFromDays` twice when the seed wants all four (year/month/day-of-month/day-of-year).
 */
object CivilFromDays {

    /**
     * Convert [daysSinceEpoch] (Unix days since 1970-01-01) to a `(year, month, day-of-month)`
     * triple — year unbounded (`Long`), month 1..12, day 1..31. Faithful to Hinnant's
     * `civil_from_days` (`int_fast64_t`-equivalent arithmetic so leap-year handling is exact
     * across centuries). The algorithm shifts the epoch by 719468 days so year-0000 March 1 lands
     * at z=0 (puts February at the end of the calendar so leap-day arithmetic uses uniform
     * 365.25-style era cycles), then maps to the requested civil-calendar coordinates.
     */
    fun civilFromDays(daysSinceEpoch: Long): Triple<Long, Int, Int> {
        val z = daysSinceEpoch + 719468L
        val era = if (z >= 0L) z / 146097L else (z - 146096L) / 146097L
        val doe = (z - era * 146097L).toInt() // day-of-era ∈ [0, 146096]
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365 // year-of-era ∈ [0, 399]
        val y = yoe.toLong() + era * 400L
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // shifted-day-of-year ∈ [0, 365]
        val mp = (5 * doy + 2) / 153 // shifted-month ∈ [0, 11]
        val d = doy - (153 * mp + 2) / 5 + 1 // day-of-month ∈ [1, 31]
        val m = mp + if (mp < 10) 3 else -9 // unshifted-month ∈ [1, 12]
        val year = y + if (m <= 2) 1L else 0L
        return Triple(year, m, d)
    }

    /**
     * Compute the **calendar day-of-year** (1..366, 1 = January 1) for the date represented by
     * [daysSinceEpoch]. Pure-int: derives year/month/day-of-month via [civilFromDays], then sums
     * the cumulative month-day counts (leap-aware) and adds the day-of-month. Faithful to upstream
     * `RemoteClock.TimeSnapshot.getDayOfYear()` (1-366, where 366 = Dec 31 of a leap year).
     */
    fun dayOfYear(daysSinceEpoch: Long): Int {
        val (year, month, dayOfMonth) = civilFromDays(daysSinceEpoch)
        val isLeap = (year % 4L == 0L && year % 100L != 0L) || year % 400L == 0L
        // Cumulative days at the END of each preceding month (0-based: index 0 = Jan).
        val cumulative = if (isLeap) {
            intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)
        } else {
            intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        }
        return cumulative[month - 1] + dayOfMonth
    }

    /**
     * Compute the **day of week** (1=Monday..7=Sunday) for the date represented by
     * [daysSinceEpoch]. Unix-epoch day 0 = 1970-01-01 = Thursday → with the 1-Monday..7-Sunday
     * upstream convention Thursday = 4, so `daysSinceEpoch=0 → 4`. The formula
     * `((daysSinceEpoch + 3) % 7) + 1` maps it across the week. Handles negative days correctly
     * via the explicit `(rem + 7) % 7` normalization.
     */
    fun dayOfWeek(daysSinceEpoch: Long): Int {
        // (epoch_day + 3) gives a 0-based Monday=0..Sunday=6 offset since Thursday + 3 = 7 ≡ 0
        // (mod 7). The +1 brings it to the upstream 1..7 range.
        val rem = ((daysSinceEpoch + 3L) % 7L).toInt()
        val normalized = (rem + 7) % 7 // handles Kotlin's signed-rem for negative inputs
        return normalized + 1
    }
}
