# Cross-Platform Render-Parität — Abschluss-Record (Android ↔ iOS)

> **Stand:** develop `be636e9` (current-vs-current, beide Seiten current, nach Gradient-Fix `9a5422b`).
> **Harness:** `parity_compare.py` + `parity_sweep.py` (Stufe-B perzeptueller Pixel-Diff, `cropOn rc-canvas`,
> ±2px-Density-Resize). Erzeugt von test-2 (QA). Der eine-Codebasis→Android==iOS-Beweis über den 140-Doc-Korpus.

## 🎯 Render-Parität: **129 / 131 = 98.5%**
(von den **gerenderten** Docs; 9 both-blank/nicht-gerendert ausgeschlossen.)
**Die einzigen 2 verbleibenden FAILs sind deferred Features** — d.h. für alle gebauten Features ist die
Android↔iOS-Render-Parität effektiv vollständig.

| Bucket | n | Bedeutung |
|---|---|---|
| **PASS** | 97 | clean pixel-identisch (breach ≤2%, kein Cluster) |
| **TEXT** | 32 | diffuse Cross-Skia-Font/AA/thin-line-Divergenz (erwartet, Heatmap-belegt) |
| **BLANK** | 9 | beide Plattformen blank — **nicht gerendert** (Feature-Gaps, aus Parität ausgeschlossen) |
| **FAIL** | 2 | nur noch deferred Features (s.u.) |
| ERROR | 0 | — |

## Die 2 verbleibenden FAILs — beide deferred Features (kein Parität-Bug)
- `demo_bitmap_drawing_bit_draw2` — iOS-blank (Bitmap-Draw deferred).
- `flow_control_checks_test_conditional` — gefüllter Kreis divergiert (Conditional/Fill deferred).

## ✅ Gefixt (Gradient-Fix `9a5422b`): die 3 Path-Fill-Divergenzen sind weg
`stock_sparkline` (34%→1.6%, TEXT) · `hydration_wave` (17%→1.0%, PASS) · `thumb_wheel2` (67%→0.0%, **pixel-identisch**).
Android füllt jetzt Path/Gradient korrekt. (Nebenbei: Throws im Korpus 21→2 durch denselben NaN-Gradient-Fix.)

## BLANK-9 (nicht gerendert auf beiden — Feature-Gaps, kein Parität-Thema)
`base · c_modifier_fill_max_size · c_modifier_fill_parent_max_size · c_modifier_vertical_scroll ·
c_state_layout · plot3 · plot4 · stock · themed_plot1`

## TEXT-32 (akzeptabel, Cross-Skia Font/AA)
Diffuse Glyph-Kanten / Gridlines / 1px-Linien-Offsets / AA — Heatmaps zeigen Kanten, keine soliden Blöcke.
Enthält `c_fit_box` als **Produkt-Call-Override** (PO 2026-06-26): die Fit-Box auto-sized „SCALED" nach
Plattform-Font-Metriken (A↔iOS inhärent verschieden) → Text-Klasse, kein Geometrie-Bug; vom Area-
Diskriminator nur über-geflaggt, weil großer Bold-Text einen Glyph-Block >5% bildet.

## Verdikt-Logik (Harness)
- `BLANK`: Android-Golden >99.5% uniform UND breach ≤2% → beide blank.
- `PASS`: breach ≤2% UND maxClusterFrac ≤0.5%.
- `FAIL`: maxClusterFrac >5% (**SOLIDER Block** — Struktur via Fläche, nicht Konnektivität) ODER breach >20%.
- `TEXT`: sonst (kleiner Cluster / thin-line / diffus, breach ≤20%).
- `TEXT_PRODUCT_OVERRIDE`: bekannte Text-Klasse-Docs, die der Area-Diskriminator über-FAILt (aktuell `c_fit_box`).

## Bekannte Schwäche + Follow-ups (nicht-dringend)
- **Area-Diskriminator über-FAILt großen Bold-Text** (Glyph-Block kann >5% Fläche füllen). Proper Fix =
  **glyph-edge-density-Guard** (Cluster-Fill-Ratio: solide Blöcke füllen ihre Bbox, Text nicht) → ersetzt
  das `TEXT_PRODUCT_OVERRIDE`. Follow-up.
- **(a) px-exaktes Canvas-Sizing** (statt `.dp`) für exakte Cross-Platform-Pixel → entfernt den
  ±2px-Density-Resize. Präzisions-Follow-up (dev), nicht-dringend.

## Reproduktion
`cd screenshots/parity && python3 parity_sweep.py ../reference/android ../reference/ios --diff-out <dir>`
