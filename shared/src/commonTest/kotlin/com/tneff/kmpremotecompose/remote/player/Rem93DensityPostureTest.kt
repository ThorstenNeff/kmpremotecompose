/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.remote.player.core.RemoteContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REM-93 (density posture): `seedSystemVariables` seeds ID_DENSITY with the doc's GENERATION density,
 * NOT the device density — so FLOAT_DENSITY-referencing coords stay in the doc-px (gen-density) canvas
 * space and don't over-scale at a high device density (the moon_phases r 150→450 @3x bug).
 */
class Rem93DensityPostureTest {

    @Test fun seedsGenerationDensity_notDeviceDensity() {
        val ctx = RemoteContext()
        ctx.setDensity(3f) // device density (e.g. iOS-Sim @3x)
        ctx.seedSystemVariables(500f, 500f, 0f, genDensity = 1f) // doc gen-density 1.0
        assertEquals(1f, ctx.getFloat(RemoteContext.ID_DENSITY),
            "ID_DENSITY must be the generation density (1.0), not the device density (3.0)")
    }

    @Test fun nonVacuous_defaultGenDensityIsDeviceDensity() {
        // Proof the param actually changes behavior: omitting genDensity keeps the old device-density seed.
        val ctx = RemoteContext()
        ctx.setDensity(3f)
        ctx.seedSystemVariables(500f, 500f, 0f) // default genDensity = ctx.density (3.0)
        assertEquals(3f, ctx.getFloat(RemoteContext.ID_DENSITY),
            "default (no genDensity) = device density → proves the explicit gen-density seed is what flips it")
    }
}
