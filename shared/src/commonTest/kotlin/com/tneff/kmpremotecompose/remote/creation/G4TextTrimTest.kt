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
import com.tneff.kmpremotecompose.remote.core.operations.IntegerExpression
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.TextLookup
import com.tneff.kmpremotecompose.remote.core.operations.TextMerge
import com.tneff.kmpremotecompose.remote.wire.WireTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REM-119 (G4-text-Trim) — byte-anchors per the REM-96 standard:
 * DSL output ↔ hand-computed expected ByteArray ↔ real corpus fixture region.
 *
 * The three opcodes covered:
 *  - `TEXT_MERGE`         (136 / 0x88): opcode + int textId + int srcId1 + int srcId2 — 13 bytes.
 *    **Triple-pin** — corpus-PRESENT in 19/173 docs (TEXT_MERGE is the broadest G4-text family op).
 *  - `TEXT_LOOKUP`        (151 / 0x97): opcode + int textId + int dataSet + float index — 13 bytes.
 *    **Triple-pin** — corpus-PRESENT in 8/173 chart-label demos.
 *  - `INTEGER_EXPRESSION` (144 / 0x90): opcode + int id + int mask + int count + count×int.
 *    **Triple-pin** — corpus-PRESENT in 1/173 (`experimental_solar_gmt.rc`). **Render-apply gap**
 *    (creation-only, like MatrixConstant — flagged for Render-Backlog).
 *
 * **Visible-skip** (REM-113-followup-redo standard): the visibility-check is order-INDEPENDENT,
 * does its own corpus inflate scan, includes a CONTROL-POSITIVE (TEXT_MERGE — broadest coverage),
 * no shared mutable state, no empty-escape.
 *
 * Source-grounded against `./androidx/.../operations/{TextMerge,TextLookup,IntegerExpression}.java`
 * + `RemoteComposeBuffer.{textMerge,textLookup,addIntegerExpression}`.
 */
class G4TextTrimTest {

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun ByteArray.readIntBE(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.readFloatBE(offset: Int): Float = Float.fromBits(readIntBE(offset))

    private fun intBytesBE(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(),
        (v ushr 8).toByte(), v.toByte(),
    )

    private fun floatBytesBE(v: Float): ByteArray = intBytesBE(v.toRawBits())

    private fun findOpcode(bytes: ByteArray, opcode: Int, from: Int = 0): Int {
        val target = opcode.toByte()
        for (i in from until bytes.size) if (bytes[i] == target) return i
        return -1
    }

    // ─── TEXT_MERGE ──────────────────────────────────────────────────────────

    @Test
    fun textMerge_emitsByteFaithfulWire_andRoundTrips() {
        // Wire = opcode + 3 ints = 13 bytes. Pin id-pool so textId/srcId1/srcId2 are deterministic.
        val srcId1 = 42
        val srcId2 = 43
        val expectedTextId = 44 // next id after both srcs are allocated
        val expected = byteArrayOf(0x88.toByte()) +
            intBytesBE(expectedTextId) + intBytesBE(srcId1) + intBytesBE(srcId2)
        assertEquals(13, expected.size, "TEXT_MERGE wire = opcode + 3 ints = 13 bytes")

        val dslBytes = document(width = 100, height = 100) {
            val a = addText("foo")
            val b = addText("bar")
            assertEquals(srcId1, a)
            assertEquals(srcId2, b)
            val merged = textMerge(a, b)
            assertEquals(expectedTextId, merged)
        }
        val opOffset = findOpcode(dslBytes, Operations.TEXT_MERGE)
        assertTrue(opOffset >= 0, "DSL must emit TEXT_MERGE op")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL TEXT_MERGE bytes diverge from hand-computed expected sequence",
        )

        // Round-trip parses back.
        val op = DocumentReader.inflate(dslBytes).operations.first { it is TextMerge } as TextMerge
        assertEquals(expectedTextId, op.textId)
        assertEquals(srcId1, op.srcId1)
        assertEquals(srcId2, op.srcId2)
    }

    @Test
    fun textMerge_matchesCorpusFixtureRegion_tripleAnchor() {
        runCorpusFixtureMatch(
            opcode = Operations.TEXT_MERGE,
            opSize = 13,
        ) { fixtureRegion ->
            val textId = fixtureRegion.readIntBE(1)
            val srcId1 = fixtureRegion.readIntBE(5)
            val srcId2 = fixtureRegion.readIntBE(9)
            // Pin id-pool to textId so the DSL allocates the same value.
            val ctx = RemoteComposeContext(
                writer = RemoteComposeWriter(width = 400, height = 400, apiLevel = 6),
                profile = Profile.Baseline,
            )
            ctx.ids.setNextId(textId)
            val out = ctx.textMerge(srcId1, srcId2)
            assertEquals(textId, out, "id-pool aligned to corpus textId for byte-comparison")
            val dslBytes = ctx.encodeToByteArray()
            val dslOff = findOpcode(dslBytes, Operations.TEXT_MERGE)
            dslBytes.copyOfRange(dslOff, dslOff + 13)
        }
    }

    // ─── TEXT_LOOKUP ──────────────────────────────────────────────────────────

    @Test
    fun textLookup_emitsByteFaithfulWire_andRoundTrips() {
        // Wire = opcode + int textId + int dataSet (bare) + float index = 13 bytes.
        // dataSet passed as NaN-encoded id-ref (the DSL accepts Float and unwraps via idFromNan).
        val dataSetId = 100
        val dataSetRef = WireTypes.asNan(dataSetId)
        val indexLiteral = 2.0f
        val expectedTextId = 42 // first plain alloc

        val expected = byteArrayOf(0x97.toByte()) +
            intBytesBE(expectedTextId) + intBytesBE(dataSetId) + floatBytesBE(indexLiteral)
        assertEquals(13, expected.size, "TEXT_LOOKUP wire = opcode + 2 ints + 1 float = 13 bytes")

        val dslBytes = document(width = 100, height = 100) {
            val tId = textLookup(dataSet = dataSetRef, index = indexLiteral)
            assertEquals(expectedTextId, tId)
        }
        val opOffset = findOpcode(dslBytes, Operations.TEXT_LOOKUP)
        assertTrue(opOffset >= 0, "DSL must emit TEXT_LOOKUP op")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL TEXT_LOOKUP bytes diverge from hand-computed expected sequence",
        )

        // Round-trip.
        val op = DocumentReader.inflate(dslBytes).operations.first { it is TextLookup } as TextLookup
        assertEquals(expectedTextId, op.textId)
        assertEquals(dataSetId, op.dataSet)
        assertEquals(indexLiteral.toRawBits(), op.index.toRawBits())
    }

    @Test
    fun textLookup_indexAsNanRef_preservesBits() {
        // The index slot is raw-float bits — a NaN-encoded var-ref must survive verbatim.
        val dataSetId = 200
        val varIndexId = 50
        val nanIndex = WireTypes.asNan(varIndexId)
        val dslBytes = document(width = 100, height = 100) {
            textLookup(dataSet = WireTypes.asNan(dataSetId), index = nanIndex)
        }
        val op = DocumentReader.inflate(dslBytes).operations.first { it is TextLookup } as TextLookup
        assertEquals(nanIndex.toRawBits(), op.index.toRawBits(),
            "NaN-encoded index var-ref bits must survive round-trip exactly")
        assertEquals(dataSetId, op.dataSet)
    }

    @Test
    fun textLookup_matchesCorpusFixtureRegion_tripleAnchor() {
        runCorpusFixtureMatch(
            opcode = Operations.TEXT_LOOKUP,
            opSize = 13,
        ) { fixtureRegion ->
            val textId = fixtureRegion.readIntBE(1)
            val dataSet = fixtureRegion.readIntBE(5)
            val index = fixtureRegion.readFloatBE(9)
            val ctx = RemoteComposeContext(
                writer = RemoteComposeWriter(width = 400, height = 400, apiLevel = 6),
                profile = Profile.Baseline,
            )
            ctx.ids.setNextId(textId)
            val out = ctx.textLookup(dataSet = WireTypes.asNan(dataSet), index = index)
            assertEquals(textId, out, "id-pool aligned to corpus textId")
            val dslBytes = ctx.encodeToByteArray()
            val dslOff = findOpcode(dslBytes, Operations.TEXT_LOOKUP)
            dslBytes.copyOfRange(dslOff, dslOff + 13)
        }
    }

    // ─── INTEGER_EXPRESSION ──────────────────────────────────────────────────

    @Test
    fun addIntegerExpression_emitsByteFaithfulWire_andRoundTrips() {
        // Wire = opcode + int id + int mask + int count + count×int.
        val mask = 0b0010 // entry index 1 is a var-id; rest are operators
        val value = intArrayOf(5, 42, /* I_ADD: opcode-marker per upstream */ 0x100_0001)
        val expectedId = 42 // first plain alloc
        val expected = byteArrayOf(0x90.toByte()) +
            intBytesBE(expectedId) + intBytesBE(mask) + intBytesBE(value.size) +
            intBytesBE(value[0]) + intBytesBE(value[1]) + intBytesBE(value[2])
        assertEquals(1 + 4 + 4 + 4 + 3 * 4, expected.size, "wire = 25 bytes for 3 int operands")

        val dslBytes = document(width = 100, height = 100) {
            val id = addIntegerExpression(mask, value)
            assertEquals(expectedId, id)
        }
        val opOffset = findOpcode(dslBytes, Operations.INTEGER_EXPRESSION)
        assertTrue(opOffset >= 0, "DSL must emit INTEGER_EXPRESSION op")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL INTEGER_EXPRESSION bytes diverge from hand-computed expected sequence",
        )

        // Round-trip.
        val op = DocumentReader.inflate(dslBytes).operations
            .first { it is IntegerExpression } as IntegerExpression
        assertEquals(expectedId, op.id)
        assertEquals(mask, op.mask)
        assertEquals(value.size, op.value.size)
        for (i in value.indices) assertEquals(value[i], op.value[i])
    }

    @Test
    fun addIntegerExpression_rejectsOverMaxSize() {
        // Mirror upstream IntegerExpression.MAX_SIZE = 32 cap, fail-closed at the caller.
        val ctx = RemoteComposeContext(
            writer = RemoteComposeWriter(width = 100, height = 100, apiLevel = 6),
            profile = Profile.Baseline,
        )
        val ex = kotlin.runCatching { ctx.addIntegerExpression(0, IntArray(33)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException, "33-int expression must be rejected; got $ex")
    }

    @Test
    fun addIntegerExpression_matchesCorpusFixtureRegion_tripleAnchor() {
        // Variable-size op — read count first, then size out the byte region.
        // findOpcode is fooled by 0x90 bytes in HEADER operand data, so we search ALL candidate
        // offsets in dslBytes and pick the one whose decoded operands match what we emitted.
        for (name in RcCorpus.corpusNames()) {
            val bytes = try { RcCorpus.readFixture("corpus/$name") } catch (_: Throwable) { continue }
            val decoded = try { DocumentReader.inflate(bytes).operations } catch (_: Throwable) { continue }
            if (decoded.none { it.opcode == Operations.INTEGER_EXPRESSION }) continue
            var from = 0
            while (true) {
                val off = findOpcode(bytes, Operations.INTEGER_EXPRESSION, from)
                if (off < 0) break
                if (off + 13 <= bytes.size) {
                    val id = bytes.readIntBE(off + 1)
                    val mask = bytes.readIntBE(off + 5)
                    val count = bytes.readIntBE(off + 9)
                    if (count in 0..IntegerExpression.MAX_SIZE && off + 13 + count * 4 <= bytes.size) {
                        val opByteSize = 13 + count * 4
                        val region = bytes.copyOfRange(off, off + opByteSize)
                        val value = IntArray(count) { i -> bytes.readIntBE(off + 13 + i * 4) }
                        val ctx = RemoteComposeContext(
                            writer = RemoteComposeWriter(width = 400, height = 400, apiLevel = 6),
                            profile = Profile.Baseline,
                        )
                        ctx.ids.setNextId(id)
                        val out = ctx.addIntegerExpression(mask, value)
                        if (out != id) { from = off + 1; continue }
                        val dslBytes = ctx.encodeToByteArray()
                        val dslOff = findIntegerExpressionAt(dslBytes, id, mask, count)
                        assertTrue(
                            dslOff >= 0,
                            "DSL must emit a verifiable INTEGER_EXPRESSION matching id=$id mask=$mask count=$count",
                        )
                        val dslRegion = dslBytes.copyOfRange(dslOff, dslOff + opByteSize)
                        assertTrue(
                            region.contentEquals(dslRegion),
                            "INTEGER_EXPRESSION: DSL output ≠ corpus fixture region in $name " +
                                "@ offset $off (id=$id, mask=$mask, count=$count)",
                        )
                        return
                    }
                }
                from = off + 1
            }
        }
    }

    /**
     * Find the byte offset of an INTEGER_EXPRESSION op whose first three int fields match the
     * given (id, mask, count). Robust against `0x90` bytes appearing in other ops' operand data
     * (the HEADER's resource block contains 0x90 by coincidence for some documents).
     */
    private fun findIntegerExpressionAt(bytes: ByteArray, id: Int, mask: Int, count: Int): Int {
        var from = 0
        while (true) {
            val off = findOpcode(bytes, Operations.INTEGER_EXPRESSION, from)
            if (off < 0) return -1
            if (off + 13 + count * 4 <= bytes.size &&
                bytes.readIntBE(off + 1) == id &&
                bytes.readIntBE(off + 5) == mask &&
                bytes.readIntBE(off + 9) == count
            ) {
                return off
            }
            from = off + 1
        }
    }

    // ─── corpus scanning helper for fixed-size ops ────────────────────────────

    /**
     * For fixed-size ops only. Scan the corpus for a DocumentReader-validated instance of [opcode],
     * extract the [opSize]-byte region, pass it to [decodeAndRebuild] (returns the DSL-rebuilt region),
     * assertContentEquals.
     */
    private fun runCorpusFixtureMatch(
        opcode: Int,
        opSize: Int,
        decodeAndRebuild: (fixtureRegion: ByteArray) -> ByteArray,
    ) {
        for (name in RcCorpus.corpusNames()) {
            val bytes = try { RcCorpus.readFixture("corpus/$name") } catch (_: Throwable) { continue }
            val decoded = try { DocumentReader.inflate(bytes).operations } catch (_: Throwable) { continue }
            if (decoded.none { it.opcode == opcode }) continue
            var from = 0
            while (true) {
                val off = findOpcode(bytes, opcode, from)
                if (off < 0) break
                if (off + opSize <= bytes.size) {
                    val fixtureRegion = bytes.copyOfRange(off, off + opSize)
                    val dslRegion = decodeAndRebuild(fixtureRegion)
                    assertTrue(
                        fixtureRegion.contentEquals(dslRegion),
                        "$opcode: DSL output ≠ corpus fixture region in $name @ offset $off",
                    )
                    return
                }
                from = off + 1
            }
        }
    }

    // ─── visible-skip degradation pin (order-INDEPENDENT, own-scan) ───────────

    @Test
    fun g4TextTrim_fixtureCoverage_visibilityCheck() {
        // Order-INDEPENDENT scan with control-positive (REM-113-followup-redo standard).
        // All 3 ops are corpus-PRESENT per the empirical scan that justified REM-119's trim
        // scope — TEXT_MERGE in 19 docs (broadest, used as CONTROL-POSITIVE),
        // TEXT_LOOKUP in 8 docs, INTEGER_EXPRESSION in 1 doc (experimental_solar_gmt.rc).
        val checked = setOf(
            Operations.TEXT_MERGE,
            Operations.TEXT_LOOKUP,
            Operations.INTEGER_EXPRESSION,
        )
        val present = mutableSetOf<Int>()
        for (name in RcCorpus.corpusNames()) {
            val bytes = try { RcCorpus.readFixture("corpus/$name") } catch (_: Throwable) { continue }
            val decoded = try { DocumentReader.inflate(bytes).operations } catch (_: Throwable) { continue }
            for (op in decoded) if (op.opcode in checked) present.add(op.opcode)
            if (present.size == checked.size) break
        }
        assertTrue(
            Operations.TEXT_MERGE in present,
            "Control-positive failed: TEXT_MERGE (136) not found in any corpus fixture via " +
                "DocumentReader.inflate. Either the inflate path regressed or the corpus has " +
                "changed substantially.",
        )
        val expectedAbsent = emptySet<Int>() // all 3 are corpus-PRESENT today
        val actualAbsent = checked - present
        assertEquals(
            expectedAbsent, actualAbsent,
            "REM-119 corpus-coverage snapshot — all 3 G4-text-Trim ops should be corpus-PRESENT. " +
                "A surprise here = a corpus shake-out dropped one of the fixtures. Update the " +
                "expectedAbsent set AND the class-doc/triple-pin test comments accordingly.",
        )
    }
}
