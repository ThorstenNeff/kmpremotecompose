package com.tneff.kmpremotecompose.conformance

/**
 * REM-7 — Conformance-Harness **Mechanik** (dev-2).
 *
 * Ownership-Schnitt (PO/test-1, 2026-06-25):
 *  - **dev-2 (hier):** Loader-Mechanik ([RcCorpus]/[RcCorpusReader]), Decode→Re-Encode-Loop
 *    ([ConformanceEngine]), Byte-Diff + RcToString-Diff-Plumbing ([ByteDiff]/[RcDiffReporter]).
 *  - **test-1/test-2:** Fixture-Daten, `MANIFEST.tsv`, der Assertion-Helfer
 *    `assertRcBytesEqual(expected, actual, fixtureId)` und die Test-Bodies
 *    (`RcConformanceP0Test` etc.). Handoff über den PO.
 *
 * Diese Datei ist der **provisorische Contract-Seam** zur Reader/Writer-API aus REM-2/REM-3.
 * Sobald diese landen, wird [RcCodec] an
 *   - `remote.core.document.RemoteComposeReader` (decode/initFromBuffer) und
 *   - `remote.creation.RemoteComposeWriter.encodeToByteArray()` (re-encode)
 * gebunden — die Engine/Diff-Mechanik darüber bleibt unverändert.
 */

/**
 * Eine pro Operation aufgelöste Byte-Spanne — Spiegel des in TechSpec §3.4 geforderten
 * Reader-Op-Level-Debug-Dumps `(opcode, byteStart, byteEnd, fields)`.
 *
 * [byteStart] inklusiv, [byteEnd] exklusiv (Halb-offenes Intervall, passend zu `WireBuffer.byteIndex`
 * vor/nach dem Op-Read).
 */
data class OpSpan(
    val opcode: Int,
    val byteStart: Int,
    val byteEnd: Int,
    val fields: String,
) {
    init {
        require(byteStart in 0..byteEnd) { "OpSpan ungültig: byteStart=$byteStart byteEnd=$byteEnd" }
    }

    /** true, wenn [offset] in diese Op-Spanne fällt. */
    fun contains(offset: Int): Boolean = offset in byteStart until byteEnd
}

/**
 * Ergebnis eines Decodierens: das, was die Mechanik vom Reader/Writer braucht.
 * Provisorisch — wird von der echten Reader-Document-Facade implementiert.
 */
interface RcReadback {
    /** Re-Encode der geparsten Ops zurück nach Bytes (≙ `RemoteComposeWriter.encodeToByteArray()`). */
    fun reEncode(): ByteArray

    /** Op-Level-Dump des geparsten Dokuments (≙ TechSpec §3.4) für lesbares Diff-Reporting. */
    fun opSpans(): List<OpSpan>
}

/**
 * Decodiert `.rc`-Bytes → [RcReadback] (≙ `RemoteComposeReader.initFromBuffer(bytes)`).
 *
 * SAM, damit Tester/Bindings die echte Reader-Facade ohne Boilerplate andocken können.
 * Wirft bei nicht-parsebaren Bytes (z. B. unbekanntes Opcode) — die Engine fängt das pro Fixture.
 */
fun interface RcCodec {
    fun decode(bytes: ByteArray): RcReadback
}
