package com.tneff.kmpremotecompose.conformance

import kotlin.test.Test

/**
 * REM-7 — **Decode-only-Orakel** (F7), Tester-Part (test-2).
 *
 * F7 `screenshottest.rc` (218 B, RemoteBox + RemoteText + mutable String, API-7) hat **keinen**
 * committeten Generator → **kein Byte-Equality-Ziel**. Geprüft wird nur, dass der reale
 * [RcDocumentCodec] das Dokument **fehlerfrei dekodiert** (≥1 Op) — der „Reader bricht nicht am
 * echten Dokument"-Beweis. Op-Gaps werden wie bei den Byte-Equality-Tests als `unknown opcode N`
 * + Offset gemeldet (an PO routen, kein Patch durch test-2).
 *
 * Ownership: Fixture/`MANIFEST.tsv` = test-1 (`rc-corpus/`); Codec = dev-2; Assertions + Body = test-2.
 */
@IgnoreOnWasm
class RcConformanceDecodeOnlyTest {

    @Test
    fun decode_screenshottest() {
        val golden = RcCorpus.readFixture("screenshottest.rc") // 218 B, F7 — decode-only
        assertDecodesCleanly(golden, "F7")
    }
}
