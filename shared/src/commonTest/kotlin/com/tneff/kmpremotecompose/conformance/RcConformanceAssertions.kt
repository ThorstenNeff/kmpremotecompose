package com.tneff.kmpremotecompose.conformance

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * REM-7 — **Tester-eigene** Assertion-Helfer + Host-Test-Setup (test-2).
 *
 * Ownership-Schnitt (siehe `conformance/README.md` / [ConformanceContract]): die Decode→Re-Encode-
 * Mechanik ([ConformanceEngine]), der Loader ([RcCorpus]), der Codec ([RcDocumentCodec]) und das
 * Diff-Plumbing ([RcDiffReporter]/[ByteDiff]) gehören dev-2. **Hier** liegen nur die Assertions und
 * die (provisorische) Auflösung der Korpus-Wurzel für Host-Tests.
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
 * **PROVISORISCHE** Host-Test-Auflösung der Korpus-Wurzel.
 *
 * Die plattformspezifische [RcCorpus] `fixtureRoot()` (Android-Assets / iOS `NSBundle`) ist
 * REM-7-offen (siehe `conformance/README.md`). Bis dahin lokalisiert dieser Helfer das
 * `rc-corpus/`-Verzeichnis relativ zum Test-Arbeitsverzeichnis (Gradle-Modul `shared/` bzw.
 * Repo-Wurzel) und liefert die Wurzel, die [RcCorpus.rootOverride] erwartet (Verzeichnis, das
 * `rc-corpus/` enthält). Wirft mit klarer Meldung, wenn nichts gefunden wird — kein stiller Fehlpfad.
 */
fun resolveCorpusRoot(): Path {
    val fs = FileSystem.SYSTEM
    val candidates = listOf(
        "src/commonTest/resources",
        "shared/src/commonTest/resources",
        "../shared/src/commonTest/resources",
    )
    for (c in candidates) {
        val root = c.toPath()
        if (fs.exists(root / RcCorpus.DIR / "procedure_simple1.rc")) return root
    }
    error(
        "rc-corpus nicht auffindbar (geprüft relativ zum CWD: $candidates). " +
            "Provisorische Host-Test-Root-Auflösung; plattform-portable Auflösung ist REM-7-offen.",
    )
}
