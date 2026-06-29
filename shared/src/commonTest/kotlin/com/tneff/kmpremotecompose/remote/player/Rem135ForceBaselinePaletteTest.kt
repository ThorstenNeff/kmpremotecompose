/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.RcRouter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REM-135: `&palette=baseline` parse → [RcRouter.forceBaselinePalette], fail-safe to `false`
 * (= the live device accent). Mirrors [Rem91ForcedDensityTest] for the palette capture-determinism pin.
 */
class Rem135ForceBaselinePaletteTest {

    @AfterTest fun reset() { RcRouter.setForceBaselinePalette(null) }

    @Test fun baselineLiteral_forcesBaselinePalette() {
        RcRouter.setForceBaselinePalette("baseline"); assertTrue(RcRouter.forceBaselinePalette)
        RcRouter.setForceBaselinePalette(" baseline "); assertTrue(RcRouter.forceBaselinePalette)
    }

    @Test fun absentOrOther_failsSafeToLiveAccent() {
        for (bad in listOf(null, "", "  ", "1", "true", "device", "BASELINE", "system")) {
            RcRouter.setForceBaselinePalette("baseline") // prime true first
            RcRouter.setForceBaselinePalette(bad)
            assertFalse(RcRouter.forceBaselinePalette, "palette='$bad' must fail-safe to false (live device accent)")
        }
    }

    @Test fun resetForLaunch_clearsForceBaselinePalette() {
        RcRouter.setForceBaselinePalette("baseline")
        RcRouter.resetForLaunch()
        assertFalse(RcRouter.forceBaselinePalette)
    }
}
