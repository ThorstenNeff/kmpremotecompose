package com.tneff.kmpremotecompose.conformance

import com.tneff.kmpremotecompose.remote.core.debug.OpSpan

/**
 * REM-7 — Conformance-Harness **Mechanik** (dev-2).
 *
 * Ownership-Schnitt (PO/test-1, 2026-06-25):
 *  - **dev-2 (hier):** Loader-Mechanik ([RcCorpus]/[RcCorpusReader]), Decode→Re-Encode-Loop
 *    ([ConformanceEngine]), Byte-Diff + RcToString-Diff-Plumbing ([ByteDiff]/[RcDiffReporter]),
 *    und die Bindung an die echte Reader/Writer-Facade ([RcDocumentCodec]).
 *  - **test-1/test-2:** Fixture-Daten, `MANIFEST.tsv`, der Assertion-Helfer
 *    `assertRcBytesEqual(expected, actual, fixtureId)` und die Test-Bodies
 *    (`RcConformanceP0Test` etc.). Handoff über den PO.
 *
 * [RcCodec] ist an die echte Facade gebunden ([RcDocumentCodec]) — `DocumentReader` zum Decodieren,
 * sequentielles `Operation.write()` zum byte-treuen Re-Encode. Die Op-Spannen kommen als die
 * **REM-3-`OpSpan`** (`remote.core.debug.OpSpan`, mit `name`) aus `DocumentReader.inflateWithTrace`;
 * der frühere conformance-eigene OpSpan-Duplikat ist entfernt (assist-Review).
 */

/**
 * Ergebnis eines Decodierens: das, was die Mechanik vom Reader/Writer braucht.
 */
interface RcReadback {
    /** Re-Encode der geparsten Ops zurück nach Bytes (byte-treu via `Operation.write()`). */
    fun reEncode(): ByteArray

    /** Op-Level-Dump des geparsten Dokuments (≙ TechSpec §3.4) für lesbares Diff-Reporting. */
    fun opSpans(): List<OpSpan>
}

/**
 * Decodiert `.rc`-Bytes → [RcReadback]. SAM, damit Tester/Bindings andocken können.
 * Wirft bei nicht-parsebaren Bytes (z. B. unbekanntes Opcode) — die Engine fängt das pro Fixture.
 */
fun interface RcCodec {
    fun decode(bytes: ByteArray): RcReadback
}
