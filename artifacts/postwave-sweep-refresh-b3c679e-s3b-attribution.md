# REM-108-S3b Attribution + Intent-Confirm — 3 Scroll-Flag-Docs

**Branch:** `chore/postwave-sweep-refresh` (zweiter Commit auf dem refresh-Branch).
**Datum:** 2026-06-29.
**Vorgeschichte:** Erster Commit (`c917351`) Postwave-Baseline-Refresh hat 3 NEU-
seit-`3940280` VISIBLE-Δs geflagt: `c_modifier_horizontal_scroll`,
`c_modifier_vertical_scroll`, `color_list`. PO-Hypothese
(Discord `1520964578081640671`): **REM-108-S3b**-Scroll-Layout-Render-Effekt,
**nicht** REM-128 (Creation-seitig, berührt Player nicht).

## Schritt 1 — Attribution per Commit

Render der 3 Docs an 2 Commits, Vergleich vs Goldens-Stand + vs aktueller
postwave-Render (`b3c679e`):

| Doc | golden ↔ `5f2ebe1` (pre-S3b) | golden ↔ `57a5904` (post-S3b-merge) | `57a5904` ↔ `b3c679e` (postwave) |
|---|---|---|---|
| `c_modifier_horizontal_scroll` | **IDENT** | **DIFF** | **IDENT** |
| `c_modifier_vertical_scroll` | **IDENT** | **DIFF** | **IDENT** |
| `color_list` | **IDENT** | **DIFF** | **IDENT** |

**Interpretation:** für alle 3 Docs:
- Pre-S3b-Render = aktueller Golden → kein Drift VOR REM-108-S3b.
- Post-S3b-Render = differiert vom Golden → **Δ entsteht genau bei S3b-Merge**.
- Post-S3b-Render = aktueller postwave-Render → **keine weitere Drift NACH S3b**
  (REM-127-PathExpression, REM-128-S2/S3a-Container/Modifier, REM-129 etc.
  berühren diese 3 Docs nicht).

→ **REM-108-S3b ist die alleinige Ursache** (PO-Hypothese verifiziert).
**Kein REM-128-Render-Bug** (REM-128 ist Creation-seitig per Definition, kann
existierenden Korpus-Doc-Render nicht ändern — Hypothese bestätigt).

## Schritt 2 — Daten-Orakel-Intent-Confirm (Scroll-Layout @ offset=0)

REM-108-S3b-Commit (`57a5904`) implementiert:
> Player `matrixSave → clipRect(viewport) → translate(scrollOffset) → matrixRestore`-Bracket.

@ static t=0 ist `scrollOffset=0` → `translate(0,0)` = no-op → Effekt = **Content
wird auf Viewport-Box geclippt**, aber nicht verschoben.

**Per-pixel-Diff-bbox-Analyse** (NEW vs OLD, identifiziert Scroll-Window-Region):

| Doc | Δ-pixels | Δ-bbox | Δ-Region-Größe | Außen-Region (≠Scroll) |
|---|---:|---|---|---|
| `c_modifier_horizontal_scroll` | 40,574 | x[7..499] y[0..99] | top-Strip 100×500 | **byte-identisch** (untere 400 px) |
| `c_modifier_vertical_scroll` | 14,251 | x[3..126] y[8..497] | left-Strip 127×500 | **byte-identisch** (rechte 373 px) |
| `color_list` | 21,267 | x[0..499] y[0..107] | top-Strip 108×500 | **byte-identisch** (untere 392 px) |

**Subset-vs-Superset-Analyse** (`c_modifier_horizontal_scroll`-Detail):
- **LOST** (was-non-white-im-OLD, white-im-NEW): 645 px, bbox x[8..424] y[14..39]
  — eine breite horizontale Band-Region.
- **GAINED** (white-im-OLD, non-white-im-NEW): 275 px, bbox x[8..46] y[14..39]
  — schmalere horizontale Band-Region am linken Ende der gleichen Band.

→ **Klassisches Clip-Pattern:** Pre-S3b war der Scroll-Content über den
Viewport-Rand hinaus gerendert (Bband x[8..424]); Post-S3b ist auf den
Viewport-Ausschnitt geclippt (Bband x[8..46]). Die GAINED-Pixel sind
Layout-Edge-Reflow durch die andere Anordnung im Clip-Window.

**Konsistenz mit S3b-Commit-Aussagen:**
- ✅ S3b-Commit-Msg: „color_table 18→22 benign (decode: hat MODIFIER_SCROLL idx796,
  within-feature, offset=0 visuell identisch)" — color_table 70 px-Diff
  bestätigt das (unter Noise-Floor).
- ✅ S3b-Commit-Msg: „matrixSave/Restore scopt Matrix UND Clip, undone am Holder-
  CONTAINER_END, kein Leak in Geschwister, index-balanced" — Außen-Regionen
  der Flag-Docs sind byte-identisch → kein Leak in Geschwister-Inhalt.
- ✅ S3b war an Android-Live-Scroll-Render gegated (test-1-on-device-Beweis +
  jvm 623/0 + Conformance 173/173 + Full-Sweep 1-benign-Diff). Die **Desktop-
  Pixel-Form** der Scroll-Docs wurde aber bei Merge-Zeit NICHT voll-gegated —
  diesen Postwave-Refresh fängt das jetzt.

## Verdikt

**REM-108-S3b = LEGIT-Clip-Fix für die 3 Docs.**
- ✅ Attribution: alleinige Ursache bei S3b-Merge `57a5904`.
- ✅ Intent: Δ-Pixel-bboxes = Scroll-Viewport-Regions; Außen-Regionen byte-
  identisch (nicht mis-nested); LOST-vs-GAINED-Pattern = klassische
  Viewport-Clip-Wirkung; @ offset=0 visuell-erwartetes Verhalten.
- ✅ REM-128 sauber ausgeschlossen (Creation-seitig, kann nicht render-flippen).
- ✅ Carry-Over-Sicherheit: andere Goldens unverändert; nur diese 3 + die
  bekannten Carry-Over (graph_graph2 / hydration_wave / moon_phase_dial /
  stock) bleiben mit ihren etablierten QA-no-action-Klassifizierungen.

**Routing-Mapping (zu deinem PO-Plan):**
- (1) Attribution = S3b ✓
- (2) Intent = LEGIT ✓ → **Re-Baseline die 3 PNGs**
- → ausgeführt in diesem Commit. `_sweep.csv` ist bereits im ersten Commit
  `c917351` re-ankert.

## File-Disjunktheit

Branch `chore/postwave-sweep-refresh` (kumuliert):
- `c917351` — _sweep.csv-Re-Anker + Refresh-Report.
- **Dieser Commit** — Attribution+Intent-Verdikt + 3 Re-Baseline-PNGs
  (`c_modifier_horizontal_scroll.png`, `c_modifier_vertical_scroll.png`,
  `color_list.png`).
- Kein Code. Keine weiteren PNG-Änderungen. Keine `_sweep.csv`-Änderung
  in diesem Commit (CSV ist in c917351 stabil — die 3 Docs sind in der
  CSV bereits korrekt erfasst, RENDERS-Status unverändert).
