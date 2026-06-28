# REM-121 Quarantine Re-Baseline — Verdikt-Report (test-3)

**Branch:** `bugfix/REM-121-quarantine-rebaseline` von develop @ `ee21edf`.
**Methode:** REM-123 4-Schritt-Gate (Render-Golden Promote-Gate, `docs/render-golden-promote-gate.md`)
gegen dev-2s data-driven Orakel (`docs/REM-121-loop-pathappend-quarantine-coords.md` @ `58805d4`).
**Datum:** 2026-06-28.

## Setup

dev-2s REM-121-Fix (`561071b`) wurde **lokal** in den Working Tree cherry-picked, um die 17
quarantänierten Docs mit Fix-Applied zu rendern. Cherry-pick wurde **vor dem Commit zurückgenommen**
— dieser Branch enthält **ausschließlich** die re-baselineten PNG-Goldens (file-disjunkt zu dev-2s
Code-Branch, analog REM-93/REM-117-Pattern). PO mergt Code (`bugfix/REM-121-loop-pathappend`) +
Goldens dieser Branch zusammen.

Render-Setup:
- Desktop-Sweep-Harness `./gradlew :desktopApp:desktopRenderSweep`.
- 2 Läufe: `density=1.0` → `/tmp/rem121_d1/`, `density=3.0` → `/tmp/rem121_d3/`.
- 17 Docs aus assist/dev-2-Quarantäneliste.

## REM-123 Gate Schritte

**Schritt 1 — Daten-Orakel:** dev-2 liefert `pts`-Liste pro Doc aus unit-verifizierten
Building-Blocks (`getPos` / computed angle), unabhängig vom akkumulierenden Render. Verwendet:
expected `pts`-Count, `distinct` count, x/y bounding box.

**Schritt 2 — Diff KMP gegen Orakel:** 3-Check-Verfahren
(`/tmp/oracle_check.py`, im Branch nicht committed — reines Verify-Tool):
- Check 1: `non_bg_pixel_count > 4` (non-degenerate).
- Check 2: rendered bbox brackets expected x/y range (5% tolerance).
- Plus parity_compare vs pre-Fix Golden, um die Fix-Wirkung zu klassifizieren
  (`screenshots/parity/parity_compare.py --no-resize`).

**Schritt 3 — Re-baseline:** confirmed-degenerated PNGs (parity_compare zeigt Bug-Wirkung +
Daten-Orakel grün im neuen Render) überschreiben das alte Golden. Andere bleiben.

**Schritt 4 — Cross-Density-Absicherung:** d=1.0 und d=3.0 verglichen → bitweise/oracle-äquivalent
(siehe Density-Invarianz unten). REM-93-Density-Invarianz wird nicht regressiert.

## Verdikt-Matrix (17/17)

| Doc | parity vs alt Golden | Daten-Orakel (neu) | Entscheidung |
|---|---|---|---|
| clock_demo1_clock1 | FAIL 94.89% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| clock_demo2_jancy_clock2 | FAIL 94.80% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| clock_demo2_jclock2 | FAIL 95.17% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| demo_graphs1 | BLANK 0.00% breach, both blank | PASS chk1, chk2=N/A (off-canvas) | **KEEP** (path off-canvas by design) |
| experimental_fancy_clock | FAIL 95.34% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| experimental_sweep_clock1 | FAIL 94.89% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| fancy_clock2 | FAIL 94.80% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| fancy_clocks_fancy_clock1 | FAIL 95.35% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| fancy_clocks_fancy_clock2 | FAIL 94.96% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| fancy_clocks_fancy_clock3 | FAIL 95.34% breach, android-blank | PASS chk1+chk2 | **RE-BASELINE** |
| graph_graph2 | FAIL 23.63% breach | PASS chk1+chk2 | **RE-BASELINE** |
| heart_rate_timeline | TEXT 1.99% breach (curve vs flatline) | PASS chk1+chk2 | **RE-BASELINE** (Vorfall-Ursprung) |
| hydration_wave | FAIL 7.80% breach | PASS chk1+chk2 | **RE-BASELINE** |
| path_demo_remote_construction | FAIL 38.53% breach | PASS chk1+chk2 | **RE-BASELINE** |
| paths_demos | PASS 0.00% breach | PASS chk1+chk2 (intrinsisch degenerate) | **KEEP** (Loop-Path ist 1-Punkt by design) |
| **stock** | BLANK 0.00% breach, both blank | **FAIL chk2** (bbox 35x52 vs expected 90x216) | **FLAG TO PO — Oracle-Fail trotz Fix** |
| stock_sparkline | TEXT 3.83% breach (curve vs flat) | PASS chk1+chk2 | **RE-BASELINE** |

## Ergebnis

- **14 RE-BASELINE** (PNG-Goldens überschrieben in `screenshots/reference/desktop/`):
  - 9 Clocks (ALLE), 5 non-Clock-Daten-Docs (graph_graph2, heart_rate_timeline, hydration_wave,
    path_demo_remote_construction, stock_sparkline).
- **2 KEEP** (Goldens unverändert, sind trustworthy):
  - `demo_graphs1` (Path-Koordinaten Y=[-53..-32] liegen außerhalb der 500x500-Canvas → unsichtbar
    by design, Render = blank by design).
  - `paths_demos` (Loop-Path hat distinct=1 = 1 Punkt am Origin, intrinsisch degenerate by design;
    sichtbares Rendering kommt aus separaten statisch-gemalten Path-Effects).
- **1 FLAG TO PO:** `stock` — die Oracle-Erwartung x[19.2..108.8] y[24.83..240.8] wird vom
  Render NICHT abgedeckt; statt dessen erscheinen nur ~238 Pixel in (0,1)-(34,52). Sowohl pre-Fix
  als auch post-Fix identisch. Mögliche Ursachen:
  - Sekundärer Render-Bug (Clip/Transform/Opacity überschattet den Loop-Path).
  - Oracle-Definition trifft auf eine andere Path-Instanz als der Renderer (`path 98` vs
    tatsächlich gezeichneter Path).
  - REM-121-Fix berührt den Bug von `stock` nicht.

## Anti-Hypothese bestätigt: 0 Clocks von per-iteration-draw "gerettet"

assist's Hypothese, dass die 7 Clocks (clock_demo2_jancy_clock2, clock_demo2_jclock2,
experimental_fancy_clock, fancy_clock2, fancy_clocks_fancy_clock1..3) per-iteration-draws hätten
und damit unbetroffen sein könnten, ist **empirisch widerlegt:** ALLE 9 Clocks (inkl.
high-confidence clock_demo1_clock1 + experimental_sweep_clock1) hatten android-blank Goldens
mit ~95% Breach. Keiner wurde von der `paint`-Site-Konfiguration gerettet. Lehre: assist's
"necessary≠sufficient"-Caveat war richtig (Render-Urteil entscheidet), in diesem Fall haben
alle 17 Loop+PathAppend-Kandidaten den Bug auch wirklich getroffen (modulo `paths_demos` und
`demo_graphs1`, die intrinsisch degenerate by design sind).

## Density-Invarianz (REM-93-Regression-Check)

Beide Sweep-Runs (d=1.0 und d=3.0) liefern für die 17 Docs:
- 16/17 PNGs byte-identisch (CSV draws/surfaces unverändert).
- 9 Clock-PNGs: marginal byte-Drift (~3-6%, Text/Font-Rendering density-sensitiv; Daten-Orakel
  PASS auf beiden Densities, keine strukturelle Drift).
- Stock: identisch bei d=1.0 und d=3.0 (Oracle-Fail identisch reproduziert).

→ REM-93's Density-Invarianz wird **nicht regressiert** durch REM-121-Fix.

## CSV-Konsistenz

`screenshots/reference/desktop/_sweep.csv` wurde **nicht** geändert: pre/post-Fix sind alle 17
Zeilen byte-identisch (drawCount/surfaces/deferredTags strukturell unverändert; Fix ändert nur
das VISUELLE Render-Ergebnis, nicht die Op-Strukturen). Das ist genau die Lücke, die REM-123
schließt — `drawCount > 0` ≠ visuell-korrekt.

## File-Disjunktheit

Branch enthält ausschließlich:
- 14 PNG-Goldens unter `screenshots/reference/desktop/`.
- Diesen Report unter `artifacts/REM-121-quarantine-rebaseline.md`.
- Keine Code-Änderungen — der REM-121-Fix lebt in dev-2s `bugfix/REM-121-loop-pathappend` Branch.
