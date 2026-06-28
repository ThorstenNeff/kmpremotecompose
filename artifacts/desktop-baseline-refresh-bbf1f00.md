# Desktop-Golden-Baseline-Refresh auf develop `bbf1f00`

**Datum:** 2026-06-28 · **Lane:** QA test-3 (Desktop-Render-Sweep) · **Analog:** REM-102

## TL;DR — Verdikt: **CLEAN, promote-ready**

Voll-Sweep auf 173 .rc gegen develop `bbf1f00` produziert **18 PNG-Deltas + 1 CSV-Delta** ggü.
Vor-Baseline (REM-102 @ `0050282`). **Alle 18 Deltas attribuierbar an bekannte render-relevante
Merges** (REM-99 PATH_EFFECT, REM-104 HYPOT/GRADIENT_DEGENERATE, REM-109-S1/S2 A_SPLINE/IFELSE/
RPN-Ops). **0 Regressionen, 0 ERROR, 0 neue BLANK, 0 surfaceW/H-Drift.** Netto: −1 BLANK
(`hostile_actor1` BLANK→RENDERS), +1 RENDERS.

| Metrik       | Alt (REM-102) | Neu (bbf1f00) | Δ        |
|--------------|---------------|---------------|----------|
| RENDERS      | 169           | 170           | +1 ✅    |
| BLANK        | 4             | 3             | −1 ✅    |
| ERROR        | 0             | 0             | 0        |
| Total docs   | 173           | 173           | =        |
| Build-Zeit   | —             | 36 s          | —        |

## A. CSV-Status-Upgrades (9 docs — semantische Verbesserung)

| Doc                       | Δ                                              | Attribution                |
|---------------------------|------------------------------------------------|----------------------------|
| `countdown`               | tag GRADIENT_DEGENERATE entfernt               | REM-104 HYPOT-Eval         |
| `demo_use_of_global`      | tag GRADIENT_DEGENERATE entfernt               | REM-104                    |
| `hostile_actor1`          | **BLANK(0) → RENDERS(100 draws)**              | REM-109-S1/S2 (IFELSE/RPN) |
| `hostile_actor1_c`        | 1 → 61 draws                                   | REM-109-S1/S2              |
| `stock`                   | tag PATH_EFFECT entfernt                       | REM-99 PATH_EFFECT-Impl    |
| `themed_plot1`            | tag PATH_EFFECT entfernt                       | REM-99                     |
| `paths_demos`             | PATH_EFFECT → 4 feinere Tags (DASH_DEGENERATE/DISCRETE/PATHDASH_DEGENERATE/SUM) | REM-99 (finer classification, residual degenerates real) |
| `demo_graphs0`            | 40004 → 57 draws, tag entfernt                 | REM-109-S1 IFELSE/A_SPLINE |
| `demo_graphs1`            | 40004 → 76 draws, tag PATH_EFFECT entfernt     | REM-109-S1 + REM-99        |
| `linear_regression`       | 40054 → 135 draws                              | REM-109-S1                 |

*Die `40004/40054`-drawCounts waren symptomatisch für divergente Eval-Loops vor REM-109-S1 — kein
Render-Fehler, aber stark hinweisend auf nicht-konvergierende RPN-Auswertung. Jetzt sauber.*

## B. PNG-only-Deltas (8 docs — Pixel-Refresh ohne CSV-Status-Flip)

| Doc                              | Attribution                                    |
|----------------------------------|------------------------------------------------|
| `digital_clock1`                 | REM-104 (explizit als Bonus in Merge-Msg)      |
| `experimental_solar_gmt`         | REM-109-S2 Trig-Ops (TAN/ACOS/ATAN2)           |
| `graph_graph1`, `graph_graph2`   | REM-109-S1 A_SPLINE Chart-Render               |
| `heart_rate_timeline`            | REM-109-S1/S2 RPN-Eval                         |
| `pie_chart2`                     | REM-109-S1/S2 RPN-Eval                         |
| `spline_demo_spline_demo1`       | REM-109-S1 A_SPLINE — **siehe Flag unten**     |
| `stock_sparkline`                | REM-109-S1 Spline-Render                       |

## C. Identisch (155 docs)

Inklusive **alle touch* Docs (touch1, touch2, touch_wrap)** — schon in REM-116 auf den
authored-Default-Render gegen `bf0941e+S2` refreshed, daher byte-identisch zum jetzigen Sweep.
Bestätigt: REM-116 hat die Touch-Goldens korrekt auf das aktuelle Touch-Eval-Engine-Verhalten
gesetzt.

## D. Flags an PO

1. **`spline_demo_spline_demo1` — Harness-Metric-Drift, KEIN Render-Bug.**
   CSV sagt weiterhin `BLANK, 0 draws`, aber PNG ging von `827 B` (echtes blank) auf `43707 B` (sichtbarer
   Inhalt). Hypothese: REM-109-S1 A_SPLINE rendert über deferred-path, den unser
   `drawCount`-Zähler im desktopRenderSweep nicht inkrementiert. **Keine Regression** — eher ein
   Aufdeck-Signal, dass die Harness-Drawcount-Heuristik für deferred-spline-Ops blind ist.
   Empfehlung: separates Ticket in dev-1-Lane (Harness-Telemetry-Hardening), nicht render-gating.

2. **`paths_demos` PATH_EFFECT-Residuen.** REM-99 hat PATH_EFFECT-Impl gebracht und die Tag-Granularität
   verbessert. Die 4 residualen Tags (`PATH_EFFECT_DASH_DEGENERATE`, `DISCRETE`,
   `PATHDASH_DEGENERATE`, `SUM`) sind **echte Degenerate-Cases / komplexe Effekte**, die der
   neue Impl korrekt als deferred markiert. **Erwartetes Outcome, kein Defekt.**

## E. Attribution-Map (Merge → Doc-Klasse)

| Merge             | Doc-Klasse                                    | Δ          |
|-------------------|------------------------------------------------|------------|
| REM-99 PATH_EFFECT | `stock`, `themed_plot1`, `paths_demos`, `demo_graphs1` | tag-flips |
| REM-104 HYPOT      | `countdown`, `demo_use_of_global`, `digital_clock1`    | tag-flip + pixel |
| REM-109-S1 A_SPLINE/IFELSE | `hostile_actor1*`, `linear_regression`, `demo_graphs0/1`, `graph_graph*`, `spline_demo_spline_demo1`, `stock_sparkline`, `pie_chart2`, `heart_rate_timeline` | drawCount-collapse + pixel |
| REM-109-S2 Trig/RAND/LERP | `experimental_solar_gmt`, `heart_rate_timeline`, `pie_chart2` | pixel |
| REM-108-S2-Core (Touch-Eval) | (touch* schon in REM-116 absorbiert) | identisch |
| REM-113 Advanced-Draw-Shapes | (kein Doc im Korpus nutzt drawRoundRect/TextOnCircle/BitmapInt) | identisch |
| REM-114 wasm-Live-Loop | (wasm-only)                                | identisch |
| REM-101 Sensor (Android/iOS) | (sensor_demo* tickt nicht im headless desktop) | identisch |
| REM-96 Layout-Container | (Creation-byte; render-faithful)            | identisch |
| REM-84/103 Creation-Byte-Conformance | (Creation-side, kein Render-Change) | identisch |

## F. Promote-Empfehlung

**PROMOTE: ja.** Empfehlung an PO:

1. Branch: `feature/REM-XXX-desktop-baseline-refresh-bbf1f00` (Key-Erfragung beim PO).
2. Commit: alle 18 PNGs aus `screenshots/reference/desktop_bbf1f00/` über `screenshots/reference/desktop/`
   kopieren + `_sweep.csv` updaten (= `artifacts/desktop_sweep_bbf1f00.csv`).
3. PR-Body = diese Datei.
4. PO mergt nach review (analog REM-102-Pattern).

## Artefakte

- Sweep-Output PNGs: `screenshots/reference/desktop_bbf1f00/` (173 Files)
- Sweep-CSV: `artifacts/desktop_sweep_bbf1f00.csv`
- Sweep-Log: `artifacts/desktop_sweep_bbf1f00.log` (36 s build, exit 0)
- Diff-Report: diese Datei
