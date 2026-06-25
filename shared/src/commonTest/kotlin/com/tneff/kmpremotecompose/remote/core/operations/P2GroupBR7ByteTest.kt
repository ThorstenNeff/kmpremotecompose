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
package com.tneff.kmpremotecompose.remote.core.operations

import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmap
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawOps
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCompare
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesLoop
import com.tneff.kmpremotecompose.remote.core.operations.layout.ImpulseProcess
import com.tneff.kmpremotecompose.remote.core.operations.layout.LayoutOps
import com.tneff.kmpremotecompose.remote.wire.WireBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-op byte tests for the REM-29 final P2 coverage ops (the masked tail behind IMPULSE_PROCESS).
 *
 * IMPULSE_PROCESS / DRAW_BITMAP are anchored to real corpus bytes (haptic_demo_demo_haptic1 /
 * impulse_demo_confetti_demo). PARTICLE_LOOP / PARTICLE_COMPARE use spec-anchored byte vectors here
 * (their real corpus instances are 113 / 287 bytes); their **real-byte** verification is the full
 * corpus round-trip, which is byte-identical at 173/173 with these ops registered.
 */
class P2GroupBR7ByteTest {

    @AfterTest fun cleanup() = Operations.resetReaders()

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
    private fun writeBytes(op: Operation): ByteArray = WireBuffer().also { op.write(it) }.toByteArray()

    @Test fun impulseProcess_writesExactBytes() {
        // 165 (0xA5), fieldless. Real golden: haptic_demo_demo_haptic1.rc @0xac5.
        assertContentEquals(bytes(0xA5), writeBytes(ImpulseProcess()))
    }

    @Test fun drawBitmap_writesExactBytes() {
        // 44 (0x2C) + id 54 + left 0f + top 0f + right 50f + bottom 50f + descId 55. Real: impulse_demo_confetti_demo.rc @0x98e.
        assertContentEquals(
            bytes(
                0x2C, 0x00, 0x00, 0x00, 0x36, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x42, 0x48, 0x00, 0x00, 0x42, 0x48, 0x00, 0x00, 0x00, 0x00, 0x00, 0x37,
            ),
            writeBytes(DrawBitmap(54, 0f, 0f, 50f, 50f, 55)),
        )
    }

    @Test fun particleLoop_writesExactBytes() {
        // 163 (0xA3) + id 5 + restartLen 1 + 1f + varCount 1 + {equLen 2 + 2f,3f} (spec-anchored).
        assertContentEquals(
            bytes(
                0xA3, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x01, 0x3F, 0x80, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x40, 0x00, 0x00, 0x00, 0x40, 0x40, 0x00, 0x00,
            ),
            writeBytes(ParticlesLoop(5, floatArrayOf(1f), arrayOf(floatArrayOf(2f, 3f)))),
        )
    }

    @Test fun particleCompare_writesExactBytes() {
        // 194 (0xC2) + id 5 + flags 2 + min 0f + max 1f + compare[1f] + eq1[[2f]] + eq2[] (spec-anchored).
        assertContentEquals(
            bytes(
                0xC2, 0x00, 0x00, 0x00, 0x05, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x3F, 0x80, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x3F, 0x80, 0x00, 0x00, // compare: len 1 + 1f
                0x00, 0x00, 0x00, 0x01, // eq1 count 1
                0x00, 0x00, 0x00, 0x01, 0x40, 0x00, 0x00, 0x00, // eq1[0]: len 1 + 2f
                0x00, 0x00, 0x00, 0x00, // eq2 count 0
            ),
            writeBytes(
                ParticlesCompare(5, 2, 0f, 1f, floatArrayOf(1f), arrayOf(floatArrayOf(2f)), arrayOf()),
            ),
        )
    }

    private fun assertRoundTrips(op: Operation, reader: OperationReader) {
        val encoded = writeBytes(op)
        val buffer = WireBuffer.fromBytes(encoded)
        assertEquals(op.opcode, buffer.readByte(), "opcode byte")
        val decoded = mutableListOf<Operation>()
        reader.read(buffer, decoded)
        assertEquals(1, decoded.size)
        assertEquals(op, decoded[0], "decoded equals original")
        assertFalse(buffer.available(), "all bytes consumed")
        assertContentEquals(encoded, writeBytes(decoded[0]), "re-encode byte-identical")
    }

    @Test fun allR7Ops_roundTrip() {
        assertRoundTrips(ImpulseProcess(), ImpulseProcess)
        assertRoundTrips(DrawBitmap(54, 0f, 0f, 50f, 50f, 55), DrawBitmap)
        assertRoundTrips(ParticlesLoop(5, floatArrayOf(1f), arrayOf(floatArrayOf(2f, 3f))), ParticlesLoop)
        assertRoundTrips(ParticlesLoop(5, floatArrayOf(), arrayOf()), ParticlesLoop) // empty restart + no vars
        assertRoundTrips(
            ParticlesCompare(5, 2, 0f, 1f, floatArrayOf(1f), arrayOf(floatArrayOf(2f)), arrayOf()),
            ParticlesCompare,
        )
        assertRoundTrips( // empty compare + empty equation groups (null-as-0 symmetry)
            ParticlesCompare(7, 0, 0f, 0f, floatArrayOf(), arrayOf(), arrayOf()),
            ParticlesCompare,
        )
    }

    @Test fun register_r7() {
        Operations.resetReaders()
        DrawOps.register()
        LayoutOps.register()
        for (op in listOf(Operations.IMPULSE_PROCESS, Operations.PARTICLE_LOOP, Operations.DRAW_BITMAP)) {
            assertTrue(Operations.isValid(op, 6, 0), "v6 ${Operations.name(op)}")
            assertTrue(Operations.isValid(op, 7, 0), "v7 ${Operations.name(op)}")
        }
        // PARTICLE_COMPARE: androidx + widgets overlay.
        assertFalse(Operations.isValid(Operations.PARTICLE_COMPARE, 7, 0), "not baseline")
        assertTrue(Operations.isValid(Operations.PARTICLE_COMPARE, 7, Operations.PROFILE_ANDROIDX), "androidx")
        assertTrue(Operations.isValid(Operations.PARTICLE_COMPARE, 7, Operations.PROFILE_WIDGETS), "widgets")
    }
}
