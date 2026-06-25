package com.tneff.kmpremotecompose.conformance

import kotlin.test.Test

/**
 * REM-7 — **P0-Conformance-Tests** (Tier B = Byte-Equality-Gate), Tester-Part (test-2).
 *
 * **Schritt 1 (Decode→Re-Encode-Round-Trip gegen das echte Golden):** P0-Block in test-1-Reihenfolge
 * **F1 → F2 → F4 → F5** über die [ConformanceEngine] + den realen [RcDocumentCodec].
 * **Kein Builder** — die builder-basierte Tier-B (`buildFx()` == golden) braucht den deterministischen
 * Creation-Pfad und gehört ins spätere Creation-Milestone (`remote-creation`).
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

    @Test
    fun roundTrip_procedure_simple2() {
        val golden = RcCorpus.readFixture("procedure_simple2.rc") // 82 B, F2 — RootContentBehavior + DrawOval
        val result = ConformanceEngine.roundTrip("F2", golden, RcDocumentCodec)
        assertConformancePasses(result)
    }

    @Test
    fun roundTrip_c_box() {
        val golden = RcCorpus.readFixture("c_box.rc") // 128 B, F4 — box size + background (DSL)
        val result = ConformanceEngine.roundTrip("F4", golden, RcDocumentCodec)
        assertConformancePasses(result)
    }

    @Test
    fun roundTrip_c_modifier_width() {
        val golden = RcCorpus.readFixture("c_modifier_width.rc") // 128 B, F5 — width≠height + background (DSL)
        val result = ConformanceEngine.roundTrip("F5", golden, RcDocumentCodec)
        assertConformancePasses(result)
    }
}
