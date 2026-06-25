package com.tneff.kmpremotecompose.conformance

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * REM-7 — **P0-Conformance-Tests** (Tier B = Byte-Equality-Gate), Tester-Part (test-2).
 *
 * **Schritt 1 (jetzt):** F1 **Decode→Re-Encode-Round-Trip gegen das echte Golden**
 * `procedure_simple1.rc` über die [ConformanceEngine] + den realen [RcDocumentCodec].
 * **Kein Builder** — die builder-basierte Tier-B (`buildProcedureSimple1()` == golden) braucht den
 * deterministischen Creation-Pfad und gehört ins spätere Creation-Milestone (`remote-creation`).
 *
 * P0-Reihenfolge (test-1-Vertrag): **F1 → F2 → F4 → F5**. Hier zunächst nur F1.
 *
 * Ownership: Fixture/`MANIFEST.tsv` = test-1 (`rc-corpus/`); Codec/Engine = dev-2; Assertions +
 * Test-Body = test-2.
 *
 * Hinweis (test-first): F1 ist zunächst **rot** erwartet, solange `ROOT_CONTENT_DESCRIPTION`
 * (Opcode 103, dev-1/REM-12) bzw. der Flat-Header-Minor-Version-Re-Encode (REM-7-§3, an PO gemeldet)
 * noch offen sind. Der Engine-Pfad fängt den Decode-Fehler und meldet ihn lesbar.
 */
class RcConformanceP0Test {

    @BeforeTest
    fun setUp() {
        // Provisorische Host-Test-Root-Auflösung (REM-7-offen, siehe README).
        RcCorpus.rootOverride = resolveCorpusRoot()
    }

    @AfterTest
    fun tearDown() {
        RcCorpus.rootOverride = null
    }

    @Test
    fun roundTrip_procedure_simple1() {
        val golden = RcCorpus.readFixture("procedure_simple1.rc") // 61 B, F1
        val result = ConformanceEngine.roundTrip("F1", golden, RcDocumentCodec)
        assertConformancePasses(result)
    }
}
