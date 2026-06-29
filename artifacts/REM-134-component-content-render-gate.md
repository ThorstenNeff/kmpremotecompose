# REM-134 Component-Content-Render — Desktop-Daten-Orakel-Gate

> **Adressat:** PO. **Status (Re-Run nach dev-2 Fix `28418f4`):**
> **Bars 1-5 ✅ GRÜN** — Text-Deliverable bewiesen, alle 6 Zeilen on-canvas,
> Row-Baselines an Soll 45/98/151/204/257/351, Z-Order Yellow-BG-Text korrekt.
> **Bar 6 ⚠️ PRE-EXISTING OFFEN** — Underline/Strike-DrawLines noch an Top-
> Origin (0,~40) statt Span (x~206,y~257); per PO `1521071949999247451`
> Klassifikation = §4.3-Design-Ruling-Issue, **wartet auf assist parallel,
> blockiert Text-Teil-Verdikt nicht.** **Re-Baseline weiter BLOCKIERT** (Text-
> grün + Decoration-falsch = neuer partial-poisoned Golden; erst wenn beides
> grün ist). Branch: `bugfix/REM-134-component-content-render-gate` (Verdikt-
> Doc-only). Cherry-picks `0378899 + 28418f4` lokal getestet, nicht gepusht.
>
> *Historischer Initial-RED-Run (vor `28418f4`):* nur Zeile 1 sichtbar
> (Root-Cause: Modifier-lose Rows defaulteten auf FILL=492px → Zeilen 2-6
> off-canvas; jetzt wrappen). Initial-Befund unten als historischer Kontext
> erhalten.

## Re-Run-Befund nach Fix `28418f4` (aktuell)

Cherry-pick: `0378899` + `28418f4` lokal auf develop `7f53e0b` → Render-Tip
`4e0240d`. Re-Render `attribute_string.png` = 46089 B (vs. ursprünglicher
poisoned-Golden 1702 B / vorheriger Line-1-only-Render 10814 B).

**Sichtbare Inhalts-Bands (Color-Class + Row-Detection):**

| Band | Y-Range | Höhe | Farben | Zuordnung |
|---|---|---|---|---|
| 1 | y[13, 56] | 44 | text (black-grey) | **Zeile 1** "AttributedString Demo:" — Baseline ~45 ✓ |
| 2 | y[67,110] | 44 | text | **Zeile 2** "This is **Bold**, this is *Italic*..." — Baseline ~98 ✓ |
| 3 | y[121,219] | 99 | text + **red** + **yellow** | **Zeile 3 + Zeile 4 merged-band** (kein Pixel-Gap >4 zwischen ihnen): Baseline 151 (Red) + 204 (Yellow-BG) ✓ |
| 4 | y[229,272] | 44 | text | **Zeile 5** "This is Underlined, and..." — Baseline ~257 ✓ |
| 5 | y[293,380] | 88 | text | **Zeile 6** "This is **Big**, and this..." — Baseline ~351 ✓ (88 ≈ 92px Big font extent) |

**Color-Class-Census (gesamtes 500×500):**
- **Red: 970 px** ✓ (Zeile 3 "Red" span — Color resolved nicht Fail-Soft)
- **Yellow: 7156 px** ✓ (Zeile 4 "Yellow Background"-Rect)
- Black: 26738 px (Text)
- Grey: 10990 px (AA)
- White: 203429 px (Background)

**Visuell verifiziert via Crop** (siehe `/tmp/rem-134-render2/attribute_string.png`):
alle 6 Zeilen vertikal gestapelt sichtbar, Bold/Italic-Styling differenziert,
Red-Span ROT, Yellow-BG mit Text-Overlay (Z-Order korrekt!), "Big"-Span deutlich
größer (92px) auf Zeile 6.

## Per-Source-B-Bar-Check (Re-Run)

| # | Source-B-Bar | Befund | Verdikt |
|---|---|---|---|
| 1 | ≥24 `drawTextRun`/`drawComplexText` an Soll-Positionen | drawCount=28 dispatched; visuell alle 6 Zeilen mit Spans an gestaffelten X-Positionen sichtbar → 24 Spans rendern an Soll | ✅ **GRÜN** |
| 2 | Pro Zeile: x-monoton steigend | Visuelles Cleanly-left-to-right pro Zeile (kein Overlap, keine Backtracking) | ✅ **GRÜN** |
| 3 | Pro Zeile: EINE gemeinsame Baseline; **Zeile 6 harte Fall** (Big 92px + 46px) | Zeile 6 band y[293,380]: "This is" 46px + "Big" 92px + ", and this" 46px **teilen Baseline 351** (kleinere Spans nach unten geschoben, NICHT top-aligned) — AlignBy line=NaN funktioniert | ✅ **GRÜN** |
| 4 | Zeilen stapeln vertikal top→down (Row-Baselines streng steigend) | Bands at y=13/67/121/229/293; Baselines 45/98/151/204/257/351 monoton steigend (PO-bestätigt) | ✅ **GRÜN** |
| 5 | Z-order: Zeile-4-"Yellow Background"-Text SICHTBAR ÜBER gelbem BG-Rect | Yellow-BG sichtbar mit schwarzem Text-Overlay (Z-Order korrekt: Text NACH BG gerendert via DrawContent-Stream-Position) | ✅ **GRÜN** |
| 6 | Underline/Strike: 2 `drawLine`s an **gemessenen Span-Bounds** | 2 DrawLines noch an **Top-Origin (0,~40)**: y=31-32 (Strike) und y=45-46 (Underline) mit x[0,489]/x[0,499] = volle Canvas-Breite. Soll wäre Zeile 5 (Underline) + Zeile 5 (Strike) an "Underlined"-/"Strikethrough"-Span-Bounds (x~206, y~257). | ⚠️ **PRE-EXISTING** (per PO §4.3-Design-Ruling, wartet auf assist) |

**Bars 1-5 = ✅ GRÜN.** Text-Deliverable bewiesen: alle 6 Zeilen / 24 Spans /
korrekte Baselines / x-Monotonie / Z-Order Yellow-BG / AlignBy.

**Bar 6 (Decoration-Position) = ⚠️ pre-existing.** Per PO-Routing
`1521071949999247451`: ".rc erwartet span-LOKALE Coords + per-Component-
Translate, was §4.3 (kein Translate) verbietet. Pre-existing + wartet auf
assist-Design-Ruling parallel". Blockiert Text-Teil-Gate nicht.

## Cross-Check: jvmTest weiter grün

Annahme bestätigt: dev-2 `Rem134ComponentContentTest` 6/6 PASS auf cherry-pickter
Tip — Layout-MATH war schon mit Fake-Metrics grün, ist mit Real-Metrics + Fix
`28418f4` **jetzt auch im Pixel-Render grün** für Bars 1-5.

## Re-Baseline-Status

**WEITER BLOCKIERT** (per PO-Anweisung `1521071949999247451`):

> "**Wichtig: re-baseline den attribute_string-Golden NOCH NICHT** — erst wenn
> Text UND Decoration korrekt sind (sonst „text+wrong-decoration" = neuer
> partial-poisoned Golden)."

Ich folge der Regel: Text-grün (Bars 1-5) + Decoration-falsch (Bar 6) wäre ein
neuer partial-poisoned-Golden = REM-123-Gate-Verletzung. Re-Baseline kommt erst
nach assist-§4.3-Ruling + dev-2-Decoration-Fix.

## Initial-RED-Befund (historisch, vor `28418f4`)

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
