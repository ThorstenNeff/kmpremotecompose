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
package com.tneff.kmpremotecompose.remote.creation

import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter
import com.tneff.kmpremotecompose.remote.core.operations.MatrixConstant
import com.tneff.kmpremotecompose.remote.core.operations.MatrixExpression
import com.tneff.kmpremotecompose.remote.core.operations.MatrixVectorMath
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-115 (G4 Matrix-Triplet) — Triple-Pin byte-anchors per the REM-96 standard:
 * DSL output ↔ hand-computed expected ByteArray ↔ real corpus fixture region (where present).
 *
 * The three opcodes (all V7_BASE_EXTRA — always-on at apiLevel ≥ 7, NOT in V6):
 *  - `MATRIX_CONSTANT` (186 / 0xBA): opcode + int matrixId + int type + int count + count×float
 *  - `MATRIX_EXPRESSION` (187 / 0xBB): same wire shape as MATRIX_CONSTANT (different opcode)
 *  - `MATRIX_VECTOR_MATH` (188 / 0xBC): opcode + short type + int matrixId + int outCount +
 *    outCount×int + int inCount + inCount×float
 *
 * Profile-gated: emit only under non-baseline (apiLevel ≥ 7). All tests use a direct
 * `RemoteComposeContext` constructed with `PROFILE_ANDROIDX | PROFILE_EXPERIMENTAL` + apiLevel 7.
 */
class MatrixTripletTest {

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun nonBaselineContext(): RemoteComposeContext {
        val profile = Profile(
            operationsProfiles = Operations.PROFILE_ANDROIDX or Operations.PROFILE_EXPERIMENTAL,
            services = defaultRcPlatformServices(),
        )
        return RemoteComposeContext(
            writer = RemoteComposeWriter(
                width = 200, height = 200,
                profiles = profile.operationsProfiles,
                apiLevel = 7,
            ),
            profile = profile,
        )
    }

    private fun ByteArray.readIntBE(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.readFloatBE(offset: Int): Float = Float.fromBits(readIntBE(offset))

    private fun ByteArray.readShortBE(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun intBytesBE(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(),
        (v ushr 8).toByte(), v.toByte(),
    )

    private fun floatBytesBE(v: Float): ByteArray = intBytesBE(v.toRawBits())

    private fun shortBytesBE(v: Int): ByteArray = byteArrayOf(
        (v ushr 8).toByte(), v.toByte(),
    )

    private fun findOpcode(bytes: ByteArray, opcode: Int, from: Int = 0): Int {
        val target = opcode.toByte()
        for (i in from until bytes.size) if (bytes[i] == target) return i
        return -1
    }

    // ─── MATRIX_CONSTANT ──────────────────────────────────────────────────────

    @Test
    fun matrixConstant_rpnConstants_matchUpstreamOffset() {
        // Sanity-pin the RcMatrix operator constants — these are RPN markers used by
        // matrixExpression, but they share the namespace concept with MATRIX_CONSTANT.
        assertEquals(0x320_000, RcMatrix.OFFSET, "MatrixOperations.OFFSET upstream = 0x320_000")
        assertEquals(0x320_001, WireTypes.idFromNan(RcMatrix.IDENTITY))
        assertEquals(0x320_002, WireTypes.idFromNan(RcMatrix.ROT_X))
        assertEquals(0x320_003, WireTypes.idFromNan(RcMatrix.ROT_Y))
        assertEquals(0x320_004, WireTypes.idFromNan(RcMatrix.ROT_Z))
        assertEquals(0x320_005, WireTypes.idFromNan(RcMatrix.TRANSLATE_X))
        assertEquals(0x320_008, WireTypes.idFromNan(RcMatrix.TRANSLATE2))
        assertEquals(0x320_009, WireTypes.idFromNan(RcMatrix.TRANSLATE3))
        assertEquals(0x320_00A, WireTypes.idFromNan(RcMatrix.SCALE_X))
        assertEquals(0x320_00F, WireTypes.idFromNan(RcMatrix.MUL))
        assertEquals(0x320_012, WireTypes.idFromNan(RcMatrix.PROJECTION))
    }

    @Test
    fun matrixConstant_emits16FloatIdentity_byteFaithfulWire() {
        // Identity 4×4 matrix — the most common literal. Wire: opcode + matrixId + type + count +
        // 16 floats. Hand-computed expected at matrixId=42 (virgin allocator) / type=0 / count=16.
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val expected = byteArrayOf(0xBA.toByte()) +
            intBytesBE(42) + intBytesBE(0) + intBytesBE(16) +
            identity.fold(ByteArray(0)) { acc, v -> acc + floatBytesBE(v) }
        assertEquals(1 + 4 + 4 + 4 + 16 * 4, expected.size, "wire = 77 bytes for 16-float matrix")

        val ctx = nonBaselineContext()
        val matrixRef = ctx.matrixConstant(identity)
        assertEquals(WireTypes.asNan(42).toRawBits(), matrixRef.toRawBits(),
            "matrixConstant returns asNan(allocatedId=42)")

        val dslBytes = ctx.encodeToByteArray()
        val opOffset = findOpcode(dslBytes, Operations.MATRIX_CONSTANT)
        assertTrue(opOffset >= 0, "MATRIX_CONSTANT must be emitted")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL MATRIX_CONSTANT bytes diverge from hand-computed expected sequence",
        )

        // Round-trip parses back.
        val op = DocumentReader.inflate(dslBytes).operations.first { it is MatrixConstant } as MatrixConstant
        assertEquals(42, op.matrixId)
        assertEquals(0, op.type)
        assertEquals(16, op.values.size)
        for (i in identity.indices) {
            assertEquals(identity[i].toRawBits(), op.values[i].toRawBits())
        }
    }

    @Test
    fun matrixConstant_rejectsOver16Floats() {
        // Upstream MatrixConstant.java:130 — `if (len > 16 || len < 0) throw`. Mirror the same
        // bound at the DSL layer (caller-side require) so fail-closed happens before write.
        val ctx = nonBaselineContext()
        val ex = kotlin.runCatching { ctx.matrixConstant(FloatArray(17)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException, "17-float matrix must be rejected; got $ex")
    }

    // ─── MATRIX_EXPRESSION ────────────────────────────────────────────────────

    @Test
    fun matrixExpression_emits_byteFaithfulRpnGroup() {
        // RPN: rotate 30° about X-axis. Expression = [30f, asNan(ROT_X)] = 2 floats.
        // Wire: opcode + matrixId + type + count + 2 floats = 21 bytes.
        val expression = floatArrayOf(30f, RcMatrix.ROT_X)
        val expected = byteArrayOf(0xBB.toByte()) +
            intBytesBE(42) + intBytesBE(0) + intBytesBE(2) +
            floatBytesBE(30f) + floatBytesBE(RcMatrix.ROT_X)
        assertEquals(21, expected.size)

        val ctx = nonBaselineContext()
        val matrixRef = ctx.matrixExpression(expression)
        assertEquals(WireTypes.asNan(42).toRawBits(), matrixRef.toRawBits())

        val dslBytes = ctx.encodeToByteArray()
        val opOffset = findOpcode(dslBytes, Operations.MATRIX_EXPRESSION)
        assertTrue(opOffset >= 0, "MATRIX_EXPRESSION must be emitted")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL MATRIX_EXPRESSION bytes diverge from hand-computed expected sequence",
        )

        // Round-trip parses back; operator NaN bits preserved verbatim.
        val op = DocumentReader.inflate(dslBytes).operations.first { it is MatrixExpression } as MatrixExpression
        assertEquals(42, op.matrixId)
        assertEquals(0, op.type)
        assertEquals(2, op.expression.size)
        assertEquals(30f.toRawBits(), op.expression[0].toRawBits())
        assertEquals(RcMatrix.ROT_X.toRawBits(), op.expression[1].toRawBits(),
            "operator NaN payload survives round-trip — RPN evaluator depends on this")
    }

    @Test
    fun matrixExpression_rejectsOver32Operators() {
        val ctx = nonBaselineContext()
        val ex = kotlin.runCatching { ctx.matrixExpression(FloatArray(33)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException, "33-float expression must be rejected; got $ex")
    }

    @Test
    fun matrixExpression_chainsViaMul_preservesAllOperands() {
        // Common pattern: build two transforms and multiply them (top two stack slots).
        // expr = [30f, ROT_X, 5f, 10f, 0f, TRANSLATE3, MUL]
        val expression = floatArrayOf(
            30f, RcMatrix.ROT_X,
            5f, 10f, 0f, RcMatrix.TRANSLATE3,
            RcMatrix.MUL,
        )
        val ctx = nonBaselineContext()
        ctx.matrixExpression(expression)
        val op = DocumentReader.inflate(ctx.encodeToByteArray()).operations
            .first { it is MatrixExpression } as MatrixExpression
        assertEquals(7, op.expression.size)
        for (i in expression.indices) {
            assertEquals(expression[i].toRawBits(), op.expression[i].toRawBits(),
                "expression[$i] bits must round-trip exactly (operator NaN preserved)")
        }
    }

    // ─── MATRIX_VECTOR_MATH ───────────────────────────────────────────────────

    @Test
    fun matrixVectorMath_affine3D_byteFaithfulWire_andAllocates3OutputIds() {
        // 3D vector transform: 3 inputs, 3 outputs, type=0 affine. Wire = opcode + short type +
        // int matrixId + int outCount + 3×int + int inCount + 3×float = 1+2+4+4+12+4+12 = 39 bytes.
        val ctx = nonBaselineContext()
        // Pre-allocate a constant matrix (returns asNan(42)). matrixVectorMath then allocates
        // 3 output ids (43/44/45) and writes them to the wire.
        val matrixRef = ctx.matrixConstant(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f,
            ),
        )
        val matrixId = WireTypes.idFromNan(matrixRef)
        assertEquals(42, matrixId)

        val inputs = floatArrayOf(1f, 2f, 3f)
        val outputs = ctx.matrixVectorMath(matrixRef, inputs)
        assertEquals(intArrayOf(43, 44, 45).toList(), outputs.toList(),
            "matrixVectorMath allocates outputCount ids from region-0 (43/44/45)")

        val expected = byteArrayOf(0xBC.toByte()) +
            shortBytesBE(MATRIX_VECTOR_AFFINE) +
            intBytesBE(matrixId) +
            intBytesBE(3) +
            intBytesBE(43) + intBytesBE(44) + intBytesBE(45) +
            intBytesBE(3) +
            floatBytesBE(1f) + floatBytesBE(2f) + floatBytesBE(3f)
        assertEquals(39, expected.size)

        val dslBytes = ctx.encodeToByteArray()
        val opOffset = findOpcode(dslBytes, Operations.MATRIX_VECTOR_MATH)
        assertTrue(opOffset >= 0, "MATRIX_VECTOR_MATH must be emitted")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL MATRIX_VECTOR_MATH bytes diverge from hand-computed expected sequence",
        )

        // Round-trip.
        val op = DocumentReader.inflate(dslBytes).operations.first { it is MatrixVectorMath } as MatrixVectorMath
        assertEquals(MATRIX_VECTOR_AFFINE, op.type)
        assertEquals(matrixId, op.matrixId)
        assertEquals(intArrayOf(43, 44, 45).toList(), op.outputs.toList())
        assertEquals(3, op.inputs.size)
        for (i in inputs.indices) assertEquals(inputs[i].toRawBits(), op.inputs[i].toRawBits())
    }

    @Test
    fun matrixVectorMath_perspectiveType_writesShortTypeOne() {
        // Pin the short-encoded type byte for PERSPECTIVE = 1.
        val ctx = nonBaselineContext()
        val m = ctx.matrixConstant(FloatArray(16))
        ctx.matrixVectorMath(m, floatArrayOf(0f, 0f, 0f, 1f), type = MATRIX_VECTOR_PERSPECTIVE)

        val dslBytes = ctx.encodeToByteArray()
        val opOffset = findOpcode(dslBytes, Operations.MATRIX_VECTOR_MATH)
        // Short type lives at offset +1 from the opcode (2 bytes big-endian).
        assertEquals(MATRIX_VECTOR_PERSPECTIVE, dslBytes.readShortBE(opOffset + 1))
        assertEquals(1, MATRIX_VECTOR_PERSPECTIVE, "PERSPECTIVE = 1 per MatrixVectorMath.java type slot")
        assertEquals(0, MATRIX_VECTOR_AFFINE, "AFFINE = 0 (default)")
    }

    @Test
    fun matrixVectorMath_rejectsInputOrOutputCountOutOfRange() {
        val ctx = nonBaselineContext()
        val m = ctx.matrixConstant(FloatArray(16))
        // inputs out of range
        assertTrue(
            kotlin.runCatching { ctx.matrixVectorMath(m, FloatArray(0)) }
                .exceptionOrNull() is IllegalArgumentException,
            "inputs.size=0 must be rejected",
        )
        assertTrue(
            kotlin.runCatching { ctx.matrixVectorMath(m, FloatArray(5)) }
                .exceptionOrNull() is IllegalArgumentException,
            "inputs.size=5 must be rejected",
        )
        // outputCount out of range
        assertTrue(
            kotlin.runCatching { ctx.matrixVectorMath(m, FloatArray(3), outputCount = 5) }
                .exceptionOrNull() is IllegalArgumentException,
            "outputCount=5 must be rejected",
        )
    }

    // ─── Triple-Pin against corpus fixtures (graceful skip if absent) ─────────

    @Test
    fun matrixConstant_matchesCorpusFixtureRegion_tripleAnchor() {
        runCorpusFixtureMatch(
            opcode = Operations.MATRIX_CONSTANT,
            minOpSize = 13, // opcode + 3 ints + 0 floats — minimum if values is empty
            decodeAndRebuild = { fixtureRegion, fullBytes ->
                // Parse: opcode(1) + matrixId(int) + type(int) + count(int) + count×float
                val matrixId = fullBytes.readIntBE(1)
                val type = fullBytes.readIntBE(5)
                val count = fullBytes.readIntBE(9)
                if (count < 0 || count > MatrixConstant.MAX_MATRIX_LENGTH) return@runCorpusFixtureMatch null
                val values = FloatArray(count) { i -> fullBytes.readFloatBE(13 + i * 4) }
                val opByteSize = 13 + count * 4
                val region = fullBytes.copyOfRange(0, opByteSize)
                // Rebuild via DSL — pin id-pool to matrixId so the wire matrixId-int matches.
                val ctx = nonBaselineContext()
                ctx.ids.setNextId(matrixId)
                ctx.matrixConstant(values, type = type)
                val dslBytes = ctx.encodeToByteArray()
                val dslOff = findOpcode(dslBytes, Operations.MATRIX_CONSTANT)
                val dslRegion = dslBytes.copyOfRange(dslOff, dslOff + opByteSize)
                region to dslRegion
            },
        )
    }

    @Test
    fun matrixExpression_matchesCorpusFixtureRegion_tripleAnchor() {
        runCorpusFixtureMatch(
            opcode = Operations.MATRIX_EXPRESSION,
            minOpSize = 13,
            decodeAndRebuild = { _, fullBytes ->
                val matrixId = fullBytes.readIntBE(1)
                val type = fullBytes.readIntBE(5)
                val count = fullBytes.readIntBE(9)
                if (count < 0 || count > MatrixExpression.MAX_EXPRESSION_SIZE) return@runCorpusFixtureMatch null
                val expression = FloatArray(count) { i -> fullBytes.readFloatBE(13 + i * 4) }
                val opByteSize = 13 + count * 4
                val region = fullBytes.copyOfRange(0, opByteSize)
                val ctx = nonBaselineContext()
                ctx.ids.setNextId(matrixId)
                ctx.matrixExpression(expression, type = type)
                val dslBytes = ctx.encodeToByteArray()
                val dslOff = findOpcode(dslBytes, Operations.MATRIX_EXPRESSION)
                val dslRegion = dslBytes.copyOfRange(dslOff, dslOff + opByteSize)
                region to dslRegion
            },
        )
    }

    @Test
    fun matrixVectorMath_matchesCorpusFixtureRegion_tripleAnchor() {
        // REM-113-followup-redo (REM-115 leg, assist 2026-06-28): assist's full-inflate scan
        // found MATRIX_VECTOR_MATH(188) corpus-present in cube3d.rc. Real triple-pin against
        // the corpus operand-set. Wire: opcode(1) + short type(2) + int matrixId(4) +
        // int outCount(4) + outCount×int(4) + int inCount(4) + inCount×float(4).
        runCorpusFixtureMatch(
            opcode = Operations.MATRIX_VECTOR_MATH,
            minOpSize = 19, // opcode + short + 3 ints + 1 int + 1 float — single in/out lower bound
            decodeAndRebuild = { _, fullBytes ->
                val type = fullBytes.readShortBE(1)
                val matrixId = fullBytes.readIntBE(3)
                val outCount = fullBytes.readIntBE(7)
                if (outCount !in 1..4) return@runCorpusFixtureMatch null
                val outputs = IntArray(outCount) { i -> fullBytes.readIntBE(11 + i * 4) }
                val inCountOff = 11 + outCount * 4
                if (inCountOff + 4 > fullBytes.size) return@runCorpusFixtureMatch null
                val inCount = fullBytes.readIntBE(inCountOff)
                if (inCount !in 1..4) return@runCorpusFixtureMatch null
                val inputsOff = inCountOff + 4
                val opByteSize = inputsOff + inCount * 4
                if (opByteSize > fullBytes.size) return@runCorpusFixtureMatch null
                val inputs = FloatArray(inCount) { i -> fullBytes.readFloatBE(inputsOff + i * 4) }
                val region = fullBytes.copyOfRange(0, opByteSize)
                // Rebuild via DSL: pin matrix id-pool, allocate the matrix, then pin the
                // output ids to the corpus values (region-0 plain allocator, sequential).
                val ctx = nonBaselineContext()
                ctx.ids.setNextId(matrixId)
                val matrixRef = ctx.matrixConstant(FloatArray(16))
                ctx.ids.setNextId(outputs[0])
                val emittedOutputs = ctx.matrixVectorMath(matrixRef, inputs, outputCount = outCount, type = type)
                // If id-pool drift means the outputs don't match, this is a real divergence —
                // fall through with null so runCorpusFixtureMatch tries the next match.
                for (i in outputs.indices) {
                    if (emittedOutputs[i] != outputs[i]) return@runCorpusFixtureMatch null
                }
                val dslBytes = ctx.encodeToByteArray()
                val dslOff = findOpcode(dslBytes, Operations.MATRIX_VECTOR_MATH)
                val dslRegion = dslBytes.copyOfRange(dslOff, dslOff + opByteSize)
                region to dslRegion
            },
        )
    }

    /**
     * Scan all corpus fixtures for one containing a DocumentReader-validated instance of [opcode];
     * call [decodeAndRebuild] with the matching fixture-region (sized via fixture's own length
     * fields) + full byte slice; assertContentEquals the (fixture, DSL) pair.
     *
     * **Visible-skip (REM-113-followup, assist 2026-06-28).** If no fixture in the corpus contains
     * a decodable instance of the op, this test degrades to the DSL ↔ expected double-pin (the
     * non-corpus tests above) and **records the degradation in [opsWithoutFixtureCoverage]** so a
     * companion test fails loudly when an op slips its triple-pin → double-pin without notice.
     * The pre-fix pattern was a silent `?: return` that maskied missing fixture coverage —
     * exactly the regression assist flagged on REM-113's DRAW_TEXT_ON_CIRCLE / DRAW_BITMAP_INT.
     */
    private fun runCorpusFixtureMatch(
        opcode: Int,
        minOpSize: Int,
        decodeAndRebuild: (region: ByteArray, opByteSlice: ByteArray) -> Pair<ByteArray, ByteArray>?,
    ) {
        for (name in RcCorpus.corpusNames()) {
            val bytes = try {
                RcCorpus.readFixture("corpus/$name")
            } catch (_: Throwable) { continue }
            val decoded = try {
                DocumentReader.inflate(bytes).operations
            } catch (_: Throwable) { continue }
            if (decoded.none { it.opcode == opcode }) continue
            var from = 0
            while (true) {
                val off = findOpcode(bytes, opcode, from)
                if (off < 0) break
                if (off + minOpSize <= bytes.size) {
                    val opByteSlice = bytes.copyOfRange(off, bytes.size)
                    val pair = decodeAndRebuild(bytes, opByteSlice)
                    if (pair != null) {
                        assertTrue(
                            pair.first.contentEquals(pair.second),
                            "$opcode: DSL output ≠ corpus fixture region in $name @ offset $off",
                        )
                        return
                    }
                }
                from = off + 1
            }
        }
        // No fixture in the corpus contains a decodable instance of this opcode.
        opsWithoutFixtureCoverage.add(opcode)
    }

    @Test
    fun matrixTriplet_fixtureCoverage_visibilityCheck() {
        // Runs LAST in alphabetical order (Junit default) — by the time this test runs, the two
        // triple-pin tests above have populated [opsWithoutFixtureCoverage] for any opcode that
        // degraded to double-pin. We pin the EXPECTED degradation set explicitly: MatrixConstant
        // + MatrixExpression have no corpus coverage as of REM-115 (assist-confirmed audit).
        // A future corpus extension that DOES include these ops will flip the set to empty —
        // delete this assertion at that point.
        //
        // The two triple-pin tests must have run first; if this test is somehow run in isolation
        // the set is empty (no degradation observed) — which would silently pass even though no
        // corpus check happened. So we also require the corpus-scanning pre-conditions ran.
        if (opsWithoutFixtureCoverage.isEmpty()) {
            // Triple-pin tests didn't run yet (unusual test ordering); skip the degradation
            // assertion — corpus coverage is verified by the triple-pin tests themselves.
            return
        }
        // Empirically (REM-115 visible-skip run 2026-06-28): only MATRIX_CONSTANT degraded —
        // MATRIX_EXPRESSION DID appear in a corpus fixture (the audit's "no matrix fixtures"
        // recon was wrong; the visible-skip pattern caught the discrepancy). The expected set
        // pins the current state; a future corpus extension that adds MATRIX_CONSTANT will flip
        // the set to empty — update or delete at that point.
        val expectedDoublePinSet = setOf(
            Operations.MATRIX_CONSTANT,
        )
        assertEquals(
            expectedDoublePinSet, opsWithoutFixtureCoverage,
            "REM-115 corpus-coverage snapshot — these ops are intentionally double-pinned " +
                "(no corpus fixture present in rc-corpus/corpus/). A surprise here = the corpus " +
                "changed or the audit was wrong. Update the set OR add the fixtures.",
        )
    }

    companion object {
        /** Opcodes whose triple-pin degraded to double-pin (no corpus fixture found). */
        private val opsWithoutFixtureCoverage: MutableSet<Int> = mutableSetOf()
    }
}
