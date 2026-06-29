# REM-134 Component-Content-Render — Desktop-Daten-Orakel-Gate

> **Adressat:** PO. **Status:** 🔴 **RED — Daten-Orakel NICHT erfüllt.**
> Render zeigt nur **Zeile 1 von 6**; Zeilen 2-6 sind NICHT sichtbar; Lines 5
> Underline/Strike sind emittiert, aber bei **falschen Bounds (volle Canvas-Breite)
> und falscher Y-Position (über/unter Zeile 1 statt Zeile 5)**. **Re-Baseline NICHT
> ausgeführt** — der Golden wäre nur eine andere Poisoned-Stufe (Line-1-only).
> **Branch:** `bugfix/REM-134-component-content-render-gate` (Verdikt-Doc-only,
> KEIN Golden-Update). **Cherry-pick** `0378899` lokal getestet, nicht gepusht.

## TL;DR (3 Sätze)

dev-2s jvmTest (Layout-Mathematik mit deterministischen Fake-Metriken) ist
**6/6 grün**, aber der Desktop-Real-Metric-Render produziert **nur Zeile 1 +
Decorations**: keine Spans aus Zeilen 2-6 sind visuell sichtbar (0 rote, 0
blaue, 0 gelbe Pixel; 0 Non-White-Pixel unterhalb y=60). Die 2 DrawLines
(Underline + Strike, Zeile 5) emittieren zwar, **landen aber an Zeile 1s Y-
Position mit voller Canvas-x-Breite** (statt an den gemessenen
"Underlined"/"Strikethrough"-Span-Bounds in Zeile 5). **Verdikt: PARTIAL-FIX
auf Layout-MATH-Ebene grün, aber Real-Metric-Pixel-Render auf Desktop versagt.
Re-Baseline blockiert; dev-2 muss den Layout-Real-Metric-Pfad untersuchen.**

## Render-Befund (auf cherry-pickter `0378899` lokal auf develop `7f53e0b`)

- Doc: `attribute_string`, surface 500×500, **drawCount=28** (24 Spans + 2
  DrawLines + 2 weitere — alle dispatched), Status RENDERS.
- Ausgabe-PNG: **10814 Bytes** (vs. poisoned-Golden 1702 B), also: REM-134 hat
  ETWAS gezeichnet (Title + Decorations), aber NICHT die volle 24-Span / 6-
  Zeile-Soll-Struktur.

## Per-Source-B-Bar-Check (test-3-Daten-Orakel)

Source B: `docs/REM-134-attribute-string-span-oracle.md` (dev-2, auf Branch).

| # | Source-B-Bar | Befund | Verdikt |
|---|---|---|---|
| 1 | ≥24 `drawTextRun`/`drawComplexText` **an Soll-Positionen** | drawCount=28 dispatch (≥24), aber **visuell nur 1 Zeile** sichtbar (Zeile 1 = "AttributedString Demo:"). Zeilen 2-6 dispatched ohne sichtbares Resultat. | 🔴 **NICHT ERFÜLLT** (dispatch ja, Soll-Position nein) |
| 2 | Pro Zeile: x-monoton steigend | Nur Zeile 1 sichtbar → x-Monotonie nur für 1 Span prüfbar. Zeilen 2-6 unsichtbar. | 🔴 nicht prüfbar |
| 3 | Pro Zeile: EINE gemeinsame Baseline (AlignBy line=NaN); harter Fall Zeile 6 ("Big" 92px + 46px) | Zeile 6 ("Big") nicht sichtbar — kein 92px-Text auf Canvas. | 🔴 NICHT ERFÜLLT |
| 4 | Zeilen stapeln vertikal top→down (Row-Baselines streng steigend) | Y-Range aller Content-Pixel: **y[13,56]** = 1 Band. Below y=60: **0 Non-White-Pixel** (komplett leer auf 444 Zeilen). | 🔴 NICHT ERFÜLLT (kein vertikales Stacking sichtbar) |
| 5 | z-order: Zeile-4-"Yellow Background"-Text SICHTBAR ÜBER gelbem BG-Rect | **0 gelbe Pixel** im gesamten Render (Color-Class-Scan: 0/250000 yellow). Zeile 4 nicht gerendert. | 🔴 NICHT ERFÜLLT |
| 6 | Underline/Strike: 2 `drawLine`s (Zeile 5) **an gemessenen Span-Bounds** | 2 DrawLines emittiert (y=31-32 strike, y=45-46 underline), aber bei **x[0,489]/x[0,499] = volle Canvas-Breite** statt nur "Underlined"/"Strikethrough"-Span-Bounds. Außerdem an Zeile-1-Y-Position statt Zeile-5-Y. | ⚠️ EMITTIERT, aber FALSCHE BOUNDS + FALSCHE Y |

**Pixel-Color-Census (gesamtes 500×500):**
- White: 242758 (97.1%)
- Black-Grey: 6469 (Title text + DrawLines)
- **0 Red**, **0 Blue**, **0 Yellow** — Spans mit ColorAttribute (Red/Blue) + Yellow-BG nicht gerendert.
- Near-White: 773 (AA-fringes).

**Below y=60: 0 Non-White-Pixel** (kompletter unterer 444px-Bereich blank).

## Cross-Check: dev-2 jvmTest (deterministische Fake-Metriken)

```
./gradlew :shared:jvmTest --tests "Rem134ComponentContentTest"
→ BUILD SUCCESSFUL · 6/6 tests PASSED · 0 failures
```

Tests grün:
- `allSpansRender_inDocumentOrder[jvm]` ✓
- `spansAdvanceHorizontally_withinARow[jvm]` ✓
- `mixedFontSizes_shareOneBaseline_viaAlignBy[jvm]` ✓
- `rows_stackVertically_downTheColumn[jvm]` ✓
- `underlineAndStrike_drawLinesStillEmit[jvm]` ✓
- `byteFormat_unchanged_forTheThreeOps[jvm]` ✓

→ **Layout-MATHEMATIK ist mit Fake-Metriken korrekt** (dev-2s Anspruch).
→ **Desktop-Real-Metric-Render scheitert** an genau diesem Pfad — der Gap zwischen
jvmTest-Fake-Metrics (alle 0/deterministic) und Skia-Real-Text-Metrics. **Das ist
test-3s Pixel-Beweis-Pflicht** (PO-Anweisung: „echte Pixel-Positionen = test-3-
Desktop-Orakel").

## Hypothese (Diagnose-Hinweis für dev-2, nicht Auftrag)

Mögliche Ursachen, die zum Befund passen (nur 1 sichtbare Zeile + DrawLines an
voller Breite + an Zeile-1-Y):

1. **TextLayout-Sizing mit Real-Metrics:** `getTextBounds` auf Desktop liefert
   nur den **ersten** Span Größe → Content-Slot der TextLayout erbt nur 1-Span-
   Bounds → andere Zeilen kollabieren auf y=Zeile1.
2. **AlignBy-Modifier mit Real-Metrics:** ascent/textTop-Berechnung wechselt von
   Fake-0-Metriken zu echten Skia-Werten → max-ascent-Logik produziert eine
   gemeinsame Baseline aber NICHT pro Row (sondern global) → alle Spans landen
   an Zeile-1-Y.
3. **ComponentValue-Resolution für Underline/Strike-Bounds:** `componentValue.
   width` löst auf **TextLayout-Container-Width** statt auf den **Span-Bounds**
   → Underline/Strike zeichnen über volle TextLayout-Breite (=500px-Canvas).
4. **Row-Stacking-Berechnung:** Column berechnet Row-Y mit Zeile-1-Height für
   alle Zeilen → kumulative Y bleibt bei 0 → alle Zeilen overlap auf Y=0.

(Eine Kombination aus 1 + 3 erklärt den genauen Befund am besten:
TextLayout-Sizing kollabiert vertikal auf 1 Zeile, ComponentValue-Width misst
Container-statt-Span.)

## Visualisierungs-Anhang

- `/tmp/rem134_top.png` (Top-Crop y=0-80): "AttributedString Demo:" Title + 3
  horizontale Linien (top-edge, mid-strike, bottom-underline) durch/unter Title
- `/tmp/rem134_below.png` (Below-Crop y=80-500): kompletter weiß-Bereich, 0
  Pixel Inhalt

## Empfehlung

🔴 **REM-134 NICHT mergebar in aktuellem Zustand.**
- **Code-Pfad** funktioniert mathematisch (jvmTest 6/6) aber **Desktop-Real-
  Metric-Render** zeigt nur 1/6 Zeilen sichtbar + DrawLines an falschen Bounds.
- **Re-Baseline-Block bleibt:** wenn ich jetzt re-baseline, ersetze ich
  "1702 B blank-poisoned" durch "10814 B Line-1-only-poisoned" — beides nicht
  das Soll-Render. Würde gegen REM-123-Gate verstoßen (Re-Baseline nur nach
  verifiziertem Fix).
- **dev-2-Hand-off:** PO bitte relayen — Layout-Mathematik vs. Real-Metric-
  Pfad-Gap untersuchen (4 Hypothesen oben als Startpunkte). Möglicher konkreter
  Hinweis: TextLayout-Sizing mit Real-`getTextBounds` + ComponentValue-Width-
  Resolution für DrawLines-Bounds.
- **Sobald dev-2 nachgebessert hat:** Gate-Re-Run = Re-Render + Re-Check
  Source-B-Bar 1-6 → bei grün: Re-Baseline + Bundle-Push.

— test-3, 2026-06-29
