/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose.remote.player

import com.tneff.kmpremotecompose.RcRouter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REM-91: `&density=<f>` parse → forcedDensity, fail-safe to null (= platform density). */
class Rem91ForcedDensityTest {

    @AfterTest fun reset() { RcRouter.setForcedDensity(null) }

    @Test fun validPositive_setsForcedDensity() {
        RcRouter.setForcedDensity("1.0"); assertEquals(1.0f, RcRouter.forcedDensity)
        RcRouter.setForcedDensity("3"); assertEquals(3.0f, RcRouter.forcedDensity)
        RcRouter.setForcedDensity(" 2.5 "); assertEquals(2.5f, RcRouter.forcedDensity)
    }

    @Test fun invalidOrAbsent_failsSafeToNull() {
        for (bad in listOf(null, "", "  ", "abc", "0", "-1", "NaN", "Infinity")) {
            RcRouter.setForcedDensity("1.0") // prime a non-null first
            RcRouter.setForcedDensity(bad)
            assertNull(RcRouter.forcedDensity, "density='$bad' must fail-safe to null (platform density)")
        }
    }

    @Test fun resetForLaunch_clearsForcedDensity() {
        RcRouter.setForcedDensity("1.0")
        RcRouter.resetForLaunch()
        assertNull(RcRouter.forcedDensity)
    }
}
