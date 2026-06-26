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

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-36 b1 (fail-soft re-do) — theme palette is an **opt-in host seam**, **not** auto-applied: the
 * earlier auto-seed of a default `colorId` regressed ~69 color/plot/sensor docs. `getColor` is fail-soft
 * (unset ⇒ 0, never throws); the host/test seeds the real palette via [RemoteContext.seedThemePalette].
 */
class ThemePaletteSeedTest {

    @Test
    fun seedSystemVariables_doesNotAutoSeedColors() {
        val ctx = RemoteContext()
        ctx.seedSystemVariables(200f, 200f)
        assertEquals(0, ctx.getColor(1), "no global auto-seed → unset colorId stays 0 (no regression for the 69 docs)")
    }

    @Test
    fun getColor_isFailSoft_returnsZeroForAnyUnsetId() {
        val ctx = RemoteContext()
        assertEquals(0, ctx.getColor(1))
        assertEquals(0, ctx.getColor(999), "unseeded colorId ⇒ 0, never throws")
    }

    @Test
    fun seedThemePalette_injectsHostColors_optIn() {
        val ctx = RemoteContext()
        ctx.seedThemePalette(RemoteContext.DEFAULT_THEME_PALETTE)
        assertEquals(0xFF888888.toInt(), ctx.getColor(1), "opt-in default palette → visible colorId 1")
        ctx.seedThemePalette(mapOf(1 to 0xFFFF0000.toInt()))
        assertEquals(0xFFFF0000.toInt(), ctx.getColor(1), "host palette overrides")
    }
}
