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
 * REM-36 b1 — theme-palette seed: a `colorId` background-ref with no in-doc color op (c_modifier_background_id)
 * resolves to a **visible** default so it renders non-transparent; the host-injected palette overrides it.
 */
class ThemePaletteSeedTest {

    @Test
    fun seedSystemVariables_seedsVisibleDefaultForUnsetThemeColor() {
        val ctx = RemoteContext()
        ctx.seedSystemVariables(200f, 200f)
        assertEquals(
            0xFF888888.toInt(), ctx.getColor(1),
            "unseeded theme colorId 1 → visible default (not 0/transparent)",
        )
    }

    @Test
    fun hostInjectedPalette_overridesDefault() {
        val ctx = RemoteContext()
        ctx.seedThemePalette(mapOf(1 to 0xFFFF0000.toInt())) // host theme: red
        ctx.seedSystemVariables(200f, 200f) // must NOT overwrite the host value
        assertEquals(0xFFFF0000.toInt(), ctx.getColor(1), "host palette wins over the default placeholder")
    }
}
