package com.tneff.kmpremotecompose.conformance

import com.tneff.kmpremotecompose.remote.core.debug.OpSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifiziert die Harness-**Mechanik** ohne echten Reader/Writer und ohne Korpus, indem [RcCodec]
 * mit Fakes bedient wird. Beweist: Round-Trip-Grün, Divergenz-Erkennung + Op/Feld-Reporting,
 * Exceptions werden pro Fixture gefangen, Korpus-Lauf sammelt (nicht fail-fast).
 */
class ConformanceEngineTest {

    private fun readback(reEncoded: ByteArray, spans: List<OpSpan> = emptyList()) =
        object : RcReadback {
            override fun reEncode(): ByteArray = reEncoded
            override fun opSpans(): List<OpSpan> = spans
        }

    /** Treuer Codec: re-encode == input → Byte-Equality muss matchen. */
    private val identity = RcCodec { bytes -> readback(bytes.copyOf()) }

    @Test
    fun roundTrip_identityCodec_matches() {
        val input = byteArrayOf(0, 1, 2, 3, 4)
        val r = ConformanceEngine.roundTrip("F1", input, identity)
        assertTrue(r.ok)
        assertTrue(r.matched)
        assertNull(r.firstDivergence)
        assertEquals("", r.report)
    }

    @Test
    fun writerByteEquality_brokenCodec_reportsOpAndOffset() {
        val golden = byteArrayOf(0, 1, 2, 3, 4)
        val spans = listOf(
            OpSpan(opcode = 0, name = "HEADER", byteStart = 0, byteEnd = 2, fields = "HEADER"),
            OpSpan(opcode = 42, name = "DRAW_RECT", byteStart = 2, byteEnd = 5, fields = "DRAW_RECT l,t,r"),
        )
        // Flip Byte an Offset 3 (innerhalb der zweiten Op-Spanne).
        val broken = RcCodec { bytes ->
            val out = bytes.copyOf()
            out[3] = (out[3] + 1).toByte()
            readback(out, spans)
        }
        val r = ConformanceEngine.writerByteEquality("F2", golden, broken)
        assertFalse(r.ok)
        assertEquals(3, r.firstDivergence)
        // Report nennt die Op (opcode 42) und das Feld.
        assertTrue(r.report.contains("opcode=42"), "report war: ${r.report}")
        assertTrue(r.report.contains("DRAW_RECT"), "report war: ${r.report}")
    }

    @Test
    fun decodeThrows_capturedAsError_notPropagated() {
        val throwing = RcCodec { error("unbekanntes Opcode 0x99") }
        val r = ConformanceEngine.writerByteEquality("F3", byteArrayOf(9), throwing)
        assertFalse(r.ok)
        val err = r.error
        assertTrue(err != null && err.contains("decode"))
    }

    @Test
    fun runCorpus_collectsAll_notFailFast() {
        val golden = byteArrayOf(0, 1, 2)
        val mixed = RcCodec { bytes ->
            // "bad.rc" wird absichtlich kaputt re-encodiert, der Rest treu.
            readback(bytes.copyOf())
        }
        val brokenForOne = RcCodec { bytes ->
            val out = bytes.copyOf(); out[0] = 0x7F; readback(out)
        }
        // Zwei treue + bewusst einen kaputten Lauf über getrennte Codecs zu mischen ist unhandlich;
        // stattdessen ein Korpus mit einem treuen Codec, aber einem Fixture das nach Re-Encode abweicht:
        val fixtures = listOf(
            NamedBytes("ok1", golden),
            NamedBytes("ok2", byteArrayOf(5, 6)),
        )
        val report = ConformanceEngine.runCorpus(fixtures, mixed)
        assertEquals(2, report.total)
        assertEquals(2, report.passed)
        assertTrue(report.allPassed)

        // Und ein Lauf, in dem alle abweichen → alle gesammelt, kein Abbruch.
        val badReport = ConformanceEngine.runCorpus(fixtures, brokenForOne)
        assertEquals(2, badReport.total)
        assertEquals(0, badReport.passed)
        assertEquals(2, badReport.failures.size)
        assertTrue(badReport.summary().contains("ok1"))
    }

    @Test
    fun identicalReport_whenNoSpansButMatch() {
        assertEquals("IDENTISCH (3 Bytes)", RcDiffReporter.describe(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
    }
}
