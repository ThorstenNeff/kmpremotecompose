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
package com.tneff.kmpremotecompose.conformance

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the portable [RcCorpus.fixtureRoot] (build-time absolute corpus root) loads the real corpus
 * **without** any `rootOverride`, on both host (jvmTest) and the iOS simulator — closing the deferred
 * platform-resolution gap. (The F1 byte-equality assertion itself is test-2's `RcConformanceP0Test`.)
 */
class RcCorpusFixtureRootTest {

    @BeforeTest
    fun setUp() {
        RcCorpus.rootOverride = null // exercise the generated fixtureRoot, not an override
    }

    @AfterTest
    fun tearDown() {
        RcCorpus.rootOverride = null
    }

    @Test
    fun readFixture_resolvesCorpusViaGeneratedRoot() {
        val bytes = RcCorpus.readFixture("procedure_simple1.rc")
        assertEquals(61, bytes.size) // the F1 fixture is 61 bytes
    }

    @Test
    fun readFixture_missing_throws() {
        assertFailsWith<okio.IOException> { RcCorpus.readFixture("definitely_absent.rc") }
    }
}
