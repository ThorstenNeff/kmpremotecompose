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

import com.tneff.kmpremotecompose.remote.core.operations.NamedVariable
import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import com.tneff.kmpremotecompose.remote.player.core.baselineHostPalette
import com.tneff.kmpremotecompose.remote.player.core.seedHostPalette
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-135 — the shared [seedHostPalette] seam. Proves the host palette actually reaches the store so a
 * `NamedVariable`-bound color resolves to the theme value (the seam every render/golden harness must use;
 * its absence in DesktopRenderSweep was the decode≠render gap where named colors fell back to the doc's
 * debug `ColorConstant`).
 */
class HostPaletteSeedTest {

    @Test fun seeded_namedColorResolvesToPaletteValue() {
        val ctx = RemoteContext()
        ctx.seedHostPalette() // default = systemAccentPalette()
        NamedVariable(varId = 70, varType = 0, name = "color.system_accent1_100").apply(ctx)
        assertEquals(0xFFD9E2FF.toInt(), ctx.getColor(70), "seeded → real Material-You tone, not the debug fallback")
    }

    @Test fun seeded_explicitPalette_overridesBoundId() {
        val ctx = RemoteContext()
        ctx.seedHostPalette(mapOf("host.brand" to 0xFF112233.toInt()))
        NamedVariable(varId = 71, varType = 0, name = "host.brand").apply(ctx)
        assertEquals(0xFF112233.toInt(), ctx.getColor(71))
    }

    @Test fun notSeeded_namedColorDoesNotOverride_failsSoftToDefault() {
        // The exact harness gap: without a seed, the name never overrides → getColor stays the default 0
        // (so a doc's debug ColorConstant fallback wins). This is what made goldens render cyan/green.
        val ctx = RemoteContext()
        NamedVariable(varId = 72, varType = 0, name = "color.system_accent1_100").apply(ctx)
        assertEquals(0, ctx.getColor(72), "no seed ⇒ no override")
    }

    @Test fun default_seedsTheDeterministicBaseline_notTheLivePalette() {
        // The no-arg default MUST be the deterministic baseline (capture determinism), so a harness that
        // calls seedHostPalette() gets the same palette on every target — incl. Android, deliberately
        // overriding its live Material-You accent (analog to the density=1.0 capture pin).
        val ctx = RemoteContext()
        ctx.seedHostPalette() // default
        val explicit = RemoteContext().also { it.seedHostPalette(baselineHostPalette()) }
        NamedVariable(varId = 73, varType = 0, name = "color.system_accent1_500").apply(ctx)
        NamedVariable(varId = 73, varType = 0, name = "color.system_accent1_500").apply(explicit)
        assertEquals(explicit.getColor(73), ctx.getColor(73), "default == explicit baseline")
        assertEquals(0xFF6476A5.toInt(), ctx.getColor(73), "baseline accent1_500 (deterministic)")
    }
}
