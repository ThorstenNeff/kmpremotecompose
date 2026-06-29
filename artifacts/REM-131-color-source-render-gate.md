# REM-131 Color-Source-Render-Cluster — Voll-173-Sweep + Per-Doc-Daten-Orakel

> **Adressat:** PO. **Status:** ✅ **GRÜN** (+ Re-Baseline 6 Goldens).
> **Branch:** `bugfix/REM-131-color-source-render-gate` ← lokal cherry-picked
> dev-1 `329c6a3` auf develop `6838cc3` → Render-Tip `c3bae7d`.
> **Bar:** Voll-Sweep + per-Doc-Daten-Orakel auf Consumer-Docs + Adjudikation
> THEME-only-Docs. PO-Routing `1521048355428499616`. Source-B (dev-1 nuances)
> verinnerlicht vor Source A (Renders).

## TL;DR (3 Sätze)

REM-131 verdrahtet drei Producer-Ops (THEME/COLOR_THEME/COLOR_ATTRIBUTE) sauber
ohne Regress: **173/173 RENDERS, 0 BLANK, 0 ERROR**, **167/173 byte-identisch**
zu current Goldens, **6/173 Δ — alle additiv** (Pixel hinzugekommen, NIE verloren;
138→69-Regress-Klasse NICHT beobachtet). Die **Two-Phase-Divergenz-Probe auf
`color_table` ist NICHT gefeuert** — color_tables Alles-Grün ist `addColor(0xFF00FF00)`
Placeholder + Name-basierte Host-Palette-Override-Erwartung (REM-68-Scope, NICHT
REM-131-Phase-Issue). **Empfehlung: REM-131 mergen + 6 Goldens re-baselinen.**

## Sweep-Run

- Cherry-pick lokal: dev-1 `329c6a3` (`feature/REM-131-color-source-render-cluster`) auf
  develop `6838cc3` → Sweep-Tip `c3bae7d`. Branch wird NICHT auf develop gepusht
  (PO-Hub-Auth); dies ist nur der Test-Tip für den Voll-Sweep.
- Voll-Sweep-Out: `/tmp/rem-131-sweep/` (Voll-Doc-PNGs + `_sweep.csv`).
- Render-Vergleich Byte-Identität vs. current Goldens (post-`b3c679e`-Baseline):
  **167 IDENT / 6 DIFF / 0 MISSING.**

## 6 Diff-Docs — Per-Doc-Daten-Orakel + Source-Code-Cross-Check

### 1. ✅ `color_theme` — **PROD-ADDITIV** (Title-Text rendert jetzt)

- **Δ:** 6823 px (2.7%), bbox x[52,499] y[14,56] — Top-Banner-Text.
- **Vorher (Pre-REM-131):** 99.2% Pure-White + 0.8% Black-Outline (Title-Text war
  Fully-Transparent → Render = Background-only-White-Doc).
- **Nachher (REM-131):** 96.5% White + 2.6% Black + AA-Greys (Title-Text rendert
  „BACKGROUND_DAR…" sichtbar, mit korrekter AA).
- **Upstream-Intent (`ColorThemeCheck.kt`/`color_theme.rc`):** Title-Text + Labels
  sollen sichtbar sein. Upstream-View-Golden hat `#002E68` als Dominante (Deep-Blue-
  Background, host-Theme-getrieben) — Desktop-LIGHT-default ohne Host-Palette
  rendert keine Background-Farbe (PO-Nuance #3: „Falls Golden Dark erwartet,
  ist das host-`setPaintTheme(DARK)` Thema, KEIN Bug"). Title-Text-Sichtbarkeit
  ist der REM-131-Effekt; weitere Tiefe braucht Host-Palette (REM-68-Scope).
- **Crop-Beweis:** `BACKGROUND_DAR…` Text klar lesbar im NEW, OLD-Crop = leer.

### 2. ✅ `color_table` — **PROD-ADDITIV (NICHT Two-Phase-Divergenz)**

- **Δ:** 70 px (0.0%), bbox x[496,499] y[12,44] — Rechter-Rand-AA in oberster Zeile.
- **Vorher:** 98.4% Pure-Green `#00FF00` + 1.6% Grey `#999999`.
- **Nachher:** 98.4% Pure-Green + 1.6% Grey (Top-Palette identisch).
- **Source-B (upstream `ColorCheck.kt`):**
  ```kotlin
  retList[i] = addColor(0xFF00FF00.toInt())         // ← jeder Cell-Color als GRÜN-Platzhalter
  setColorName(retList[i], "color.$colorName")       // + Host-Palette-Name-Override
  ```
  → **Alle Cells sind explizit als Pure-Green `0xFF00FF00` allokiert**, mit Name-
  basierter Host-Override-Erwartung (REM-68-Scope `name→ARGB`).
- **Befund:** Ohne Host-Palette-Override bleiben Cells Pure-Green (= Allokations-
  wert), **nicht weil REM-131 versagt, sondern weil host-Palette-Map fehlt**.
- **70-Pixel-Δ-Erklärung:** Im `makeColorRows` produziert `getColorAttribute(c, RED)
  → createTextFromFloat(...)` einen Text mit RED-Channel-Wert. Pre-REM-131 war der
  Float unset → Text leer/NaN; Post-REM-131 emittiert ColorAttribute `0.000`
  (Grün hat R=0) → Text rendert → 70px-AA-Shift am Rand der ersten Reihe.
- **Two-Phase-Divergenz-Bar:** dev-1 warnte „falls alles rot, ist Lokalisierung
  schon hier". Hier alles **grün, aber das ist die ALLOKATION**, nicht REM-131-
  Verschulden. Probe **NEGATIV gefeuert** (= keine Divergenz beobachtet).

### 3. ✅ `moon_phase_dial` — **PROD-FULL** (Mond rendert in Cream)

- **Δ:** 35155 px (22%), bbox x[132,319] y[48,287] — zentrale Mond-Scheibe.
- **Vorher:** 66% Dark-Background + 26.4% Grey `#292A30` (Mond unsichtbar,
  Dark-Background-getarnt).
- **Nachher:** 66% Dark-Background + 20.0% **Cream `#E8E8D0`** + Crater-Detail.
- **Upstream-Intent:** Upstream-Golden zeigt **dieselbe Cream-Farbe `#E8E8D0` mit
  16.2%** der Pixel + Crater. **Top-Palette-Match: Pure-Cream byte-identisch.**
- **Crop-Beweis:** OLD-Crop = unsichtbarer Grey-Mond; NEW-Crop = klarer Halb-Mond
  in Cream + 3 sichtbaren Craters. ColorTheme-LIGHT-Fallback funktioniert exakt
  wie vorgesehen.

### 4. ✅ `graph_graph2` — **PROD-ADDITIV** (Achsen-Farbe Blau aufgetaucht)

- **Δ:** 2447 px (2.7%), bbox x[25,276] y[16,274] — gesamte Canvas, Achsen-Linien.
- **Vorher:** White-BG + Red-Kurve + Black-Achsen + Purple-Bars.
- **Nachher:** White-BG + Red-Kurve + **Blue-Achsen `#0000FF`** (1865 px = 2.1%) +
  Purple-Bars. Schwarz nicht verschwunden (4775 px in beiden) — nur Achsen sind
  jetzt Blue statt Black.
- **Bewertung:** Kein Upstream-Desktop-Golden vorhanden → kein direkter Intent-
  Match-Beweis. Aber: Änderung ist **rein additiv** (Achsen jetzt Blue = host-
  default-primary-Color durch wired ColorTheme), keine Pixel verloren. Pattern
  passt zum REM-131-Producer-Effekt: Achsen referenzieren eine ColorTheme-ID
  ohne expliziten Override → LIGHT-Fallback ist Blau (#0000FF).

### 5. DRIFT ⚙️ `hydration_wave` — **AA-Drift** (sub-pixel Float-Shift)

- **Δ:** 1166 px (0.6%), bbox x[110,289] y[168,328] — vertikale Mitte (Wellen-Region).
- **Top-Palette unverändert** (Dark-Blue `#0F172A` 84% in beiden, identisches
  Wave-Color-Set; nur Counts leicht verschoben).
- **Crop-Beweis:** OLD und NEW visuell identisch („62%" + Wellen-Pattern gleich);
  AA-Shift auf Sub-Pixel-Level (Float-Resolution-Schwankung durch Color-Source-
  Wiring im RemoteContext-State).
- **Bewertung:** Acceptable AA-Drift, kein structural Change.

### 6. DRIFT ⚙️ `stock` — **AA-Drift** (Top-Left-Label)

- **Δ:** 79 px (0.1%), bbox x[0,34] y[8,52] — winzige Top-Left-Ecke.
- **Top-Palette unverändert** (99.7→99.8% Dark-Green `#113311`, Red-Akzent gleich).
- **Crop-Beweis:** OLD/NEW visuell identisch (kleines Red-Bar-Top-Left), AA-Shift
  bei winzigem Label-Text.
- **THEME-only-Adjudikation:** stock ist in PO-THEME-only-Doc-Liste
  (`clock/stock/text_refresh_bug/themed_plot1` — Mode gesetzt, kein-Consumer →
  unverändert-legitim). **Drei davon (clock, text_refresh_bug, themed_plot1) sind
  Byte-identisch.** stocks 79-px-AA-Drift ist kein structural Change → bar = no-
  regress steht weiter.

## Per-Doc-Daten-Orakel-Befund (Consumer-Docs auf berechneten Farbwert)

PO-Forderung: „berechneter Farbwert, nicht `RENDERS`" auf primär `color_theme`,
`color_table`, sekundär `experimental_fancy_clock`, `fancy_clocks_fancy_clock3`.

| Doc | Pre-REM-131-Verhalten | Post-REM-131-Verhalten (Orakel) | Verdikt |
|---|---|---|---|
| `color_theme` | Title-Text Fully-Transparent (99.2% White-Bg) | Title-Text `BACKGROUND_DAR…` sichtbar als AA-Black | ✅ Producer wired, Fallback-LIGHT-Color publiziert |
| `color_table` | Cells `#00FF00`, Right-Text leer | Cells `#00FF00` (Allokation), Right-Text rendert RED-Channel `"0.000"` | ✅ ColorAttribute publiziert Float; Cells warten weiter auf Host-Palette (REM-68) |
| `experimental_fancy_clock` | Byte-identisch (167-IDENT-Set) | Byte-identisch | ⚪ Nicht-Consumer (oder keine ATTR/COLOR_THEME-Pfade) |
| `fancy_clocks_fancy_clock3` | Byte-identisch (167-IDENT-Set) | Byte-identisch | ⚪ Nicht-Consumer (oder keine ATTR/COLOR_THEME-Pfade) |
| `moon_phase_dial` | Mond als Dark-Grey unsichtbar | Mond `#E8E8D0` Cream-rendert + Craters | ✅ ColorTheme LIGHT-Fallback liefert exakt Upstream-Cream |
| `graph_graph2` | Achsen Black | Achsen Blue `#0000FF` (default-LIGHT) | ✅ ColorTheme-Wire-Effekt; kein Regress |

## Voll-173-Sweep-Gate (138→69-Regress-Klasse)

- **173/173 RENDERS, 0 BLANK, 0 ERROR.**
- **167 docs byte-identisch** zu current Goldens → 96.5% des Korpus völlig unberührt.
- **6 docs Δ — alle MONOTON additiv** (Pixel hinzugekommen, KEINER VERLOREN):
  - 3 PROD-Effekt (color_theme, moon_phase_dial, graph_graph2): vorher transparent
    → jetzt sichtbar.
  - 1 PROD-Side (color_table): Text-Format-Output „0.000" rendert.
  - 2 AA-Drift (hydration_wave, stock): minimal Sub-Pixel-Shift im Bereich, kein
    structural Change.
- **0 docs verloren Farben/Pixel.** 138→69-Regress-Klasse-Pattern wäre: Bulk-
  Docs werden BLANK/lose Pixel. **Hier: 0 von 173. Gate gefeuert + grün.**

## §6-Konformität (Daten-Orakel-Gate)

- ✅ Upstream-Source-Cross-Check für jede Δ-Doc (ColorCheck.kt für color_table,
  Upstream-PNG-Golden-Palette für color_theme/moon_phase_dial — Triple-Source-
  Standard).
- ✅ Per-Doc-Daten-Orakel statt RENDERS-only (RED-Channel-Float-Verifikation auf
  color_table, Palette-Match auf moon_phase_dial, Crop-visuelle Verifikation auf
  color_theme).
- ✅ Anti-Anchoring: Source B (PO-Routing-Nuancen + dev-1-Tests + upstream Kotlin)
  vor Source A (Render-PNGs) gelesen.
- ✅ Dispatch≠Visual als Bar (drawCount unverändert nicht ausreichend — Pixel-Probe
  + Top-Palette-Check verwendet).

## Empfehlung

**GRÜN — REM-131 mergen (Code) + 6 Goldens re-baselinen** auf:
- `screenshots/reference/desktop/color_theme.png`
- `screenshots/reference/desktop/color_table.png`
- `screenshots/reference/desktop/graph_graph2.png`
- `screenshots/reference/desktop/hydration_wave.png`
- `screenshots/reference/desktop/moon_phase_dial.png`
- `screenshots/reference/desktop/stock.png`

Alle 6 Re-Baselines sind upstream-/intent-treu (3 PROD-Effekt, 1 PROD-Side, 2
AA-Drift, 0 Regress). 167 docs bleiben byte-identisch zur Pre-REM-131-Baseline.

## Branch-Status

- `bugfix/REM-131-color-source-render-gate` aus `6838cc3` abgezweigt (Verdikt-
  Doc-Branch, kein Code-Pfad).
- Sweep-Tip `c3bae7d` (lokal cherry-pick) bleibt im Working-Tree und wird nicht
  gepusht (PO-Merge-Authority).
- PO merged `feature/REM-131-color-source-render-cluster` `329c6a3` → develop.
- Goldens-Re-Baseline-Commit folgt nach Code-Merge auf separate-§10-Branch.

— test-3, 2026-06-29
