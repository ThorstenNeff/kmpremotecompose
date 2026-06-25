# Conformance-Harness — Mechanik (REM-7, dev-2)

Diese Package liefert die **Mechanik** der Conformance-Harness. **Assertions, Fixture-Daten,
`MANIFEST.tsv` und die Test-Bodies gehören den Testern (test-1/test-2).** Handoff über den PO.

## Was dev-2 besitzt (hier enthalten)
| Datei | Rolle |
|---|---|
| `ConformanceContract.kt` | `RcCodec` (decode) + `RcReadback` (reEncode + opSpans). opSpans liefert die **REM-3-`OpSpan`** (`remote.core.debug.OpSpan`, mit `name`) — eigener Duplikat entfernt. |
| `RcDocumentCodec.kt` | **Reale Bindung** an die REM-3-Facade: `DocumentReader.inflateWithTrace` (decode) + sequentielles `Operation.write()` (byte-treuer Re-Encode; NICHT `RemoteComposeWriter`, das den Header re-derivt). |
| `ByteDiff.kt` | Reiner Byte-Vergleich: `firstDivergence`, `equal`, Hex-Helfer. |
| `RcDiffReporter.kt` | RcToString-Diff-Plumbing: Offset → Op/Feld über REM-3-`OpSpan` (inkl. `name`) + Hex-Fenster. |
| `FixtureManifest.kt` | `FixtureEntry` (Signatur von test-1) + `ManifestParser` (TSV, `#`=Kommentar). |
| `RcCorpus.kt` | `RcCorpus` (Contract von test-1) + `RcCorpusReader` (okio, gegen `FakeFileSystem` testbar). |
| `ConformanceEngine.kt` | Decode→Re-Encode-Loop: `roundTrip`, `writerByteEquality`, `runCorpus` (sammelt, P2). |
| `*Test.kt` | **Self-Tests der Mechanik** (Fakes, kein Korpus/Reader nötig) — beweisen, dass die Harness läuft & Op/Feld-Diffs meldet. |

## Was die Tester besitzen (NICHT hier)
- Die 175 `.rc` unter `shared/src/commonTest/resources/rc-corpus/` + `MANIFEST.tsv` (+ `corpus/` für P2).
- `assertRcBytesEqual(expected, actual, fixtureId)` — dünner Assertion-Helfer (Größe + erster Divergenz-Index), nutzt `ByteDiff`/`RcDiffReporter`.
- Test-Klassen `RcConformanceP0Test` (Reihenfolge F1,F2,F4,F5: je `roundTrip_*` + `writerByteEquality_*`), dann P1, F7-decode, P2.

## Andock-Punkte (so docken die Tester an)
```kotlin
// 1) Codec aus REM-2/REM-3 (sobald vorhanden) an den Seam binden:
val codec = RcCodec { bytes -> RemoteComposeReaderAdapter(bytes) } // RcReadback: reEncode()+opSpans()

// 2) Golden laden (Loader-Mechanik):
val golden = RcCorpus.readFixture("procedure_simple1.rc")

// 3) Mechanik aufrufen, im Test-Body asserten (Assertion = Tester):
val r = ConformanceEngine.writerByteEquality("F1", golden, codec)
assertRcBytesEqual(golden, /*actual*/ ..., "F1")   // oder direkt r.matched prüfen + r.report ausgeben

// P2 (sammeln, nicht fail-fast):
val names = RcCorpus.corpusNames()
val report = ConformanceEngine.runCorpus(names.map { NamedBytes(it, RcCorpus.readFixture("corpus/$it")) }, codec)
assertTrue(report.allPassed, report.summary())
```

## Offene/abzustimmende Punkte (mit PO/test-1)
1. **`RcCorpus.fixtureRoot()` Plattform-Auflösung** (Android/iOS) ist noch offen → blockiert auf REM-3 +
   physischem Korpus. Bis dahin: `RcCorpus.rootOverride` im Test-Setup setzen. Ohne Override wirft die
   Mechanik bewusst `NotImplementedError` (kein stiller Fehlpfad).
2. **`MANIFEST.tsv`-Format** hier provisorisch nach der `FixtureEntry`-Signatur (8 Spalten). Finalisierung
   durch test-1.
3. **Codec-Seam ist gebunden** (`RcDocumentCodec` → REM-3 `DocumentReader` + `Operation.write()`).
   Engine/Diff/Loader unverändert. Header-only-Dokument decode→re-encode ist byte-identisch (Test).
   **Offen (dev-1):** `Header.write()` stempelt im Flat-Pfad die Versions-Konstanten (MAJOR/MINOR=1)
   statt der geparsten Version → ein **API-6-Flat-Header (z. B. `procedure_simple1`) round-trippt
   noch nicht byte-genau** (Minor 0→1). Blockiert F1-Byte-Equality; an PO gemeldet.
4. Loader-Tests laufen über den in-memory `CorpusIo`-Seam (kein `okio-fakefilesystem` — dessen
   `kotlinx-datetime`-Abhängigkeit kollidiert mit der von `compose-material3` erzwungenen datetime-0.7.x).
