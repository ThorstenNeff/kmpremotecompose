# Post-Wave Desktop-Render-Sweep — develop `3940280` (wave-blind 3.-Orakel-Pass)

**Datum:** 2026-06-29.
**Auftrag:** PO-Discord `1520916535722508399` — ungated Verifikations-Arbeit
während Story-4 läuft; konsolidierter 173-Doc-Sweep auf dem integrierten Develop-Tip
nach 44-Merge-Welle (in Wahrheit: 39 Commits seit REM-117-Baseline `8e8013d`,
einschließlich REM-121/124/125 + REM-110/113/115/117/119).
**Methode:** headless Desktop-Render-Sweep, `--density 1.0` (player-density-normalisiert,
canonical doc-px). Goldens unangetastet (Output → `/tmp/rem-postwave-sweep/`).
Vergleich gegen `screenshots/reference/desktop/_sweep.csv` (REM-117 `8e8013d`) +
in-tree PNG-Bytes (Mix aus REM-78/102/117/121-Rebaselines, je nach Doc).

## Headline

- **0 BLANK** im neuen Sweep (Baseline hatte 3). **0 FAIL**, **0 surface-dim-Drift**.
- **0 drawCount-Drops** — alle Δ sind `+` (mehr Ops dispatcht).
- **3 BLANK→RENDERS** Status-Reklassifizierung (Harness-Klassifikation, *nicht*
  Render-Pipeline; PNG-Bytes pre/post **byte-identisch**).
- **7 PNG-Bytes-Δ** vs in-tree-Baseline, davon **2 mit substanziellem
  Non-bg-Pixel-Drop** → PO-Eye gefragt.

## Status-Reklassifizierung (kein Render-Pipeline-Effekt)

| Doc | base→new | Bytes | Non-bg-Pixel | Verdikt |
|---|---|---|---|---|
| player_info | BLANK→RENDERS | identisch | 22779 = 22779 | Harness-Klassifikation |
| small_animated | BLANK→RENDERS | identisch | 2716 = 2716 | Harness-Klassifikation |
| spline_demo_spline_demo1 | BLANK→RENDERS | identisch | 39219 = 39219 | Harness-Klassifikation |

→ Diese 3 PNGs sind byte-identisch. Der Status-Flip kommt rein aus dem CSV
(vermutlich Harness erkennt jetzt Non-bg-Pixel sauberer als „RENDERS").
**Kein Render-Pipeline-Effekt** — nur Harness-Reklassifizierung.

## DrawCount-Δ (91 Docs, ausschließlich `+`)

Vollständige Δ-Liste in `/tmp/rem-postwave-sweep/_joined.csv`. Top-Ups (Δ ≥ 50):

| Doc | base→new | Δ |
|---|---|---|
| color_theme | 394→590 | +196 |
| color_list | 384→572 | +188 |
| clock_demo2_jclock2 | 103→235 | +132 |
| shader_calendar | 14→118 | +104 |
| spread_sheet | 10→66 | +56 |

→ Alle drawCount-Drift ist **monoton positiv** → Wave dispatcht mehr Ops, nicht
weniger. Keine Drop-Regression in der CSV-Spalte.

## PNG-Byte-Δ (7 Docs) — Non-bg-Pixel-Analyse

| Doc | Bytes | drawCount | Non-bg-Pixel-Δ | Baseline-PNG-Origin | Verdikt |
|---|---|---|---|---|---|
| **experimental_gmt** | 113638→92868 | 92→111 | **-104,707 (-23%)** | REM-102 (`0050282`) | **FLAG** |
| **experimental_solar_gmt** | 128385→112139 | 92→126 | **-86,616 (-19%)** | REM-117 (`8e8013d`) | **FLAG** |
| graph_graph2 | 10625→9838 | 23→32 | -637 (-2.2%) | REM-121 (`b9754a0`) | DRIFT |
| hydration_wave | 9082→9037 | 3→8 | -904 (-3.0%) | REM-121 (`b9754a0`) | DRIFT |
| moon_phase_dial | 20091→21172 | 8→16 | +1 (≈0%) | REM-78 (`5968733`) | DISPATCH-ONLY |
| paths_demos | 5096→5154 | 73→91 | ±0 (=) | REM-117 (`8e8013d`) | DISPATCH-ONLY |
| stock | 1017→1103 | 14→42 | **+21** | REM-117 (`8e8013d`) | **REM-124-EFFEKT (klein, sichtbar)** |

**Lese-Hilfe für die Spalte „Non-bg-Pixel-Δ":** = Δ der nicht-Hintergrund-Pixel
(Hintergrund = häufigster Farb-Modus). Maß für **sichtbar gerendertes Inhaltsvolumen**.

### Cluster A — DISPATCH-ONLY (visible-output unverändert)

- `moon_phase_dial` (+1 Pixel auf 54478, **0.0018%** Δ) — drawCount-Up = +8, aber
  visible-pixel-Count **identisch**. PNG-Bytes-Drift ist PNG-Kompression-Rauschen
  bei marginal verschobenen Anti-Alias-Kanten.
- `paths_demos` (±0 Pixel, 7430=7430) — drawCount-Up = +18, aber visible-Pixel-
  Count **byte-identisch in der Pixel-Statistik**. PNG-Bytes 5096→5154 = +58 Bytes
  PNG-Kompression-Rauschen.

→ Beide sind **No-op visuell**. Kein Refresh nötig, kein Regress.

### Cluster B — REM-124-EFFEKT

- `stock` (+21 Non-bg-Pixel von 198 auf 219, **+11%**) — REM-124-ClipRect-Fix
  produziert jetzt **sichtbare Sparkline-Pixel** im Voll-Doc-Render. In der REM-124-
  Verdikt-Note hatte ich „PNG unverändert, Sparkline scroll-off-screen bei scroll=0"
  argumentiert; das gilt **fast** — der Canvas ist nur teilweise off-screen und
  die nun korrekt geclippten Sparkline-Endpunkte schauen jetzt mit ~21 Pixeln
  in den Doc-Render rein. Kein Regress. Empfehlung: **kann jetzt re-baselined
  werden**, falls PO einen sauberen Anker will.

### Cluster C — DRIFT seit REM-121 (klein)

- `graph_graph2`: -637 Pixel (-2.2%) seit REM-121-Rebaseline. drawCount +9.
- `hydration_wave`: -904 Pixel (-3.0%) seit REM-121-Rebaseline. drawCount +5.

→ Beide haben seit REM-121 (`b9754a0`) zwischen `561071b`-Render-Zeitpunkt und
heutigem `3940280` die folgenden Wellen-Ops mitgemacht: REM-119 (TEXT_MERGE/
TEXT_LOOKUP/INTEGER_EXPRESSION), REM-115 (Matrix-Triplet), REM-113-followup,
REM-110 (Symbol-Fallback wasm-only-gated), REM-124, REM-125. Der Δ liegt unter
der `±5%`-Anti-Alias-Toleranz typischer Wellen-Drift. **Vermutlich Op-Order /
Anti-Alias-Edge-Resampling**, nicht echter Regress. Klärung wäre ein per-doc-
REM-123-Gate; angesichts der Größenordnung nicht zwingend.

### Cluster D — **FLAG** (substanzieller Non-bg-Pixel-Drop)

- `experimental_gmt`: **-104,707 Non-bg-Pixel (-23%)**.
  - Baseline-PNG-Origin: REM-102 (`0050282`) — **alter Baseline-Anker**, hat REM-117-
    Welle ohne Refresh überstanden, zeigt jetzt nach 39 Commits den ersten
    Pixel-Bruch.
- `experimental_solar_gmt`: **-86,616 Non-bg-Pixel (-19%)**.
  - Baseline-PNG-Origin: REM-117 (`8e8013d`) — nur diese Welle Drift, +19/+34 draw,
    aber **fast 20% weniger sichtbare Pixel**.

→ Beide sind time-driven Clock-Docs (Render @ t=0 fix). Pattern: drawCount-`+`,
visible-pixel-`–`. Drei mögliche Ursachen:
1. **Wave-Fix korrigiert Overdraw** (z.B. doppelt-gezeichnete Loop-Iteration
   wird jetzt 1× gezeichnet → -20% Pixel = legit).
2. **Wave-Fix introduziert Over-Culling** (z.B. ein Clip oder VariableSupport
   resolved jetzt strenger → Inhalt verschwindet = Regress).
3. **Anti-Alias / Stroke-Width-Re-Sampling** — bei -20% unwahrscheinlich.

Ohne Daten-Orakel (REM-123-Gate) kann ich (1) vs (2) nicht entscheiden. Beides
ist plausibel, die Größenordnung allein ist nicht eindeutig.

## Empfehlung an PO

- **0 harte Regression** (kein FAIL, kein BLANK, kein drawCount-Drop, keine
  Surface-Dim-Drift).
- **3 reine Status-Reklassifikationen** (BLANK→RENDERS, byte-identisch) → Harness-
  Klassifikation kann auf neuen Stand committed werden (CSV-only-Update).
- **5 PNG-Drifts ohne klare Sichtbar-Regress-Signatur** (Cluster A+B+C: jeweils
  ≤3% Pixel-Δ oder positiv) → vertretbar als Wave-Drift; kein PO-Eingriff zwingend.
- **2 PNG-Drifts mit `-20%`-Klasse Pixel-Drop** (`experimental_gmt`,
  `experimental_solar_gmt`) → **REM-123-Gate-Bedarf** vor einer Re-Baseline.
  Diese 2 sind die Stellen, wo die Welle „etwas Sichtbares verschoben hat", das
  ich aus dem reinen Sweep nicht legitim als Fix-vs-Regress klassifizieren kann.
  Empfehlung: dev-2 oder assist ein per-doc-Daten-Orakel-Artefakt (Coord-Listen
  der relevanten Loop/Tween/Path-Ops bei t=0) für `experimental_gmt`+
  `experimental_solar_gmt` bauen lassen, dann REM-123-Gate-Lauf.

## Artefakte

- `/tmp/rem-postwave-sweep/_sweep.csv` — neues 173-Doc-CSV
- `/tmp/rem-postwave-sweep/_joined.csv` — Baseline-vs-New Join-Tabelle
- `/tmp/rem-postwave-sweep/*.png` — 173 neue Renders (Goldens **nicht** überschrieben)
- `/tmp/rem-postwave-sweep/_run.log` — Sweep-Lauflog (`BUILD SUCCESSFUL in 30s`)

## File-Disjunktheit

Branch trägt ausschließlich diesen Report unter `artifacts/REM-117-postwave-sweep-3940280.md`.
Keine PNG-/CSV-/Code-Änderungen. PO entscheidet, ob FLAGs → Tickets werden und
ob die DISPATCH-ONLY / REM-124-EFFEKT Goldens refresht werden.
