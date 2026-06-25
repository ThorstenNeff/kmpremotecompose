package com.tneff.kmpremotecompose.conformance

import kotlin.test.Test

/**
 * REM-7 — **P0-Conformance-Tests** (Tier B = Byte-Equality-Gate), Tester-Part (test-2).
 *
 * **Schritt 1:** F1 **Decode→Re-Encode-Round-Trip gegen das echte Golden**
 * `procedure_simple1.rc` über die [ConformanceEngine] + den realen [RcDocumentCodec].
 * **Kein Builder** — die builder-basierte Tier-B (`buildProcedureSimple1()` == golden) braucht den
 * deterministischen Creation-Pfad und gehört ins spätere Creation-Milestone (`remote-creation`).
 *
 * P0-Reihenfolge (test-1-Vertrag): **F1 → F2 → F4 → F5**. Hier zunächst nur F1.
 *
 * Ownership: Fixture/`MANIFEST.tsv` = test-1 (`rc-corpus/`); Codec/Engine + portable
 * [RcCorpus.fixtureRoot] = dev-2; Assertions + Test-Body = test-2.
 *
 * Korpus-Wurzel wird von [RcCorpus.fixtureRoot] selbst aufgelöst (build-zeit-absolut, Host + iOS-Sim)
 * — kein `rootOverride`-Stub mehr nötig.
 */
class RcConformanceP0Test {

    @Test
    fun roundTrip_procedure_simple1() {
        val golden = RcCorpus.readFixture("procedure_simple1.rc") // 61 B, F1
        val result = ConformanceEngine.roundTrip("F1", golden, RcDocumentCodec)
        assertConformancePasses(result)
    }
}
