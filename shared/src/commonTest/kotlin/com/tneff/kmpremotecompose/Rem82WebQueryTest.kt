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
package com.tneff.kmpremotecompose

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REM-82 / W1 — the Web URL-query adapter. Proves the pure parse ([parseQuery]) and the shared
 * router-drive ([applyWebQueryToRouter]) on the JVM, so the wasmJs entry is reduced to a one-line
 * `window.location.search` read. The router-drive must mirror the mobile parsers exactly and reuse
 * RcRouter's fail-safes (unknown-doc sentinel, t fail-safe-to-0f).
 */
class Rem82WebQueryTest {

    @AfterTest fun reset() {
        // RcRouter is a process singleton — restore defaults so tests don't leak into each other.
        RcRouter.select(RcRouter.DEFAULT_DOC)
        RcRouter.resetForLaunch()
    }

    @Test fun parseQuery_basicAndEdgeCases() {
        assertEquals(emptyMap(), parseQuery(null))
        assertEquals(emptyMap(), parseQuery(""))
        assertEquals(emptyMap(), parseQuery("?"))
        assertEquals(mapOf("rc" to "clock"), parseQuery("?rc=clock"))
        assertEquals(mapOf("rc" to "clock"), parseQuery("rc=clock")) // leading '?' optional
        assertEquals(mapOf("rc" to "clock", "t" to "3", "live" to "1"), parseQuery("?rc=clock&t=3&live=1"))
        assertEquals(mapOf("live" to ""), parseQuery("?live")) // bare key ⇒ "" (so it reads as not "1")
        assertEquals(mapOf("rc" to "a b"), parseQuery("?rc=a+b")) // '+' ⇒ space
        assertEquals(mapOf("rc" to "a b"), parseQuery("?rc=a%20b")) // percent-decode
        assertEquals(mapOf("rc" to "second"), parseQuery("?rc=first&rc=second")) // last wins
    }

    @Test fun applyWebQuery_selectsDocAndPinsFrame() {
        applyWebQueryToRouter("?rc=clock&t=3")
        assertEquals("clock", RcRouter.docName)
        assertEquals(3f, RcRouter.staticTimeSeconds)
        assertFalse(RcRouter.live, "no live=1 ⇒ static")
    }

    @Test fun applyWebQuery_liveFlag() {
        applyWebQueryToRouter("?rc=clock&live=1")
        assertTrue(RcRouter.live)
    }

    @Test fun applyWebQuery_resetsTransientStateFirst() {
        // A prior capture left live + a pin; a fresh page-load query without them must reset to static t=0.
        RcRouter.live = true
        RcRouter.setStaticTime("5")
        applyWebQueryToRouter("?rc=clock")
        assertFalse(RcRouter.live, "fresh load ⇒ resetForLaunch ⇒ live=false (REM-62 discipline)")
        assertEquals(0f, RcRouter.staticTimeSeconds, "fresh load without &t ⇒ t=0")
    }

    @Test fun applyWebQuery_badName_isUnknownDocSentinel() {
        applyWebQueryToRouter("?rc=../etc/passwd")
        assertEquals(RcRouter.UNKNOWN_DOC, RcRouter.docName, "traversal/charset-invalid ⇒ UNKNOWN_DOC ⇒ rc-error")
    }

    @Test fun applyWebQuery_noRc_keepsCurrentDoc() {
        RcRouter.select("color_table")
        applyWebQueryToRouter("?t=2") // no rc param
        assertEquals("color_table", RcRouter.docName, "absent rc ⇒ keep current (default-launch contract §2A)")
    }

    @Test fun applyWebQuery_invalidT_failsSafeToZero() {
        applyWebQueryToRouter("?rc=clock&t=notanumber")
        assertEquals(0f, RcRouter.staticTimeSeconds, "non-numeric t ⇒ fail-safe 0f")
        applyWebQueryToRouter("?rc=clock&t=-4")
        assertEquals(0f, RcRouter.staticTimeSeconds, "negative t ⇒ fail-safe 0f")
    }

    @Test fun applyWebQuery_densityOverride_mirrorsMobileParsers() {
        // REM-91 cross-target parity: &density=<f> overrides platform density on web too (like Android/iOS).
        applyWebQueryToRouter("?rc=clock&density=3")
        assertEquals(3f, RcRouter.forcedDensity, "valid &density ⇒ forcedDensity override")
    }

    @Test fun applyWebQuery_absentOrInvalidDensity_failsSafeToNull() {
        applyWebQueryToRouter("?rc=clock") // no &density
        assertNull(RcRouter.forcedDensity, "absent density ⇒ null ⇒ platform density")
        applyWebQueryToRouter("?rc=clock&density=abc")
        assertNull(RcRouter.forcedDensity, "non-numeric density ⇒ fail-safe null")
        applyWebQueryToRouter("?rc=clock&density=-1")
        assertNull(RcRouter.forcedDensity, "non-positive density ⇒ fail-safe null")
    }
}
