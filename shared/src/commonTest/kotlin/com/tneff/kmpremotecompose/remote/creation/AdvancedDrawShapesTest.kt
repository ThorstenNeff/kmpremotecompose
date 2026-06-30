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

import com.tneff.kmpremotecompose.conformance.IgnoreOnWasm
import com.tneff.kmpremotecompose.conformance.RcCorpus
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawBitmapInt
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawRoundRect
import com.tneff.kmpremotecompose.remote.core.operations.draw.DrawTextOnCircle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * REM-113 (G2 Advanced-Draw-Shapes) — byte-anchors per the REM-96 standard:
 * DSL output ↔ hand-computed expected ByteArray ↔ real corpus fixture region.
 *
 * The three opcodes covered are:
 *  - `DRAW_ROUND_RECT` (51 / 0x33): 25 bytes, 6 floats (l/t/r/b/rx/ry) — **triple-pin** (corpus coverage)
 *  - `DRAW_TEXT_ON_CIRCLE` (57 / 0x39): 27 bytes, int textId + 5 floats + 2 enum bytes —
 *    **double-pin** (corpus-ABSENT per assist's authoritative 173-doc inflate scan,
 *    REM-113-followup-redo 2026-06-28; the hand-computed + DSL round-trip pin still runs,
 *    the fixture-anchor degrades to visible-skip). My earlier "audit was wrong" claim was
 *    itself wrong — the prior visible-skip test was vacuous-green because of `?: return`
 *    + alphabetical-order assumption + cross-test mutable state. Lesson re-learned: trust
 *    the full-inflate probe with a control-positive, not a unit-test status indicator.
 *  - `DRAW_BITMAP_INT` (66 / 0x42): 41 bytes, 10 ints (imageId + 8 coords + cdId) —
 *    **double-pin** (corpus-ABSENT; same harness as op-57)
 *
 * Strategy for "real fixture" pin: scan corpus fixtures for the op-opcode byte, extract the
 * known-size op-region, decode the operands, build a minimal DSL document with those decoded
 * operands, then assertContentEquals the DSL's op-region against the fixture's op-region. This
 * proves the DSL can reproduce ANY operand-set the corpus happens to use.
 *
 * **Visible-skip (REM-113-followup-redo, assist 2026-06-28).** The visibility-check is now
 * **order-INDEPENDENT** — it does its own corpus inflate scan (no shared mutable state with the
 * triple/double-pin tests, no empty-escape) and includes a **control-positive** (op-51 must be
 * found) so a broken inflate path fails the gate instead of silently passing. The corpus tests
 * for op-57 + op-66 keep `findCorpusFixtureWithOp(...) ?: return` as a soft fail (a future
 * corpus extension that adds either op activates the byte-match branch) — the visibility-check
 * is the loud gate that pins the expected corpus-absence set.
 *
 * Source-grounded against `./androidx/.../operations/{DrawRoundRect,DrawTextOnCircle,DrawBitmapInt}.java`.
 */
class AdvancedDrawShapesTest {

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun ByteArray.readIntBE(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.readFloatBE(offset: Int): Float = Float.fromBits(readIntBE(offset))

    /** Find the first occurrence of [opcode] (as a byte) in [bytes] starting at [from]. -1 if none. */
    private fun findOpcode(bytes: ByteArray, opcode: Int, from: Int = 0): Int {
        val target = opcode.toByte()
        for (i in from until bytes.size) {
            if (bytes[i] == target) return i
        }
        return -1
    }

    // ─── DRAW_ROUND_RECT ──────────────────────────────────────────────────────

    @Test
    fun drawRoundRect_emitsByteFaithfulWire_andRoundTrips() {
        // Hand-computed expected wire: opcode 0x33 + 6 floats.
        val l = 10.5f; val t = 20.25f; val r = 100.75f; val b = 200f; val rx = 5f; val ry = 7.5f
        val expected = byteArrayOf(0x33) +
            floatBytesBE(l) + floatBytesBE(t) + floatBytesBE(r) + floatBytesBE(b) +
            floatBytesBE(rx) + floatBytesBE(ry)
        assertEquals(25, expected.size, "DRAW_ROUND_RECT wire = opcode + 6 floats = 25 bytes")

        // DSL output region.
        val dslBytes = document(width = 100, height = 100) {
            drawRoundRect(l, t, r, b, rx, ry)
        }
        val dslOpOffset = findOpcode(dslBytes, Operations.DRAW_ROUND_RECT)
        assertTrue(dslOpOffset >= 0, "DSL must emit DRAW_ROUND_RECT op")
        val dslRegion = dslBytes.copyOfRange(dslOpOffset, dslOpOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL DRAW_ROUND_RECT bytes diverge from hand-computed expected sequence",
        )

        // Round-trip parses back.
        val op = DocumentReader.inflate(dslBytes).operations.first { it is DrawRoundRect } as DrawRoundRect
        assertEquals(l, op.left); assertEquals(t, op.top)
        assertEquals(r, op.right); assertEquals(b, op.bottom)
        assertEquals(rx, op.radiusX); assertEquals(ry, op.radiusY)
    }

    @Test
    @IgnoreOnWasm
    fun drawRoundRect_matchesCorpusFixtureRegion_tripleAnchor() {
        // Find a corpus fixture containing DRAW_ROUND_RECT (0x33), extract the op-region,
        // decode the operands, rebuild via DSL, assert byte-equality.
        val fixture = findCorpusFixtureWithOp(Operations.DRAW_ROUND_RECT, opSize = 25)
            ?: return // tolerant: skip if no fixture in corpus contains a clean instance
        val (bytes, opOffset) = fixture
        val fixtureRegion = bytes.copyOfRange(opOffset, opOffset + 25)

        // Decode operands.
        val left = fixtureRegion.readFloatBE(1)
        val top = fixtureRegion.readFloatBE(5)
        val right = fixtureRegion.readFloatBE(9)
        val bottom = fixtureRegion.readFloatBE(13)
        val radiusX = fixtureRegion.readFloatBE(17)
        val radiusY = fixtureRegion.readFloatBE(21)

        // Rebuild via DSL.
        val dslBytes = document(width = 400, height = 400) {
            drawRoundRect(left, top, right, bottom, radiusX, radiusY)
        }
        val dslOpOffset = findOpcode(dslBytes, Operations.DRAW_ROUND_RECT)
        val dslRegion = dslBytes.copyOfRange(dslOpOffset, dslOpOffset + 25)

        assertTrue(
            fixtureRegion.contentEquals(dslRegion),
            "DRAW_ROUND_RECT: DSL output ≠ corpus fixture region (operands: l=$left, t=$top, " +
                "r=$right, b=$bottom, rx=$radiusX, ry=$radiusY)",
        )
    }

    // ─── DRAW_TEXT_ON_CIRCLE ──────────────────────────────────────────────────

    @Test
    fun drawTextOnCircle_emitsByteFaithfulWire_andRoundTrips() {
        // Hand-computed: opcode 0x39 + int textId + 5 floats + 2 enum bytes = 27 bytes.
        // textId pinned at 43 via the addText prolog reservation.
        val textId = 43
        val cx = 100f; val cy = 100f; val rad = 50f; val startAngle = 0f; val warp = 0f
        val alignmentByte: Byte = 1 // CENTER
        val placementByte: Byte = 0 // OUTSIDE
        val expected = byteArrayOf(0x39) +
            intBytesBE(textId) +
            floatBytesBE(cx) + floatBytesBE(cy) + floatBytesBE(rad) +
            floatBytesBE(startAngle) + floatBytesBE(warp) +
            byteArrayOf(alignmentByte, placementByte)
        assertEquals(27, expected.size, "DRAW_TEXT_ON_CIRCLE wire = opcode + int + 5 floats + 2 bytes = 27")

        // DSL produces it. addText reserves id 42 for content-desc-style prolog usage; with
        // contentDescription = null the prolog doesn't claim id 42, so addText returns 42 here.
        // We pin to textId=43 by reserving via a content-description-style prolog.
        val dslBytes = document(width = 200, height = 200, contentDescription = "Clock") {
            // content-desc claims id 42. The next addText gets 43 = our textId.
            val tId = addText("hi")
            assertEquals(43, tId)
            drawTextOnCircle(
                textId = tId,
                centerX = cx, centerY = cy, radius = rad,
                startAngle = startAngle, warpRadiusOffset = warp,
                alignment = DrawTextOnCircle.Alignment.CENTER,
                placement = DrawTextOnCircle.Placement.OUTSIDE,
            )
        }
        val opOffset = findOpcode(dslBytes, Operations.DRAW_TEXT_ON_CIRCLE)
        assertTrue(opOffset >= 0, "DSL must emit DRAW_TEXT_ON_CIRCLE op")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL DRAW_TEXT_ON_CIRCLE bytes diverge from hand-computed expected sequence",
        )

        // Round-trip parses back.
        val op = DocumentReader.inflate(dslBytes).operations
            .first { it is DrawTextOnCircle } as DrawTextOnCircle
        assertEquals(textId, op.textId)
        assertEquals(cx, op.centerX); assertEquals(cy, op.centerY); assertEquals(rad, op.radius)
        assertEquals(startAngle, op.startAngle); assertEquals(warp, op.warpRadiusOffset)
        assertEquals(DrawTextOnCircle.Alignment.CENTER, op.alignment)
        assertEquals(DrawTextOnCircle.Placement.OUTSIDE, op.placement)
    }

    @Test
    fun drawTextOnCircle_alignmentPlacementOrdinals_matchUpstream() {
        // The two enum bytes encode the ordinal — pin the order against upstream
        // DrawTextOnCircle.java:50-81 (Alignment: START=0, CENTER=1, END=2; Placement: OUTSIDE=0, INSIDE=1).
        assertEquals(0, DrawTextOnCircle.Alignment.START.ordinal)
        assertEquals(1, DrawTextOnCircle.Alignment.CENTER.ordinal)
        assertEquals(2, DrawTextOnCircle.Alignment.END.ordinal)
        assertEquals(0, DrawTextOnCircle.Placement.OUTSIDE.ordinal)
        assertEquals(1, DrawTextOnCircle.Placement.INSIDE.ordinal)

        // Round-trip each enum value via emission + decode.
        for (a in DrawTextOnCircle.Alignment.entries) {
            for (p in DrawTextOnCircle.Placement.entries) {
                val bytes = document(width = 100, height = 100, contentDescription = "x") {
                    val tId = addText("y")
                    drawTextOnCircle(tId, 0f, 0f, 1f, startAngle = 0f, alignment = a, placement = p)
                }
                val op = DocumentReader.inflate(bytes).operations
                    .first { it is DrawTextOnCircle } as DrawTextOnCircle
                assertEquals(a, op.alignment, "Alignment $a should round-trip")
                assertEquals(p, op.placement, "Placement $p should round-trip")
            }
        }
    }

    @Test
    @IgnoreOnWasm
    fun drawTextOnCircle_matchesCorpusFixtureRegion_doubleAnchor_visibleSkip() {
        // Double-pin: op-57 DRAW_TEXT_ON_CIRCLE is corpus-ABSENT per assist's authoritative
        // 173-doc inflate scan (REM-113-followup-redo). This test stays as a marker — if the
        // corpus ever adds a DRAW_TEXT_ON_CIRCLE fixture, the lookup returns non-null and the
        // byte-match branch activates (triple-pin). The visibility-check test below is the
        // loud gate: it pins op-57 in the expected corpus-absence set, so a corpus shake-out
        // that adds op-57 will fail loudly and force this comment to be updated.
        val fixture = findCorpusFixtureWithOp(Operations.DRAW_TEXT_ON_CIRCLE, opSize = 27)
            ?: return
        val (bytes, opOffset) = fixture
        val fixtureRegion = bytes.copyOfRange(opOffset, opOffset + 27)

        // Decode operands.
        val textId = fixtureRegion.readIntBE(1)
        val cx = fixtureRegion.readFloatBE(5)
        val cy = fixtureRegion.readFloatBE(9)
        val radius = fixtureRegion.readFloatBE(13)
        val startAngle = fixtureRegion.readFloatBE(17)
        val warpOff = fixtureRegion.readFloatBE(21)
        val alignment = DrawTextOnCircle.Alignment.fromInt(fixtureRegion[25].toInt() and 0xFF)
        val placement = DrawTextOnCircle.Placement.fromInt(fixtureRegion[26].toInt() and 0xFF)

        // Rebuild via DSL with the same textId allocator state. Direct context construction so
        // we can `setNextId(textId)` to pin the id-pool before the addText reservation.
        val ctx = RemoteComposeContext(
            writer = RemoteComposeWriter(width = 400, height = 400, apiLevel = 6),
            profile = Profile.Baseline,
        )
        ctx.ids.setNextId(textId)
        val reservedTextId = ctx.addText("placeholder") // claims `textId`
        assertEquals(textId, reservedTextId, "id-pool aligned to corpus textId for byte-comparison")
        ctx.drawTextOnCircle(
            textId = reservedTextId,
            centerX = cx, centerY = cy, radius = radius,
            startAngle = startAngle, warpRadiusOffset = warpOff,
            alignment = alignment, placement = placement,
        )
        val dslBytes = ctx.encodeToByteArray()
        val dslOpOffset = findOpcode(dslBytes, Operations.DRAW_TEXT_ON_CIRCLE)
        val dslRegion = dslBytes.copyOfRange(dslOpOffset, dslOpOffset + 27)

        assertTrue(
            fixtureRegion.contentEquals(dslRegion),
            "DRAW_TEXT_ON_CIRCLE: DSL output ≠ corpus fixture region " +
                "(textId=$textId, cx=$cx, cy=$cy, r=$radius, sa=$startAngle, wo=$warpOff, " +
                "align=$alignment, place=$placement)",
        )
    }

    // ─── DRAW_BITMAP_INT ──────────────────────────────────────────────────────

    @Test
    fun drawBitmapInt_emitsByteFaithfulWire_andRoundTrips() {
        // Hand-computed: opcode 0x42 + 10 ints = 41 bytes.
        val imageId = 42
        val src = listOf(0, 0, 100, 100)
        val dst = listOf(10, 20, 110, 120)
        val cdId = 0
        val expected = byteArrayOf(0x42) +
            intBytesBE(imageId) +
            intBytesBE(src[0]) + intBytesBE(src[1]) + intBytesBE(src[2]) + intBytesBE(src[3]) +
            intBytesBE(dst[0]) + intBytesBE(dst[1]) + intBytesBE(dst[2]) + intBytesBE(dst[3]) +
            intBytesBE(cdId)
        assertEquals(41, expected.size, "DRAW_BITMAP_INT wire = opcode + 10 ints = 41 bytes")

        val dslBytes = document(width = 200, height = 200) {
            // pin imageId by allocating first. addBitmap returns id 42 (first plain allocation).
            val id = addBitmap(width = 100, height = 100, data = ByteArray(0))
            assertEquals(42, id)
            drawBitmapInt(
                imageId = id,
                srcLeft = src[0], srcTop = src[1], srcRight = src[2], srcBottom = src[3],
                dstLeft = dst[0], dstTop = dst[1], dstRight = dst[2], dstBottom = dst[3],
                contentDescriptionId = cdId,
            )
        }
        val opOffset = findOpcode(dslBytes, Operations.DRAW_BITMAP_INT)
        assertTrue(opOffset >= 0, "DSL must emit DRAW_BITMAP_INT op")
        val dslRegion = dslBytes.copyOfRange(opOffset, opOffset + expected.size)
        assertTrue(
            expected.contentEquals(dslRegion),
            "DSL DRAW_BITMAP_INT bytes diverge from hand-computed expected sequence",
        )

        // Round-trip parses back.
        val op = DocumentReader.inflate(dslBytes).operations
            .first { it is DrawBitmapInt } as DrawBitmapInt
        assertEquals(imageId, op.imageId)
        assertEquals(src[0], op.srcLeft); assertEquals(src[3], op.srcBottom)
        assertEquals(dst[0], op.dstLeft); assertEquals(dst[3], op.dstBottom)
        assertEquals(cdId, op.cdId)
    }

    @Test
    @IgnoreOnWasm
    fun drawBitmapInt_matchesCorpusFixtureRegion_doubleAnchor_visibleSkip() {
        // Double-pin: op-66 DRAW_BITMAP_INT is corpus-ABSENT per assist's authoritative
        // 173-doc inflate scan. Same harness as drawTextOnCircle_matches... above; the
        // visibility-check below pins op-66 in the expected corpus-absence set.
        val fixture = findCorpusFixtureWithOp(Operations.DRAW_BITMAP_INT, opSize = 41)
            ?: return
        val (bytes, opOffset) = fixture
        val fixtureRegion = bytes.copyOfRange(opOffset, opOffset + 41)

        // Decode 10 ints.
        val imageId = fixtureRegion.readIntBE(1)
        val srcLeft = fixtureRegion.readIntBE(5)
        val srcTop = fixtureRegion.readIntBE(9)
        val srcRight = fixtureRegion.readIntBE(13)
        val srcBottom = fixtureRegion.readIntBE(17)
        val dstLeft = fixtureRegion.readIntBE(21)
        val dstTop = fixtureRegion.readIntBE(25)
        val dstRight = fixtureRegion.readIntBE(29)
        val dstBottom = fixtureRegion.readIntBE(33)
        val cdId = fixtureRegion.readIntBE(37)

        // Rebuild via DSL. Pin id-pool to imageId so addBitmap reserves the right id.
        val ctx = RemoteComposeContext(
            writer = RemoteComposeWriter(width = 400, height = 400, apiLevel = 6),
            profile = Profile.Baseline,
        )
        ctx.ids.setNextId(imageId)
        val reservedImageId = ctx.addBitmap(width = 64, height = 64, data = ByteArray(0))
        assertEquals(imageId, reservedImageId, "id-pool aligned to corpus imageId for byte-comparison")
        ctx.drawBitmapInt(
            imageId = reservedImageId,
            srcLeft = srcLeft, srcTop = srcTop, srcRight = srcRight, srcBottom = srcBottom,
            dstLeft = dstLeft, dstTop = dstTop, dstRight = dstRight, dstBottom = dstBottom,
            contentDescriptionId = cdId,
        )
        val dslBytes = ctx.encodeToByteArray()
        val dslOpOffset = findOpcode(dslBytes, Operations.DRAW_BITMAP_INT)
        val dslRegion = dslBytes.copyOfRange(dslOpOffset, dslOpOffset + 41)

        assertTrue(
            fixtureRegion.contentEquals(dslRegion),
            "DRAW_BITMAP_INT: DSL output ≠ corpus fixture region " +
                "(imageId=$imageId, src=($srcLeft,$srcTop,$srcRight,$srcBottom), " +
                "dst=($dstLeft,$dstTop,$dstRight,$dstBottom), cdId=$cdId)",
        )
    }

    // ─── corpus scanning helper ────────────────────────────────────────────────

    /**
     * Scan all corpus fixtures (`corpus/` subdir) for a byte at value [opcode]. Validates the
     * match by decoding the candidate fixture and checking that an op of that opcode actually
     * exists in the decoded op stream (filters out coincidental opcode-byte matches embedded
     * in other ops' operand bytes).
     *
     * Returns `(fixtureBytes, opcodeByteOffset)` of the first valid match across the corpus,
     * or `null` if no fixture contains a decodable instance of the op. The caller decides
     * whether `null` is acceptable (double-pin via `?: return`) or a hard fail — the loud
     * gate is [advancedDrawShapes_fixtureCoverage_visibilityCheck], which does its own
     * order-independent corpus scan.
     */
    private fun findCorpusFixtureWithOp(opcode: Int, opSize: Int): Pair<ByteArray, Int>? {
        val opcodeByte = opcode.toByte()
        for (name in RcCorpus.corpusNames()) {
            val bytes = try {
                RcCorpus.readFixture("corpus/$name")
            } catch (_: Throwable) {
                continue
            }
            // Decode the fixture; any decodable instance of `opcode` confirms a real op
            // somewhere in the stream (uses the same OperationReader that the player uses).
            val decoded = try {
                DocumentReader.inflate(bytes).operations
            } catch (_: Throwable) {
                continue
            }
            val hasOp = decoded.any { it.opcode == opcode }
            if (!hasOp) continue
            // Scan for the opcode byte; for each candidate, sanity-check that the surrounding
            // window is at least opSize bytes (so we can extract without OOB).
            var from = 0
            while (true) {
                val off = findOpcodeFromOffset(bytes, opcodeByte, from)
                if (off < 0) break
                if (off + opSize <= bytes.size) {
                    return bytes to off
                }
                from = off + 1
            }
        }
        return null
    }

    private fun findOpcodeFromOffset(bytes: ByteArray, opcodeByte: Byte, from: Int): Int {
        for (i in from until bytes.size) if (bytes[i] == opcodeByte) return i
        return -1
    }

    // ─── byte-write helpers ────────────────────────────────────────────────────

    private fun intBytesBE(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(),
        (v ushr 8).toByte(), v.toByte(),
    )

    private fun floatBytesBE(v: Float): ByteArray = intBytesBE(v.toRawBits())

    // ─── visible-skip degradation pin (order-INDEPENDENT, own-scan) ───────────

    @Test
    @IgnoreOnWasm
    fun advancedDrawShapes_fixtureCoverage_visibilityCheck() {
        // Order-INDEPENDENT scan: this test does its own corpus inflation; no shared mutable
        // state with the other tests, no empty-escape, no alphabetical-order assumption.
        //
        // Pins the EXPECTED corpus-absence set against assist's authoritative 173-doc inflate
        // probe (REM-113-followup-redo 2026-06-28):
        //  - DRAW_ROUND_RECT  (51): corpus-PRESENT → triple-pin (used as CONTROL-POSITIVE here)
        //  - DRAW_TEXT_ON_CIRCLE (57): corpus-ABSENT → double-pin
        //  - DRAW_BITMAP_INT  (66): corpus-ABSENT → double-pin
        //
        // The control-positive matters: if DocumentReader.inflate ever silently fails for the
        // whole corpus, the scan would report all 3 ops absent — and only the control-positive
        // gives us a way to distinguish "corpus genuinely lacks the op" from "scan is broken".
        val checked = setOf(
            Operations.DRAW_ROUND_RECT,
            Operations.DRAW_TEXT_ON_CIRCLE,
            Operations.DRAW_BITMAP_INT,
        )
        val present = mutableSetOf<Int>()
        for (name in RcCorpus.corpusNames()) {
            val bytes = try {
                RcCorpus.readFixture("corpus/$name")
            } catch (_: Throwable) { continue }
            val decoded = try {
                DocumentReader.inflate(bytes).operations
            } catch (_: Throwable) { continue }
            for (op in decoded) if (op.opcode in checked) present.add(op.opcode)
            if (present.size == checked.size) break
        }
        // Control-positive: op-51 MUST be found. If it's absent here, the inflate path is
        // broken (or the corpus actually lost DRAW_ROUND_RECT, which would itself be news).
        assertTrue(
            Operations.DRAW_ROUND_RECT in present,
            "Control-positive failed: DRAW_ROUND_RECT (51) not found in any corpus fixture " +
                "via DocumentReader.inflate. Either the inflate path regressed, or the " +
                "corpus has changed substantially — investigate before trusting any " +
                "double-pin assertion here.",
        )
        val expectedAbsent = setOf(
            Operations.DRAW_TEXT_ON_CIRCLE,
            Operations.DRAW_BITMAP_INT,
        )
        val actualAbsent = checked - present
        assertEquals(
            expectedAbsent, actualAbsent,
            "REM-113 corpus-coverage snapshot — these ops are intentionally double-pinned " +
                "per assist's authoritative scan (no corpus fixture in rc-corpus/corpus/). " +
                "A surprise here = the corpus changed or the audit was wrong. Update the " +
                "expectedAbsent set AND the class-doc + double-pin test comments to promote " +
                "(absent → present) or demote (present → absent) the affected op.",
        )
    }
}
