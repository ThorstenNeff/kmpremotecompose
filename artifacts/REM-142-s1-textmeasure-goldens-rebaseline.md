# REM-142 S1-TextMeasure Goldens Re-Baseline — `procedure_look_up1` + `procedure_center_text1`

> **Adressat:** PO. **Status:** ✅ **GRÜN (BENIGN)**.
> **Branch:** `bugfix/REM-142-s1-textmeasure-goldens-rebaseline` (test-3, dieser Commit).
> **PO-Routing:** `1521169490543509576` (BENIGN-Disposition, dev-2 decode-conclusive).
> **Bar:** REM-139-S1-Merge-Time-Gap-Cleanup — 2 Goldens auf den korrekten post-S1-Render-Stand bringen via Daten-Orakel.

## TL;DR (3 Sätze)

REM-139-S1s `TextMeasure.apply` produziert jetzt **korrekte Box-Dimensionen** (war 0/degenerate). Auf den 2 Text-bearing Docs wird dadurch der pre-existing **DRAW_OVAL-Highlight-Box** (36×14 um „John" bzw. 27×14 um „99") sichtbar — was vorher als unsichtbarer 0×0-Punkt renderte und jetzt als korrekte gesizte Box → optisch wie ein kleines „+"/Crosshair-Artefakt aussieht, aber **upstream-faithful intended** ist (kein Bug). Beide Goldens auf den 7664B / 8041B post-S1-Stand re-baselined.

## Hintergrund (dev-2 decode-conclusive disposition)

PO `1521169490543509576`: das `+`/Crosshair-Artefakt ist NICHT die DRAW_OVAL-Mechanik, die ich zuerst hypothetisierte — die DRAW_OVAL ist tatsächlich der **Voll-Fenster-Kreis** (`r=WINDOW_WIDTH/b=WINDOW_HEIGHT` → 300×300) plus `MATRIX_SCALE scaleX=-1` (animierter Horizontal-Mirror), **byte-identisch pre/post-S1**, **keine Ref auf TextMeasure/Lookup**. Der visuelle „+" entsteht aus der nun-korrekt-gesizten Highlight-Box (`36×14` um „John" / `27×14` um „99") + dem gerenderten Text — S1s Δ (degenerierter Punkt → gesizte Box; blank → gerendert) ist intended/korrekt.

→ **Re-Baseline ist die richtige Disposition** (Goldens waren am S1-Merge nicht aktualisiert; dev-2 hat das Render-Verhalten korrekt, nur die Goldens-Sync fehlte; war an MEINEM REM-139-S2-Voll-Render-Gate aufgefallen via 3-Tip-Bisect-Attribution).

## 3-Tip-Stability-Cross-Check (Daten-Orakel-Vorbereitung)

| Doc | pre-S1 `00f8ee9` | S1-tip `02f542a` | S2-merged `114f8ce` | Konsistenz |
|---|---|---|---|---|
| `procedure_look_up1` | 5444B (pre-S1) | 7664B | 7664B | post-S1-stabil über 3 Tips |
| `procedure_center_text1` | 7883B (pre-S1) | 8041B | 8041B | post-S1-stabil über 3 Tips |

Beide Docs sind seit S1-Merge byte-stable (nicht von S2 berührt, byte-identisch zwischen S1-tip und merged-develop). Re-Baseline-Quelle = fresh Render auf `114f8ce` (aktueller develop-Tip).

## Daten-Orakel-Verifikation (visuell)

**`procedure_look_up1`** (DATA_MAP_LOOKUP key="First"):
- Pre-S1: roter Voll-Kreis, **kein Text** (DataMapLookup decode-only → text[50] null → DRAW_TEXT_ANCHOR-leer).
- Post-S1 (re-baseline): roter Voll-Kreis + **„John" blau zentriert** + sichtbare 36×14-Highlight-Box (gesizt durch TextMeasure-`John`-Bounds).

**`procedure_center_text1`** (TEXT_FROM_FLOAT „99"):
- Pre-S1: roter Voll-Kreis + „99" blau zentriert, **keine Highlight-Box** (TextMeasure-degenerate → 0×0-Box unsichtbar).
- Post-S1 (re-baseline): roter Voll-Kreis + „99" + sichtbare 27×14-Highlight-Box (gesizt durch TextMeasure-`99`-Bounds).

S1s Visual-Δ in beiden Docs = **intended Effekt** der korrekten Producer-Pipeline (Lookup + Measure → real-Werte statt null/0).

## Methodologie-Notes

- **3-Tip-Bisect war Pflicht** (pre-S1 ↔ S1-tip ↔ S2-tip) zur sauberen Attribution — sonst wäre nicht klar gewesen, ob procedure_center_text1 ein S2-Collateral oder ein S1-Late-Catch ist. War S1-Late-Catch.
- **Disambiguierungs-Disziplin (necessary≠sufficient):** mein zuerst-flag „`+`-Crosshair sieht degenerate aus" war eine Hypothese ohne Decode-Belegung — dev-2s Decode-Proof (DRAW_OVAL = Voll-Fenster-mirror, byte-id pre/post-S1) hat sie widerlegt. **dispatch≠visual gilt in beide Richtungen:** ein verdächtig-aussehendes Pixel-Muster ist nicht automatisch ein Bug, ein unverdächtiges nicht automatisch sauber — Decode-/Source-Cross-Check ist das Orakel.
- **Lehre verankert (test-3-memory):** `feedback_gate_verdict_full_corpus_byte_diff.md` — voll-Korpus-byte-Diff vs Goldens als Standard-Verdikt-Bar (nicht nur fingerprint+1-Doc) hat hier den S1-Merge-Time-Gap retroaktiv gefangen. Postwave-Sweep-Hygiene + Verdikt-Gate-Disziplin gemeinsam = die Sicherheit.
- **REM-139 (compute/lookup) komplett zu** mit dem REM-142-Re-Baseline (S1-Goldens-Sync) + REM-139-S2-Merge (LayoutCompute + S2-Goldens).
