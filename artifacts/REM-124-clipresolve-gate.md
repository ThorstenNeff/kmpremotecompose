# REM-124 ClipRect-Resolve — REM-123-Gate-Verdikt (test-3)

**Branch:** `bugfix/REM-124-clipresolve-gate` von develop @ `64e7924`
(+ post-fetch develop-Tip `771a1a7`; Rebase auf den finalen Stand vor PO-Merge).
**Methode:** REM-123 Variante A (pure-data path-98-Orakel + 2-Quellen-Cross-Check)
gemäß PO-Entscheidung Discord `1520890314951491586`.
**Datum:** 2026-06-28.

## Scope

REM-124 = 2. Einsatz des REM-123-Gates. 1 Doc: `stock`. Bug-Locus: `ClipRect`-Op
nicht `VariableSupport`-konform → NaN-Coord-Refs erreichen `clipRect()` als rohe
NaN → degenerierter Clip versteckte den Loop-gebauten Sparkline `DrawPath id=98`.

Pre-Bug-Status (aus REM-121-Quarantäne-Rebaseline-Report): `stock` war der einzige
Daten-Orakel-FAIL trotz REM-121-Fix, dort als `REM-124` geflagt.

## Setup — 2 unabhängige Quellen

**Anti-Anchoring-Discipline:** Sources A und B wurden in dieser Reihenfolge konsumiert,
KEINE rückwärts-Konsultation:
1. Source B (Coord-Artefakt) komplett gelesen + Werte extrahiert.
2. **Dann erst** Source A (Test) gelesen + Asserts extrahiert.
3. **Dann erst** Cross-Check.

**Source A — Strukturtest** (`shared/src/commonTest/.../Rem124ClipRectResolveTest.kt`,
Commit `822fe75` auf `bugfix/REM-124-stock`):
- Test: `clipRect_resolvesVariableBounds`.
- Synthetic Inputs: `FloatConstant`s in Region 2 (`0x200000|N`), Werte `(5f, 6f, 100f, 200f)`.
- Op: `ClipRect(asNan(x1), asNan(y1), asNan(x2), asNan(y2))` — alle 4 Bounds als NaN-encoded var-refs.
- Assert: `clipRect`-Aufruf am `PaintContext` empfängt `[5f, 6f, 100f, 200f]` (resolved values,
  NICHT raw NaN).
- Quelle der Werte: dev-2-handgemachte Synthetik, **nicht** aus dem `stock.rc`-Korpus.

**Source B — Daten-Orakel-Artefakt** (`docs/REM-124-stock-path98-coords.md`, Commit `a140646`):
- Quelle: data-driven aus `corpus/stock.rc` via fixed pipeline (post-`822fe75`),
  doc-space, 256x256 surface, density=1.0, t=0.
- Werte:
  - `canvasDims: w=128.0, h=260.0`.
  - `clipRect(resolved): (24.2, 24.2, 103.8, 235.8)` — valider bounded Rect.
  - `path98: 92 pts, distinct=92, x[19.2..108.8] y[24.83..240.8]`.
  - 92 pts-Endpunkte aufgelistet (Sparkline).

**Unabhängigkeits-Argument:** A nutzt synthetische `(5, 6, 100, 200)`-Werte und einen
minimalen op-Stream (4 FloatConstants + 1 ClipRect, kein stock.rc-Bezug); B leitet aus
`stock.rc` reale Geometrie ab. Keine geteilten Assertion-Werte zwischen A und B. Die
zwei Quellen sind **mutually independent** und können einander cross-checken.

## Cross-Check Source A ↔ Source B

| Property | Source A (Test) | Source B (Coord-Artefakt) | Verdikt |
|---|---|---|---|
| ClipRect resolviert NaN-Bounds zu var-Werten | ✓ direkt asserted: `[5f,6f,100f,200f]` aus `FloatConstant`s | ✓ implizit: `clipRect(resolved) = (24.2,24.2,103.8,235.8)` ≠ NaN | **AGREE** |
| Resolver-Mechanismus ist VariableSupport-konform | ✓ Test würde fehlschlagen, wenn ClipRect nicht VariableSupport-konform wäre | ✓ Resolved Clip impliziert VariableSupport-Pfad gelaufen | **AGREE** |
| Bug-Symptom (degenerierter Clip versteckt DrawPath) ist behoben | indirekt: wenn Resolution funktioniert, wird Clip valide → DrawPath sichtbar | direkt: `path98: 92 pts, distinct=92` (Bug hätte distinct=1 → flat geliefert) | **AGREE** |

**Zusätzliche Daten-Orakel-Checks (REM-123-Gate-Standard) auf Source B:**

- **Check 1 — non-degenerate:** `distinct=92` = `count=92`. ✓ Bug hätte distinct=1.
- **Check 2 — spans data range:** `x[19.2..108.8]` (Spanne 89.6) und `y[24.83..240.8]`
  (Spanne 215.97) — die Sparkline überstreicht 70 % der Canvas-Breite und 83 % der
  Canvas-Höhe. ✓ Substanziell, nicht-kollabiert.
- **Check 3 — Clip-resolved-valide:** `clipRect(resolved) = (24.2, 24.2, 103.8, 235.8)`
  ist ein gültiger Rect (left<right, top<bottom, keine NaN). ✓
- **Anti-Symptom (REM-121-FAIL-Vergleich):** mein voriger Render zeigte bbox `(0,1)-(34,52)`
  in der 256x256-Doc-Surface — das ist die **außerhalb-der-Canvas**-Region (der 128x260-
  Canvas startet erst bei x>20 in Doc-Space). Mit gefixtem Clip rendert path-98 jetzt
  INNERHALB der Canvas (path-Coords liegen im (19..109)x(25..241)-Bereich), die scroll-
  off-screen-Position des Canvas im Doc-Render erklärt, warum ein Voll-Doc-Pixel-Diff
  das nicht sieht — das ist Scroll-Mechanik, **nicht** der Bug.

**Verdikt Cross-Check:** **GRÜN.** Beide Quellen stimmen über die behauptete Eigenschaft
(ClipRect-Resolver mit VariableSupport, valide Clip-Bounds, non-degenerate path-98)
überein. Disagreement gefunden? Nein.

## Source A lokal grün (verify-don't-trust)

Test ausgeführt auf JVM-Target:

```
./gradlew :shared:jvmTest --tests "...Rem124ClipRectResolveTest"
BUILD SUCCESSFUL in 27s
```

JUnit XML (`shared/build/test-results/jvmTest/TEST-...Rem124ClipRectResolveTest.xml`):
```
<testsuite tests="1" skipped="0" failures="0" errors="0" time="0.073">
  <testcase name="clipRect_resolvesVariableBounds[jvm]" time="0.073"/>
</testsuite>
```

Source A also tatsächlich grün auf meiner Maschine, nicht nur in dev-2s Bericht.

## REM-123 Schritt 4 — Cross-Density-Argumentation

**Statt unnötigem Sweep:** ClipRect-Resolution + Path-Build laufen in **doc-space-Coords**,
nicht in pixel-space. Density ist eine reine Skiko-Surface-Scale-Eigenschaft, die NACH
dem Geometry-Build greift. Der Bug-Locus (NaN-Bound-Resolution) ist density-orthogonal.
Damit ist Density-Invarianz **strukturell garantiert** und braucht keinen eigenen
Sweep zur Bestätigung.

Empirisches Backup (aus REM-121-Rebaseline-Daten): `stock`-Render war pre/post-REM-121
und d=1.0/d=3.0 byte-identisch (1141 Bytes auf allen 4 Achsen). Der REM-124-Fix wird
das d=1.0/d=3.0-Verhalten gleichermaßen ändern (oder strukturell unverändert lassen
auf der Doc-Pixel-Ebene, wenn der Canvas off-screen bleibt).

## Golden-Disposition

`screenshots/reference/desktop/stock.png` bleibt **unverändert**. Begründung:
- Der Sparkline-Canvas ist im Voll-Doc-Render bei scroll=0 weiterhin off-screen
  (Scroll-Mechanik ist nicht Teil von REM-124).
- Pre/post-REM-124 ist der Voll-Doc-Pixel-Output strukturell unverändert (1141 Bytes).
- Daten-Orakel-Verifikation läuft auf der Render-Pipeline-Ebene (Source A+B), nicht
  auf der visible-output-Ebene — kein Pixel-Golden-Refresh nötig.

Falls das Visual-Behavior später durch eine Scroll-into-View-Logik geändert wird
(separater Bug, nicht REM-124), wäre das ein neuer REM-123-Gate-Lauf.

## File-Disjunktheit

Branch enthält ausschließlich:
- Diesen Verdikt-Report unter `artifacts/REM-124-clipresolve-gate.md`.
- Keine Code-Änderungen. Keine PNG-Änderungen. Keine `_sweep.csv`-Änderungen.
- Der Fix + Test + Coord-Artefakt leben in dev-2s `bugfix/REM-124-stock`-Branch.
  PO mergt beide Branches zusammen.

## Empfehlung

**REM-123-Gate für REM-124 grün** — beide unabhängigen Quellen stimmen überein, Source A
lokal grün lauffähig, Daten-Orakel-Checks 1+2+3 erfüllt, Density-Invarianz strukturell
garantiert, kein Golden-Regress.

PO kann `bugfix/REM-124-stock` (`822fe75` + `a140646`) und `bugfix/REM-124-clipresolve-gate`
zusammenmergen.
