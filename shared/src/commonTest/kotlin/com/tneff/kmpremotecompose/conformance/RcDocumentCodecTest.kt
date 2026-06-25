package com.tneff.kmpremotecompose.conformance

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeWriter
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end binding test: the real [RcDocumentCodec] (REM-3 `DocumentReader` + faithful
 * `Operation.write()` re-encode) driven through the [ConformanceEngine].
 *
 * This is the structural unblock for the conformance pipeline — the first real decode→re-encode
 * byte-equality on an actual `.rc` document (here a writer-produced, header-only API-7 document;
 * the full corpus fixtures follow once op-group readers are registered via REM-6 `registerBuiltins`).
 */
class RcDocumentCodecTest {

    private fun headerOnlyRc(): ByteArray =
        RemoteComposeWriter(width = 320, height = 470, profiles = Operations.PROFILE_ANDROIDX, contentDescription = "")
            .encodeToByteArray()

    @Test
    fun headerOnlyDocument_decodeReEncode_isByteIdentical() {
        val golden = headerOnlyRc()
        val r = ConformanceEngine.writerByteEquality("header-only", golden, RcDocumentCodec)
        assertTrue(r.ok, r.report)
        assertNull(r.firstDivergence)
        assertEquals(golden.size, r.actualSize)
    }

    @Test
    fun decode_exposesRem3OpSpansWithName() {
        val spans = RcDocumentCodec.decode(headerOnlyRc()).opSpans()
        assertEquals(1, spans.size)
        assertEquals("HEADER", spans[0].name) // REM-3 OpSpan carries the name — duplicate dropped
        assertEquals(0, spans[0].byteStart)
    }

    @Test
    fun unknownOpcode_capturedAsErrorNotPropagated() {
        // Append orphan opcode 4 (LOAD_BITMAP) — the real reader rejects it; the engine captures it.
        val bad = headerOnlyRc() + byteArrayOf(0x04)
        val r = ConformanceEngine.writerByteEquality("bad", bad, RcDocumentCodec)
        assertFalse(r.ok)
        val err = r.error
        assertTrue(err != null && err.contains("decode"), "expected decode error, got: $err")
    }
}
