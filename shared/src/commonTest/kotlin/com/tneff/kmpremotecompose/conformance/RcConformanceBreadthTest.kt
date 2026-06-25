package com.tneff.kmpremotecompose.conformance

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * REM-7 — **P2-Breadth-Sweep** (Tester-Part, test-2).
 *
 * Lädt + dekodiert **alle** `.rc` unter `rc-corpus/corpus/` (die 173) über [ConformanceEngine.runCorpus]
 * und **sammelt** die Ergebnisse pro Doc — **nicht fail-fast**. Zweck: den Op-Gap-/Divergenz-Schwanz
 * **enumerieren**, damit der PO gebündelt + priorisiert an die Devs routen kann.
 *
 * Das ist ein **Sweep, kein hartes Gate**: die Assertion schlägt zunächst fehl (viele Ops fehlen noch),
 * und die Fehlermeldung trägt den **vollständigen, maschinen-parsebaren Per-Doc-Dump**
 * (`DECODE_ERROR | <doc> | <err>` bzw. `DIVERGENCE | <doc> | @offset | <RcDiff>`), aus dem test-2 die
 * aggregierte „Opcode → Anzahl betroffener Docs"-Liste destilliert. Geht grün, sobald alle Ops gebaut sind.
 *
 * Ownership: Korpus = test-1 (`rc-corpus/corpus/`); Engine/Codec = dev-2; Body = test-2. Kein Patch von test-2.
 */
class RcConformanceBreadthTest {

    @Test
    fun corpus_sweep_loadAndDecode() {
        val names = RcCorpus.corpusNames() // alle .rc unter rc-corpus/corpus/
        assertTrue(names.isNotEmpty(), "rc-corpus/corpus/ ist leer — test-1 muss die Korpus-.rc bereitstellen")

        val report = ConformanceEngine.runCorpus(
            names.map { NamedBytes(it, RcCorpus.readFixture("corpus/$it")) },
            RcDocumentCodec,
        )

        val dump = buildString {
            append("P2-SWEEP total=").append(report.total)
                .append(" passed=").append(report.passed)
                .append(" failed=").append(report.failures.size).append('\n')
            report.failures.forEach { r ->
                if (r.error != null) {
                    append("DECODE_ERROR | ").append(r.fixtureId).append(" | ").append(r.error).append('\n')
                } else {
                    append("DIVERGENCE | ").append(r.fixtureId)
                        .append(" | @").append(r.firstDivergence)
                        .append(" exp=").append(r.expectedSize).append(" act=").append(r.actualSize)
                        .append(" | ").append(r.report.replace('\n', ' ')).append('\n')
                }
            }
        }
        // Immer (grün wie rot) die Summenzeile ausgeben, damit der ok-Stand auch bei
        // Vollabdeckung (kein Failure-Dump) als Evidenz sichtbar ist.
        println("P2-SWEEP-RESULT total=${report.total} passed=${report.passed} failed=${report.failures.size}")

        // Sweep, kein Gate: sammelt ALLES (runCorpus ist nicht fail-fast); die Message trägt den
        // vollständigen Per-Doc-Dump für die Aggregation. Grün, sobald alle Ops gebaut sind.
        assertTrue(report.allPassed, dump)
    }
}
