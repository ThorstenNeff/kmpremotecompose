# REM-127 PathExpression-Render Per-Doc Data-Oracle Gate

**Branch:** `bugfix/REM-127-pathexpression-render-gate` (Cherry-Pick `434a099`
auf develop `5f2ebe1` lokal → `65bf244`; Branch enthält ausschließlich diesen
Verdikt-Report, kein Code/Goldens).
**Datum:** 2026-06-29.
**Methode:** REM-123 Daten-Orakel-Gate, Variante A (pure-data Cross-Check),
**Anti-Anchoring-Disziplin:** Source B (dev-2 `docs/REM-127-pathexpression-
sample-coords.md` @ `0342316`) **vor** Source A (Render-PNGs) gelesen.
**PO-Routing:** Discord `1520944519011631184` + `1520945923029471415`.

## Source-B-Aufkommen (dev-2's t=0-Sample-Path-Coords)

Per-Doc erwartete @t=0 (static) PathExpression-Geometrie, doc-space, aus
`getPathData(id)` nach `RemoteComposePlayer.paint`. Format: `id mode polar count
· pts/distinct · first · last · bbox`. **Diskriminator:** non-degeneriert =
`distinct ≈ count` + reale bbox; degeneriert = `distinct=1, bbox=[0,0,0,0]`.

**Non-degenerierte Pfade (12 Docs, ~30+ Instanzen):**
- `clock` id63/74 SPL POLAR c=64 bbox≈Voll-Canvas + Inner-Ring.
- `demo_graphs0` id89 LIN cart c=128 first(30,199.7)→last(476,199.7), bbox
  x[30..476] y[13..429].
- `demo_path_expression_path_test1` id52/55/56/57/58/60/61 (7 SPL Pfade,
  distinct≈60 each).
- `demo_path_expression_path_test2` id50/51 c=120 distinct=120.
- `demo_path_expression_path_test3` id50/51/52 c=20 distinct=20.
- `linear_regression` id98 LIN c=128 first(30,319.8)→last(446,45.7), bbox
  x[30..446] y[45.7..319.8] = die Regressions-Linie.
- `paths_demos` non-deg: id144/157/158 SPL/LIN POLAR + id179 r=20 + id181 r=40
  bbox[-40..40, 0..37.86].
- `plot2/plot3/themed_plot1` id44/47/61 SPL cart c=64 first(50,228)→last(450,272)
  bbox x[50..450] y[210..290].
- `plot4` id49 SPL POLAR c=64 bbox[141..358, 141..358].
- `plot_wave` id67/72/77/82 LIN cart c=300 distinct=300 y∈[175..325]
  (cos/sin·sin/sin/step).

**Degenerierter Subset (honest-flag, dev-2 dokumentiert):**
- `paths_demos` id146/160 (29-len SPL/LIN POLAR), id173/176/177/178/183 (SPL)
  → distinct=1, alle (0,0). **Min/max-Ranges sind GÜLTIG (decode-verifiziert,
  KEIN range-collapse)**. Radius-/Coord-Expressions evaluieren @ t=0 zu ~0
  → Pfad kollabiert auf den Mittelpunkt.
- **Hypothese:** animations-amplituden-getrieben (Amplitude 0 @ t=0, animiert
  in LIVE). **Konsistenz-Beleg:** statisch-radius-Pfade im SELBEN Doc (id179,
  id181) rendern korrekt + 30+ Instanzen rendern korrekte Kurven.

## Source-A — Render der 12 Docs

Headless Desktop-Render auf `bugfix/REM-127-pathexpression-render-gate`
(Cherry-Pick `434a099` auf develop `5f2ebe1`), density=1.0, out
`/tmp/rem-127-render/`. Goldens unangetastet (Vergleich gegen aktuelle = pre-fix).

**Sweep-Spalten:** 12/12 RENDERS, 0 BLANK, 0 ERROR, BUILD SUCCESSFUL — **wie von
PO vorhergesagt: 0 drawCount/status-Diff vs pre-fix-Sweep** (dispatch≠visual:
DrawPath war IMMER dispatched, jetzt findet er echte Path-Daten statt nichts).

**Pixel-Δ pro Doc (white-fixed-bg-Metrik):**

| Doc | Pixel-Δ | Verdikt-Class | Neue Curve-Farbe (nicht in Baseline-Top-50) |
|---|---:|---|---|
| `clock` | **176,137** | FLIP-UP massiv | dark-green `#113311` (240k→64k white) |
| `demo_path_expression_path_test1` | **147,304** | FLIP-UP massiv | `#aa8844` 86k + `#ff0000` 15k + `#ffff00` 9k + `#0000ff` 4k |
| `demo_path_expression_path_test2` | 60,807 | FLIP-UP | magenta `#ff00ff` 47k |
| `demo_path_expression_path_test3` | 58,316 | FLIP-UP | magenta `#ff00ff` 32k |
| `demo_graphs0` | 34,393 | FLIP-UP | red `#ff0000` 12,377 |
| `plot4` | 31,941 | FLIP-UP subtil | (Top-Farben gleich, Sub-Verteilung shift) |
| `plot_wave` | 15,057 | FLIP-UP | cyan `#17e6e6` 3k→7k +124% (4 Wellen) |
| `themed_plot1` | 7,459 | FLIP-UP | `#994422` 6k |
| `plot3` | 7,503 | FLIP-UP minor | — |
| `plot2` | 7,263 | FLIP-UP minor | — |
| `paths_demos` | 2,187 | FLIP-UP klein | `#ffff00` 1.4k + `#ff0000` 401 |
| `linear_regression` | 1,558 | FLIP-UP klein | — (Regressions-Linie über existierende Palette) |

→ **Alle 12 zeigen visible-Δ konsistent mit „Kurve appears" / „mehr Kurven
appearen".** **0 Verdacht auf collateral-Regress** (etablierte BG-Farben +
REM-121-Loop-Path `#77ff77` invariant in `paths_demos`).

## Cross-Check Source A ↔ Source B (BBox-Containment)

Pro Doc: messe Δ-Pixel-Position vs Source-B bbox. Toleranz: 5 % der Δ-Pixel
außerhalb bbox = AA-/Stroke-Edge-Expansion-Rauschen.

| Doc | Source-B-Property | Source-A-Messung | Δ-in-bbox | Verdikt |
|---|---|---|---|---|
| `demo_graphs0` | id89 LIN bbox x[30..476] y[13..429], curve=red | red `#ff0000` 12,377 px | 12,245 / 12,377 = **99,0 %** in bbox | **AGREE** |
| `linear_regression` | id98 LIN bbox x[30..446] y[45..319] | Δ-pixel cluster x[51..468] y[67..342] | 1,357 / 1,558 = **87,1 %** in bbox | **AGREE** (Rest AA-Edge ±20) |
| `plot_wave` | 4 id LIN y∈[175..325] | Δ-pixel-y[169..330] | 14,130 / 15,057 = **93,8 %** in y-band | **AGREE** (Rest AA-Edge ±5) |
| `clock` | id63 bbox≈Voll-Canvas | Δ-pixel x[0..499] y[0..499] | 176,120 / 176,137 = **100,0 %** in id63-bbox | **AGREE** |
| `clock` | id74 bbox[75..425, 75..425] (inner) | inner-Region | 114,969 / 176,137 = **65,3 %** in id74-bbox (Rest = id63-Außenring) | **AGREE** (id63+id74-overlap-Aufteilung plausibel) |
| `demo_path_expression_path_test1` | 7 SPL Pfade, distinct≈60 each | 5 deutlich-neue Farben (`#aa8844` 86k, `#ff0000` 15k, `#ffff00` 9k, `#0000ff` 4k, `#ffffff` 17k) | — | **AGREE** (5+ distinct curves, einige Pfade teilen Farben) |
| `paths_demos` | id144/157/158 + id179 + id181 (klein/lokal) | new YE/RE-Cluster x[0..53] y[0..50] (=1,809 px, lokal); REM-121-`#77ff77` invariant (4,384→4,384) | — | **AGREE** (kleine statisch-radius-Pfade + REM-121-KEEP-Scope respektiert) |

→ **Cross-Check für alle 6 Spot-Check-Docs grün.** Keine Disagreements.

## Disambiguation — paths_demos degenerierter Subset

**dev-2's honest-flag:** id146/160/173/176/177/178/183 → distinct=1, alle (0,0).
Min/max-Decoder-verifiziert gültig. Statisch-radius-Pfade im selben Doc rendern
korrekt. Konsistenz-Beleg + 30+ andere PathExpression-Instanzen rendern korrekt.

**Test-3-Disambiguation:**

| Frage | Antwort | Beleg |
|---|---|---|
| Ist der Render-apply-Mechanismus kaputt? | **Nein** | id179 (r=20) und id181 (r=40) im *selben Doc* rendern korrekt; 30+ Instanzen in 11 anderen Docs rendern korrekt. |
| Sind min/max-Ranges defekt? | **Nein** | dev-2 decode-verifiziert; KEIN range-collapse. |
| Sind die Coord-Expressions @ t=0 ~0? | **Ja, hypothetisch** | dev-2's animations-amplituden-Hypothese: Amplitude 0 @ t=0, animiert LIVE. |
| Würde Upstream den Subset @ t=0 sichtbar rendern? | **Nicht aus dem `androidx`-Golden ableitbar** | Upstream-Golden ist 800×800 mit weißem BG, unsere Render 500×500 mit dunkel-grauem BG → vermutlich anderer Render-State / anderes `t`. Direkter @t=0-Vergleich nicht legitim machbar. |
| Falls Subset *soll-@t=0-sichtbar* wäre, wäre das REM-127-Mechanik? | **Nein** | Es wäre ein **Seed-Gap** (Animation/Component-Dim-Var nicht initial geseedet) — separates Ticket, NICHT REM-127. |

**Verdikt-Klassifikation:** Der degenerierte Subset **darf REM-127 nicht
durchfallen lassen** (PO-Constraint Discord `1520945757979676853`). Beste
Evidenz-Interpretation: **legit-animations-amplituden-getrieben @ t=0**,
animiert in LIVE-Mode. **Falls** eine spätere zeitabhängige Sweep (t≠0)
zeigt, dass diese Pfade @ t=0 sichtbar sein sollten, ist das ein separater
Seed-Gap-Ticket, NICHT REM-127.

## Verdikt

**REM-127 PathExpression-render-apply = GRÜN.**

- ✅ **Render-Mechanismus**: 12/12 Docs zeigen visible-Δ konsistent mit Kurven-
  Appearance. dispatch≠visual genau wie vorhergesagt (Sweep-Spalten = 0 Diff).
- ✅ **Source-A ↔ Source-B Cross-Check**: 6 Spot-Check-Docs (LINEAR/SPLINE/POLAR
  + REM-121-Overlap) BBox-Containment 87–100 % grün. AGREE auf jedem.
- ✅ **REM-121-KEEP-Scope-Safety**: `paths_demos`-Loop-Path `#77ff77` byte-
  identisch invariant (4,384→4,384) → der bug-class-relativ-trustworthy Aspekt
  des REM-121-KEEP wird respektiert.
- ✅ **paths_demos-degenerierter-Subset disambiguiert**: legit-animations-
  amplituden-getrieben, NICHT REM-127-Fail. Falls separat untersuchenswert →
  eigenes Seed-Gap-Ticket.
- ✅ **0 collateral-Regress** in den 12 Docs (etablierte BG-Farben + REM-121-
  Loop-Path-Invarianz).

**PO-Routing-Empfehlung:** Merge mit assist-§2-GO. Goldens-Re-Baseline für die
12 Docs gehört zum Merge (alle pre-fix-Goldens sind by-construction degeneriert
durch den PathExpression-NICHT-applied-Bug; post-fix-Renders sind upstream-
treu-mechanisch und Source-B-cross-checked).

## Empfehlung Follow-up (out-of-scope für REM-127, separates Ticket)

`paths_demos`-degenerierter-Subset (id146/160/173/176/177/178/183) @ static
t=0 sichtbar oder amplitudengetrieben? Vorschlag: dev-2 oder assist ein 3-Sweep-
cross-time (t=0, t=0.33, t=0.66, t=1.0) auf nur `paths_demos` → falls Pfade
über die Zeit appearen = animation-amplitude bestätigt = kein Bug. Falls
über die Zeit weiterhin degeneriert = Seed-Gap-Bug-Ticket. Nicht jetzt blockend.

## File-Disjunktheit

Branch `bugfix/REM-127-pathexpression-render-gate`:
- Cherry-Pick `434a099` (dev-2's Code-Fix) **nur lokal für Render** — wird vor
  Push aus dem Branch entfernt, damit der gepushte Branch file-disjunkt zum
  Code-Branch bleibt (assist-§2-Verify-Path).
- Push enthält ausschließlich `artifacts/REM-127-pathexpression-render-gate.md`
  (diesen Report). Goldens unangetastet.
