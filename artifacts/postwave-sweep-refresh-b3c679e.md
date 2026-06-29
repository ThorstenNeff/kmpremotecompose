# Post-Welle-Baseline-Refresh — develop `b3c679e`

**Branch:** `chore/postwave-sweep-refresh`. **Datum:** 2026-06-29.
**Auftrag:** PO-Discord `1520962933574865119` — reiner Aggregat-Refresh des
`_sweep.csv`-Ankers vom stale REM-117-Anker (`8e8013d`) auf den aktuellen
develop-Tip (`b3c679e`, post-E6-MVP). **Kein neuer Gate-Verdikt.**

## Sweep

Headless Desktop-Render auf develop `b3c679e`, `--density 1.0`, Output
`/tmp/postwave-refresh/`. **BUILD SUCCESSFUL in 34s.**

Resultate:
- **173/173 RENDERS**, 0 BLANK, 0 ERROR.
- 0 drawCount-Drops (alle Δ monoton positiv).
- 92/173 Docs mit drawCount-Δ (wave-typisch, mehr Ops dispatcht).
- 3 status-Reklassifikationen (Harness-only, PNG byte-identisch):
  `player_info`, `small_animated`, `spline_demo_spline_demo1` — bekannt aus
  `3940280`-Sweep.

## PNG-Byte-Identity-Diff (Goldens-Stand vs neuer Render)

**165 / 173 PNGs byte-identisch**. 8 PNG-Δ → klassifiziert:

### A. Carry-Over PNG-Δ aus dem `3940280`-Sweep (nicht re-baselined, bekannt)

| Doc | Pixel-Diff | Klasse aus `3940280`-Gate | Status |
|---|---:|---|---|
| `graph_graph2` | 2,447 | DRIFT seit REM-121 (im AA-Rauschen ~2%) | **carry-over** |
| `hydration_wave` | 1,166 | DRIFT seit REM-121 (im AA-Rauschen ~3%) | **carry-over** |
| `moon_phase_dial` | 35,155 | DISPATCH-ONLY +1 non-bg-px (coloring-shift, ±0 Content) | **carry-over** |
| `stock` | 79 | REM-124-EFFEKT (+21 px Sparkline sichtbar) | **carry-over, jetzt schrumpft auf 79 px-Diff** |

→ Alle 4 in dieser Gruppe wurden im REM-117-postwave-Sweep-Verdikt
(`bugfix/REM-117-postwave-sweep-3940280` `8bd048e`) bereits klassifiziert
und vom PO als „QA-no-action" akzeptiert. **Kein neuer Flag-Bedarf** — sie
sind weiterhin innerhalb der „AA-Drift / DISPATCH-ONLY / REM-124-positiv"-
Toleranz. Goldens werden hier **NICHT** angepasst (PO sagt „per-doc-Goldens
sind aktuell").

### B. NEU seit `3940280`-Sweep — Flag-Kandidaten

| Doc | Pixel-Diff | Klasse | Hypothese |
|---|---:|---|---|
| **`c_modifier_horizontal_scroll`** | 40,574 | **VISIBLE-Δ** | REM-128-Modifier-MVP-Korrelation |
| **`c_modifier_vertical_scroll`** | 14,251 | **VISIBLE-Δ** | REM-128-Modifier-MVP-Korrelation |
| **`color_list`** | 21,267 | **VISIBLE-Δ** | REM-128-Modifier-MVP-Korrelation (List-Layout) |
| `color_table` | 70 | **DISPATCH-ONLY (≈0 visuell)** | wahrsch. REM-128-Marginalia, unter Noise-Floor |

**Welle-Window**: `3940280..b3c679e` enthält REM-127 (PathExpression — alle 12
Docs bereits re-baselined ✓), REM-128 (E6 Compose-Creation MVP + E6-S3a
**RemoteModifier T1: Width/Height/Background** + E6-S3b Scroll-Layout), REM-129
(2 Goldens bereits re-baselined ✓).

**REM-128-Korrelations-Beleg:**
- `c_modifier_horizontal_scroll` + `c_modifier_vertical_scroll` sind explizit
  Modifier-Test-Docs → REM-128-S3a-Width/Height/Background-Pipeline berührt sie
  direkt. **Direktes Routing-Indiz.**
- `color_list` nutzt vermutlich List/Grid-Layout, der über Modifier konfiguriert
  wird → S3a-Modifier-Pipeline kann visuell durchschlagen.
- `color_table` 70 px-Diff = unter AA-Edge-Rauschen, vermutlich Op-Order-
  Marginalie aus REM-128, kein echtes visuelles Δ.

**Per PO-Flag-Trigger** („Divergenz NICHT erklärt durch REM-121/124/125/127/129
→ flag als potenziellen neuen Gap"): REM-128 ist **nicht** in der erklärten-Set-
Liste des PO-Routings. Daher:

**FLAG für PO-Routing-Entscheidung:**
- Sind die 3 VISIBLE-Δ legit-Effekt von REM-128-Modifier-MVP-Werk (= einfach
  re-baseline) oder unerwartete Side-Effects (= eigenes Gate vor Re-Baseline)?
- `color_table` ist unter Noise-Floor und unkritisch.

Ich treffe **keine** Re-Baseline-Entscheidung hier — das ist PO-Routing.

## Re-Anker `_sweep.csv`

`screenshots/reference/desktop/_sweep.csv` neu geschrieben aus
`/tmp/postwave-refresh/_sweep.csv` (sweep auf `b3c679e`). Inhalt:
- 173/173 RENDERS, 0 BLANK, 0 ERROR
- Spalten unverändert: `doc,surfaceW,surfaceH,drawCount,status,deferredTags,note`
- drawCount-Werte spiegeln post-Welle-Stand wider (92 Docs mit drawCount-Δ)
- 3 BLANK→RENDERS-Status-Korrekturen aus Harness-Classification (PNG byte-
  identisch, Pipeline unverändert)

## File-Disjunktheit

Branch `chore/postwave-sweep-refresh` enthält ausschließlich:
- Neue `screenshots/reference/desktop/_sweep.csv` (Re-Anker).
- Diesen Report unter `artifacts/postwave-sweep-refresh-b3c679e.md`.
- **Keine PNG-Updates** (carry-over-Goldens bleiben, neue REM-128-Flag-Kandidaten
  werden NICHT vor PO-Routing re-baselined).
- Keine Code-Änderungen.
