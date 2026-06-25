package com.tneff.kmpremotecompose.conformance

import com.tneff.kmpremotecompose.remote.core.debug.OpSpan
import com.tneff.kmpremotecompose.remote.core.document.DocumentReader
import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.wire.WireBuffer

/**
 * REM-7 — the real [RcCodec] binding to the REM-3 reader/writer facade.
 *
 * - **decode:** `DocumentReader.inflateWithTrace` → the document plus its per-op [OpSpan] byte map.
 * - **re-encode:** sequential `Operation.write()` of every parsed op (header included) into a fresh
 *   [WireBuffer]. This is byte-faithful: each op reproduces exactly what it parsed.
 *
 * Why not `RemoteComposeWriter.encodeToByteArray()` for re-encode: that facade *re-derives* the
 * header from width/height/profiles and always stamps the current (map-form, API-7) version — it is
 * the creation/DSL path, not a faithful reproduction of an already-parsed document (e.g. a flat
 * API-6 header would not round-trip). Conformance re-encode therefore replays the parsed ops' own
 * `write()`.
 */
object RcDocumentCodec : RcCodec {

    override fun decode(bytes: ByteArray): RcReadback {
        val (doc, spans) = DocumentReader.inflateWithTrace(bytes)
        return DocumentReadback(doc, spans)
    }

    private class DocumentReadback(
        private val document: RemoteComposeDocument,
        private val spans: List<OpSpan>,
    ) : RcReadback {
        override fun reEncode(): ByteArray {
            val buffer = WireBuffer()
            for (op in document.operations) op.write(buffer)
            return buffer.toByteArray()
        }

        override fun opSpans(): List<OpSpan> = spans
    }
}
