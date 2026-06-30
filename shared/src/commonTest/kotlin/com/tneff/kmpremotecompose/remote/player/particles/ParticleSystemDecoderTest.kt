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
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.conformance.RcCorpus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REM-143 — the particle-system decoder, validated against the real corpus doc particle.rc. */
@IgnoreOnWasm
class ParticleSystemDecoderTest {

    @Test fun decodesParticleRcSystems() {
        val doc = DocumentReader.inflate(RcCorpus.readFixture("corpus/particle.rc"))
        val systems = ParticleSystemDecoder.decode(doc)
        // particle.rc has two particle systems (id=59, id=73), 10 particles × 6 vars each (§7 "particle 10×2").
        assertEquals(2, systems.size, "two particle systems")
        for (s in systems) {
            assertEquals(10, s.create.particleCount, "10 particles")
            assertEquals(6, s.create.varIds.size, "6 vars")
            assertEquals(6, s.create.equations.size, "6 init equations")
            assertEquals(6, s.loop.equations.size, "6 update equations")
            assertEquals(s.create.id, s.loop.id, "create/loop share the system id")
            assertTrue(s.body.isNotEmpty(), "loop has a body")
            // the body draws (MATRIX_TRANSLATE placement + local shapes) and is fully closed before CONTAINER_END.
            assertTrue(s.body.any { it.opcode == Operations.MATRIX_TRANSLATE }, "body places via MATRIX_TRANSLATE")
            assertTrue(s.body.none { it.opcode == Operations.CONTAINER_END }, "body excludes its own closing CONTAINER_END")
            assertEquals(10, s.reconstruction.particleCount)
        }
    }
}
