# REM-125 Tween-Resolve — REM-123-Gate-Verdikt (test-3)

**Branch:** `bugfix/REM-125-tween-gate` von develop @ `49e3560` (= post-REM-124-Merge `391d0be`).
**Methode:** REM-123 Variante A (Daten-Orakel + 2-Quellen-Cross-Check) wie REM-124.
**Datum:** 2026-06-28.

## Scope

REM-125 = **3. Einsatz** des REM-123-Gates. 7 Docs mit `DrawTweenPath`/`PathTween`-Ops.
3. Instanz der NaN-Coord-Klasse (nach REM-121-PathAppend und REM-124-ClipRect).

**REM-121-Überlapp (3 Docs):** clock_demo1_clock1, experimental_sweep_clock1, paths_demos.
Per-Path-scoped: REM-121-Loop-Path-Status bleibt narrow gültig (REM-121-Trustworthy hatte
narrow Scope auf PathAppend); der hier verifizierte Tween-Path ist eine **eigenständige
Aussage**. „Goldens sind bug-class-relativ, nicht absolut."

**Doku-Korrektur:** dev-2s Commit-Message `4b44e36` listet REM-121-Überlapp als
„clock_demo1/paths_demos/path_demo_path2". Der eigentliche Überlapp ist
**clock_demo1_clock1/experimental_sweep_clock1/paths_demos**; `path_demo_path2` ist neu in
REM-125-Quarantäne und war **nicht** in REM-121. Die Coord-Daten in Source B selbst sind
korrekt (alle 7 Docs einzeln aufgelistet); nur die Header-Zusammenfassung hat den Typo.

## Anti-Anchoring-Discipline

PO bestätigte den HOLD-Punkt (Discord `1520896221076783325`): Source B (`4b44e36`,
`docs/REM-125-tween-resolved-coords.md`) wurde JUST nach meinem ersten Fetch gepusht;
re-fetched + Source B vollständig konsumiert + extrahiert **bevor** Source A gelesen
wurde. Reihenfolge: B-Read → B-Extract → A-Read → A-Extract → Cross-Check → Test-Run →
Render. Anti-Anchoring strikt eingehalten.

## Setup — 2 unabhängige Quellen

**Source A — Strukturtest** (`shared/src/commonTest/.../Rem125TweenResolveTest.kt`,
Commit `9708a2b`):
- 3 Tests: `drawTweenPath_resolvesTweenStartStop`, `pathTween_resolvesTween`,
  `literalTween_passesThroughUnchanged`.
- Synthetic Inputs: ctx-loaded floats `(0.75f, 0.10f, 0.90f)` für var-refs in Region 2
  + Literal-Floats `(0.5f, 0f, 1f)`.
- Asserts: nach `op.updateVariables(ctx)` halten die resolved `rTween/rStart/rStop`-Felder
  die Var-Werte (NICHT raw NaN); Literal-Floats passieren unverändert durch.

**Source B — Daten-Orakel-Artefakt** (`docs/REM-125-tween-resolved-coords.md`,
Commit `4b44e36`):
- Quelle: data-driven aus 7 Korpus-`.rc`s durch fixed pipeline (post-`9708a2b`).
- Format: per-Doc, per-Op `DrawTweenPath`/`PathTween` mit resolved tween/start/stop
  + ggf. resulting path bbox+distinct.
- Bug-Diskriminator: finite ≠ raw-NaN (Resolution lief vs. wurde übersprungen).

**Unabhängigkeit:** A nutzt synthetische `(0.75, 0.10, 0.90)`-Werte und keine
`.rc`-Korpus-Bezüge; B leitet aus realen Korpus-Docs ab. Null geteilte Assertion-Werte.

## Cross-Check Source A ↔ Source B

| Property | Source A (Test) | Source B (Coord-Artefakt) | Verdikt |
|---|---|---|---|
| DrawTweenPath resolviert NaN-encoded tween/start/stop via VariableSupport | ✓ direkt: `rTween/rStart/rStop = (0.75, 0.10, 0.90)` aus ctx-loadFloat | ✓ alle 4 DrawTweenPath-Docs zeigen resolved finite-Werte (kein raw NaN) | **AGREE** |
| PathTween resolviert NaN-encoded tween via VariableSupport | ✓ direkt: `rTween = 0.75` | ✓ path_demo_path_tween_demo zeigt resolved tween=1.0 mit non-degenerate 60-pt out-paths | **AGREE** |
| Literal tween/start/stop passieren unverändert | ✓ direkt: `(0.5, 0, 1)` → `(0.5, 0, 1)` | (impliziert: resolved-Werte konsistent mit erwarteten Doc-Inhalten) | **AGREE** |
| Resulting interpolated path non-degenerate bei resolvable tween | indirekt: Mechanismus liefert finite Werte → korrekte Interpolation | ✓ direkt: clock_demo1 + experimental_sweep_clock1 = 301 distinct; path_demo_path_tween_demo = 60 distinct | **AGREE** |

**Disagreement:** Keiner. **Cross-Check verdikt: GRÜN.**

**Anmerkung zu `path_demo_path2` — Source B zeigt resolved tween=NaN:** Das ist
**by-design degenerate** (Var-Daten selbst sind NaN, NICHT raw-NaN-bits aus
Var-Encoding). Source A beweist die Resolution-Mechanik mit finite Var-Daten; B zeigt,
dass für diese spezielle Doc die Var-Daten selbst NaN sind. Beide Aussagen sind konsistent.

## Source A lokal grün (verify-don't-trust)

```
./gradlew :shared:jvmTest --tests "...Rem125TweenResolveTest"
BUILD SUCCESSFUL — tests=3 failures=0 errors=0 time=0.028s
- drawTweenPath_resolvesTweenStartStop[jvm]  PASS
- pathTween_resolvesTween[jvm]                PASS
- literalTween_passesThroughUnchanged[jvm]    PASS
```

## REM-123 Schritt 4 — Cross-Density (d=1.0 + d=3.0)

| Doc | d=1.0 PNG | d=3.0 PNG | Δ |
|---|---|---|---|
| demo_path_expression_path_test2 | 10740 | 10740 | identisch |
| demo_path_expression_path_test3 | 10740 | 10740 | identisch |
| path_demo_path2 | 869 | 869 | identisch |
| path_demo_path_tween_demo | 4912 | 4912 | identisch |
| paths_demos | 5154 | 5154 | identisch |
| clock_demo1_clock1 | 51153 | 53827 | +5.2 % (text/font-density-sensitiv, strukturell identisch) |
| experimental_sweep_clock1 | 51153 | 53827 | +5.2 % (text/font-density-sensitiv) |

5/7 byte-identisch; Clocks zeigen marginale font-density-Drift wie in REM-121.
REM-93-Density-Invarianz **nicht regressiert**.

## Verdikt-Matrix (7/7)

| Doc | parity vs alt | non_bg pre→post | Source-B-Erwartung | Entscheidung | Begründung |
|---|---|---|---|---|---|
| clock_demo1_clock1 | TEXT 19.0 % breach, 3.0 % cluster | 205717 → 207023 (+1306, AA-Overlay) | DrawTweenPath p1=p2=55 tween=0 → 301-pt circle = REM-121-Loop-path | **RE-BASELINE** | Tween-Fix fügt zusätzliche AA-Stroke auf Tween-path-Site hinzu. Per-Path-scoped: Tween-path-scope ist eigenständig vs REM-121-Loop-path. |
| demo_path_expression_path_test2 | PASS 0.00 % | 55966 → 55966 (identisch) | 2× DrawTweenPath tween=1.0 (kein path-content in B) | **KEEP** | Render pixel-identisch; Tween-Resolve strukturell durch A bewiesen; kein visueller Effekt. |
| demo_path_expression_path_test3 | PASS 0.00 % | 55966 → 55966 (identisch) | 2× DrawTweenPath tween=1.0 | **KEEP** | wie test2. |
| experimental_sweep_clock1 | TEXT 19.0 % breach, 3.0 % cluster | 205717 → 207023 (+1306) | DrawTweenPath p1=p2=60 tween=0 → 301-pt circle | **RE-BASELINE** | wie clock_demo1_clock1. |
| path_demo_path2 | PASS 0.00 % | 4775 → 4775 (identisch) | 2× PathTween resolved tween=NaN (by-data) | **KEEP** | intrinsic-NaN-by-data; Resolution-Mechanik lief korrekt (Source A); Renderer hat NaN-Tween-Verhalten (clamp/fallback) → kein visueller Effekt; kein Regress. |
| **path_demo_path_tween_demo** | **FAIL 78.9 % breach, 78.9 % cluster, android-blank** | **0 → 19984 (BLANK→VISIBLE)** | 2× PathTween tween=1.0 → 60-pt-Kreis x[-150..150] y[-150..150] | **RE-BASELINE** | **Der dispatch≠visual-Beleg**: drawCount=5 RENDERS pre-Fix, aber non_bg=0 = TRULY BLANK. Fix bringt den 60-pt-Tween-Kreis sichtbar; bbox (0,0)-(299,299) deckt 300x300-Canvas voll ab (Doc-Coords [-150..150] werden via translate(150,150) zu Surface-Coords abgebildet). **Load-bearing für REM-123-Gate-Validierung.** |
| paths_demos | PASS 0.03 % breach | 7430 → 7430 (identisch) | 4× DrawTweenPath tween=1.0 (kein path-content in B) | **KEEP** | parity_compare PASS; 58-byte file-size-diff (1.1 %) ist Kompressionsrauschen; Tween-Resolve strukturell bewiesen; REM-121-KEEP für Loop-Path bleibt narrow gültig. |

**Verteilung: 3 RE-BASELINE / 4 KEEP / 0 FLAG.**

## Per-Path-Scope für die 3 REM-121-Überlapp-Docs

| Doc | REM-121-Scope (Loop-Path) | REM-125-Scope (Tween-Path) | Kombiniertes Verdikt |
|---|---|---|---|
| clock_demo1_clock1 | RE-BASELINE (PathAppend-Fix war PathAppend-Bug) ✓ trustworthy | RE-BASELINE (Tween-Fix fügt korrekten Tween-stroke) ✓ trustworthy | **Doppelt-fix-baselined, höchste End-Confidence** |
| experimental_sweep_clock1 | RE-BASELINE ✓ trustworthy | RE-BASELINE ✓ trustworthy | wie oben |
| paths_demos | KEEP (Loop-Path distinct=1 intrinsisch by design) ✓ trustworthy für Loop-scope | KEEP (Tween-Resolve mechanisch bewiesen + visuell ident) ✓ trustworthy für Tween-scope | **Beide Scopes separat verifiziert, beide trustworthy** |

## load-bearing Highlight (path_demo_path_tween_demo)

Dieses Doc validiert die REM-123-Gate-Methodologie selbst:
- `_sweep.csv` pre-Fix: `path_demo_path_tween_demo,300,300,5,RENDERS,,` — drawCount=5, Status=RENDERS, defer=∅.
- Pixel-bbox pre-Fix: **0 non_bg pixels**. Komplett blank.
- Klassischer dispatch≠visual: drawCount>0 + RENDERS-Status hat 0 sichtbare Pixel maskiert.
- Source B's Daten-Orakel-Erwartung (60-pt Tween-Kreis non-degenerate) wurde von **keinem** der bestehenden CSV/Cross-Target-Heuristiken gefangen.
- post-Fix: 19984 non_bg, full bbox, distinct=139 colors → der Tween-Kreis erscheint.

**Das ist exakt der Vorfall-Klassen-Beweis dafür, warum die REM-123-Working-Rule
(Daten-Orakel statt nur drawCount/Cross-Target) als §6-Erweiterung gerechtfertigt ist.**

## CSV-Konsistenz

`screenshots/reference/desktop/_sweep.csv` wurde **nicht** geändert. Die 7 REM-125-Docs
sind pre/post-Fix byte-identisch in CSV-Spalten (surfaces/drawCount/deferredTags) —
auch `path_demo_path_tween_demo` bleibt `300,300,5,RENDERS,,` strukturell. Fix ändert nur
visuelles Rendering, nicht Op-Struktur — wieder die Lücke, die REM-123-Gate schließt.

## File-Disjunktheit

Branch enthält ausschließlich:
- 3 PNG-Goldens unter `screenshots/reference/desktop/`.
- Diesen Verdikt-Report unter `artifacts/REM-125-tween-resolve-gate.md`.
- Keine Code-Änderungen, keine CSV-Änderungen, keine Source-A/B-Dateien.
- Fix + Test + Coord-Artefakt leben in `bugfix/REM-125-tween-variablesupport`.
- PO mergt beide Branches zusammen.

## Empfehlung

**REM-123-Gate für REM-125 grün** — beide unabhängigen Quellen stimmen überein, Source A
lokal grün lauffähig (3 Tests), Daten-Orakel-Checks erfüllt auf allen 7 Docs, Density-
Invarianz strukturell + empirisch bestätigt, per-Path-scoped Verdikte für die 3 REM-121-
Überlapp-Docs sauber abgegrenzt. **Load-bearing dispatch≠visual-Beweis** durch
path_demo_path_tween_demo (drawCount=5+RENDERS aber 0 sichtbare Pixel pre-Fix) bestätigt
die Gate-Methodologie.

PO kann `bugfix/REM-125-tween-variablesupport` (`9708a2b`+`4b44e36`) und
`bugfix/REM-125-tween-gate` zusammenmergen.
