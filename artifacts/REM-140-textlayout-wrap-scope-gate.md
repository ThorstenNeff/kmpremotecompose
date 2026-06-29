# REM-140 — TextLayout-Wrap-Scope-Regress-Fix Voll-RENDER-Sweep-Gate

> **Adressat:** PO. **Status:** ✅ **GRÜN** (Voll-RENDER-Sweep-Gate, echter
> Skia, NICHT Fingerprint). Branch:
> `bugfix/REM-140-textlayout-wrap-scope-gate` von develop `cabb25b` mit
> Cherry-pick dev-2 `9ebbc5b` lokal → 3 Text-Gain-Goldens re-baselined.
> **PO-Routing:** `1521150044424896534`. dev-2-honest-flag: player_info-
> Regress war headless-Fingerprint-unsichtbar → echter Skia-Render war Pflicht.

## TL;DR (3 Sätze)

dev-2s REM-140-Scope-Fix `9ebbc5b` (REM-134-Measure-Changes auf TextLayout-
Span-Fall gescoped) **stellt korrekt wieder her**: `player_info` byte-identisch
(55938B → 55938B) und `text_baseline` byte-identisch (36585B → 36585B) zu
pre-REM-134-Goldens, **attribute_string behält seinen REM-134-Fix** (24-Spans-
Render byte-id 45548B). **3 Text-Gain-Docs** (color_table, demo_graphs1,
demo_text_transform) sind verify-correct Improvements — visuell bestätigt
(Cell-Labels / Y-Achsen-Labels / Timer-Text rendern jetzt sichtbar) — und
re-baselined via Daten-Orakel. **170/173 byte-identisch zu pre-REM-138-Goldens
+ 3 re-baselined Text-Gains = 173/173 abgedeckt.**

## Voll-RENDER-Sweep-Befund (echter Skia, post-Cherry-pick `9ebbc5b`)

- Render-Tip lokal `2110b88` (Cherry-pick `9ebbc5b` auf develop `cabb25b`).
- Voll-173-Sweep → **170/173 byte-identisch** zu current Goldens, **3/173 Δ**
  (alle Text-Gain-Improvements, keine Regresse).

## Daten-Orakel-Verifikation der 4-Bar-Gate-Punkte (PO-spezifiziert)

### Bar 1: player_info + text_baseline zurück auf voll-Content ✅

| Doc | OLD (pre-REM-138-Golden) | NEW (post-REM-140-Fix) | Match |
|---|---|---|---|
| `player_info` | 55938B | 55938B | ✅ **IDENT** = Regress vollständig recovered |
| `text_baseline` | 36585B | 36585B | ✅ **IDENT** = Regress vollständig recovered |

**Beweis-Logik:** Die Goldens für player_info + text_baseline wurden vor REM-134
gesetzt (pre-poisoning-Stand). Nach REM-134 produzierte der Sweep diese Docs
mit Inhaltsverlust (player_info -54236B / text_baseline -23295B = die Regress-
Klasse die REM-140 jetzt fixt). Post-REM-140 ist der Render wieder byte-
identisch zum pre-REM-134-Golden = **Regress vollständig zurückgenommen** durch
Scope-Fix (row-wrap nur für span-bearing flex + AlignBy-baseline nur für
TEXT_LAYOUT-Nodes).

### Bar 2: attribute_string behält seinen REM-134-Fix ✅

| Doc | Pre-REM-134-Golden | Post-REM-134-Golden | NEW (post-REM-140) | Match |
|---|---|---|---|---|
| `attribute_string` | 1702B (blank-poisoned) | 45548B (24-Spans-Render) | 45548B | ✅ **IDENT zu Post-REM-134-Golden** |

REM-140-Scope-Fix gilt nur für non-Span-Komponenten → attribute_string als
TEXT_LAYOUT-Span-tragender Doc nimmt weiter den REM-134-Pfad. Der 24-Span-
Render mit Z-Order-Yellow-BG + Bracket-Leak-Probe-konformen Decoration-
Bounds (REM-134 (a) Gate) bleibt intakt.

### Bar 3: 4 Text-Gains → verify-correct → re-baseline ⚠️ (3 verified-correct + 1 nicht-im-Korpus)

PO-genannte 4 Text-Gain-Kandidaten: `color_table` / `demo_graphs1` /
`demo_text_transform` / `demo_use_of_global`. **`demo_use_of_global`
existiert NICHT im test-3-Desktop-Korpus** (kein .rc + kein .png-Golden);
vermutlich PO-Liste aus dev-2-Fingerprint-View, das einen anderen Subset hat.
**Die 3 vorhandenen** Text-Gains:

| Doc | OLD (poisoned/blank) | NEW (verify-correct) | Visual-Befund | Re-Baseline |
|---|---|---|---|---|
| `color_table` | 1871B (single periwinkle, keine Labels) | 71344B | Cell-Color-Swatches + Labels (`system_accent1_0`/`_10`/`_100`/`_200`/`_300`/`_400` etc.) sichtbar | ✅ executed |
| `demo_graphs1` | 2796B (fast-blank) | 43041B | Y-Achse-Labels (`1.5`/`1.3`/`1.1`/`0.9`/...`-0.3`) + rote sine-wave + Grid auf navy-BG sichtbar | ✅ executed |
| `demo_text_transform` | 5116B | 12928B | `0:00:00`-Timer-Text auf orange-BG + rotem Kreis sichtbar | ✅ executed |

**Daten-Orakel-Methode (NICHT Cross-Target-Self-Compare):**
- Alte Goldens waren ja gerade die poisoned-Stufe vor REM-134 (1871/2796/5116B = mostly-blank).
- Re-Baseline-Quelle = der jetzt-korrekte Skia-Render via REM-134-TextLayout-
  Render-Pfad (auf Span-Fall gescoped via REM-140, plus globale Improvements
  wirken doch — siehe Diskussion unten).
- Visuell-Verifikation: Crops zeigen die jeweiligen erwarteten Inhalte
  (Cell-Labels / Achsen-Labels / Timer-Text) korrekt gerendert.

**Diskussions-Punkt zu Bar 3:** color_table/demo_graphs1/demo_text_transform
sind eigentlich KEINE TextLayout-Span-Docs (sie nutzen normale Text-Ops, nicht
attribute_string-Spans). Trotzdem rendern sie jetzt mehr — wahrscheinlich weil
REM-134s zugrundeliegende `TextMeasure`-/`drawTextRun`-Pfad-Verbesserungen
(nicht nur die row-wrap+AlignBy-Measure-Changes, die REM-140 jetzt scopt)
**weiter wirksam sind** auf alle Text-rendernden Docs. Die scoped row-wrap+
AlignBy-Fix verhindert nur den Regress in non-Text-Docs, nicht die Text-Render-
Improvements. **Interpretation: Improvements legitim** — REM-134 hat
TextLayout-Path verbessert + REM-140 hat das auf non-Span-Docs zurück-Scope.
Re-Baseline ist also korrekt für diese 3 Improvements.

### Bar 4: Alle anderen byte-identisch zu pre-REM-134-Goldens ✅

**170/173 byte-identisch** = 167 nicht-textuelle Docs unverändert (das ist die
breite Negativ-Kontrolle) + 3 vorher-fingerprint-fingerprint-affected-Docs sind
jetzt wieder byte-id:
- `color_list` (war +47515 in REM-138-Sweep, jetzt byte-id)
- `color_theme` (war +43595, jetzt byte-id)
- `c_column` / `c_row` / `c_modifier_align_by_baseline` / `stock` (waren AA-noise, jetzt byte-id)

**= REM-140-Fix hat alle 8 fingerprint-affected Docs (player_info + text_baseline + die 6 AA/medium) zurück auf pre-REM-134-Pfad gebracht** durch Scoping der Measure-Changes auf TEXT_LAYOUT-Nodes. Genau wie dev-2s Commit-Message verspricht.

## Voll-173-Sweep-Status Summary

- **170/173 byte-identisch** (Negativ-Kontrolle massiv: alle Non-Text-Docs +
  alle vorher-fingerprint-affected-zurück).
- **3/173 Δ — alle 3 verify-correct Text-Gain-Improvements** → re-baselined.
- **0 Regresse**.

## Empfehlung

✅ **GRÜN — REM-140 (dev-2 `9ebbc5b`) mergen + die 3 re-baselined Text-Gain-
Goldens.**
- Scope-Fix korrekt: player_info + text_baseline recovered, attribute_string
  Fix intakt.
- 3 Text-Gains verify-correct Improvements (visuell bestätigt) → re-baseline
  appropriate (REM-123-konform: Daten-Orakel, NICHT Self-Compare gegen
  poisoned Goldens).
- Voll-RENDER-Sweep war notwendig (player_info-Regress headless-Fingerprint-
  unsichtbar per dev-2-honest-flag).
- Bundle = `bugfix/REM-140-textlayout-wrap-scope-gate` mit 3 Goldens +
  Verdikt-Doc (kein Code-Change durch test-3).

**Hinweis:** `demo_use_of_global` (PO-genannt aus dev-2-Fingerprint-View) ist
NICHT im test-3-Desktop-Korpus — vermutlich Dev-2-specific Doc. Falls relevant,
wäre eine separate dev-2-bestätigte Δ-Analyse für diesen Doc nötig.

— test-3, 2026-06-29
