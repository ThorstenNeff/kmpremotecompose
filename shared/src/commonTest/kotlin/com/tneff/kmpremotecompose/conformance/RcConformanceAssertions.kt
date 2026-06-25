package com.tneff.kmpremotecompose.conformance

import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * REM-7 — **Tester-eigene** Assertion-Helfer + Host-Test-Setup (test-2).
 *
 * Ownership-Schnitt (siehe `conformance/README.md` / [ConformanceContract]): die Decode→Re-Encode-
 * Mechanik ([ConformanceEngine]), der Loader ([RcCorpus]), der Codec ([RcDocumentCodec]) und das
 * Diff-Plumbing ([RcDiffReporter]/[ByteDiff]) gehören dev-2. **Hier** liegen nur die Assertions.
 */

/**
 * Dünner Byte-Gleichheits-Helfer (Signatur von test-1): vergleicht zwei `.rc`-Byte-Arrays.
 * Bei Gleichheit: kein Fehler. Bei Divergenz: Fehlermeldung mit Größen, erstem Divergenz-Offset und
 * Hex-Fenster ([RcDiffReporter]; Op/Feld-Spannen optional, hier reine Byte-Schicht).
 */
fun assertRcBytesEqual(expected: ByteArray, actual: ByteArray, fixtureId: String) {
    if (ByteDiff.equal(expected, actual)) return
    fail(
        "[$fixtureId] .rc-Bytes weichen ab " +
            "(expected=${expected.size} B, actual=${actual.size} B):\n" +
            RcDiffReporter.describe(expected, actual),
    )
}

/**
 * Asserts, dass ein [ByteEqualityResult] der [ConformanceEngine] grün ist. Deckt **beide** Rot-Fälle
 * ab und legt den Grund direkt in die Test-Message:
 *  - Decode/Re-Encode-Exception (z. B. unbekanntes Opcode) → `result.error`,
 *  - Byte-Divergenz → `result.report` (RcToString-Diff: Offset → Op/Feld + Hex-Fenster).
 */
fun assertConformancePasses(result: ByteEqualityResult) {
    assertTrue(
        result.ok,
        buildString {
            append('[').append(result.fixtureId).append("] Conformance FEHLGESCHLAGEN — ")
            if (result.error != null) {
                append("Decode/Re-Encode-Fehler: ").append(result.error)
            } else {
                append("Byte-Divergenz (expected=").append(result.expectedSize)
                    .append(" actual=").append(result.actualSize)
                    .append(" @").append(result.firstDivergence).append(')')
            }
            if (result.report.isNotEmpty() && result.report != result.error) {
                append('\n').append(result.report)
            }
        },
    )
}

/**
 * **Decode-only**-Assertion (F7-Orakel): das Dokument muss **fehlerfrei dekodieren** und ≥1 Op
 * liefern. **KEIN** Byte-Equality — es gibt keinen committeten Generator, also kein Golden-Ziel.
 * Reiner „Reader bricht nicht am echten Dokument"-Beweis. Bei einem Op-Gap wirft der Codec; die
 * Meldung trägt dann das unbekannte Opcode + den Byte-Offset (wie bei den Byte-Equality-Tests).
 */
fun assertDecodesCleanly(golden: ByteArray, fixtureId: String) {
    val spans = try {
        RcDocumentCodec.decode(golden).opSpans()
    } catch (t: Throwable) {
        fail("[$fixtureId] decode FEHLGESCHLAGEN: ${t.message ?: t::class.simpleName}")
    }
    assertTrue(spans.isNotEmpty(), "[$fixtureId] dekodiert, aber 0 Ops")
}
