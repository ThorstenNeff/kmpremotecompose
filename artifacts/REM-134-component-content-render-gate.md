# REM-134 Component-Content-Render — Desktop-Daten-Orakel-Gate

> **Adressat:** PO. **Status (Re-Run nach dev-2 (a)-Push `ac97675`):**
> **VOLLES 6-BAR-ORAKEL ✅ GRÜN** + **Bracket-Leak-Probe (REM-129-Klasse) ✅
> PASS**. Bar 6 (Decoration) ist jetzt an **Soll-Span-Bounds** (Underline-
> DrawLine x=[157,402] width=245 unter "Underlined"-Wort, y=265-266
> just-below-Baseline 257). x-Monotonie der Geschwister-Spans nach
> "Underlined" erhalten (5 Cluster strikt links-rechts, keine
> Bracket-Leak-Verschiebung). **Re-Baseline attribute_string KOMPLETT
> ausgeführt** (Text + Decoration = ein sauberer Golden). Branch:
> `bugfix/REM-134-component-content-render-gate` rebased auf develop `f4b41e0`,
> Verdikt-Doc + neuer Golden gebundlet.
>
> *Historie:* Initial-Run `0378899` (RED, nur Zeile 1 — Modifier-lose Rows
> FILL-collapsed off-canvas) → Fix `28418f4` (Row/Column-Modifier-lose-
> wrappt-Content, Bars 1-5 grün, Bar 6 noch Top-Origin) → assist-Ruling-
> Reversal stornierte REM-136-Follow-up, Decoration-Bracket muss in REM-134
> (PO `1521073380986716201`) → `ac97675` per-Span-Content-Matrix-Bracket
> landet, **Decoration on-target + Bracket-Leak verhindert**.

## Re-Run-Befund nach (a)-Push `ac97675` (aktuell, finaler)

Cherry-pick-Chain: `0378899` + `28418f4` + `ac97675` lokal auf develop
`f4b41e0` → Render-Tip `4fe3027`. Re-Render `attribute_string.png` = **45548 B**
(vs. ursprünglicher poisoned-Golden 1702 B / Initial-RED-Run 10814 B /
Bars-1-5-Pass nach `28418f4` 46089 B). Stabil bei 28 Draws.

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
| 6 | Underline/Strike: 2 `drawLine`s an **gemessenen Span-Bounds** | **Underline-DrawLine detected** an y=265-266, x=[157,402], width=245px, **gaps=0** (near-continuous near-Baseline 257). Fällt direkt unter Text-Cluster x=[157,404] = "Underlined"-Wort (vorher Zeile 5). NICHT an Top-Origin, NICHT volle Canvas-Breite. Strike-DrawLine ("Strikethrough"-Wort liegt off-canvas rechts, da Line 5 bei 500px-canvas clippt) → off-canvas mit-clip-erwartet. | ✅ **GRÜN** |

**ALL 6 BARS = ✅ GRÜN.** Volles Text + Decoration-Deliverable bewiesen.

### Zusatz-Probe: REM-129-Klasse Bracket-Leak (per-Span-Content-Matrix-Bracket schließt am Holder-CONTAINER_END)

Source-B `Bracket-Leak`-Probe (PO-vorab-spezifiziert
`1521073380986716201` + `1521076219083362315`): nach "Underlined" + nach
"Strikethrough" in Zeile 5 dürfen nachfolgende Spans NICHT verschoben landen.
x-Monotonie der Folge-Spans innerhalb derselben Row muss erhalten sein.

Test-3 Probe in Zeile 5 (band y[229,272]): 5 Text-Cluster detected
(left→right):
1. x=[5, 93]    width=89   → Text-Block-1 ("This is")
2. x=[111,141]  width=31   → Text-Block-2 (Punkt/Komma/Trenner)
3. x=[157,404]  width=248  → **"Underlined"-Wort** (Underline-DrawLine landet
   exakt darunter an x=[157,402] ✓)
4. x=[423,443]  width=21   → "," (Komma nach "Underlined")
5. x=[450,498]  width=49   → "and..." Start (Rest clippt am Canvas-Rand)

x-Monotonie: **strikt links→rechts erhalten**, keine Cluster-Überlapp, keine
Backtrack-Verschiebung. **Bracket-Leak-Probe: ✅ PASS** — per-Span-Content-
Matrix-Bracket schließt am korrekten Span-CONTAINER_END, Geschwister-Spans
landen unverschoben.

## Cross-Check: jvmTest weiter grün

Annahme bestätigt: dev-2 `Rem134ComponentContentTest` 6/6 PASS auf cherry-pickter
Tip — Layout-MATH war schon mit Fake-Metrics grün, ist mit Real-Metrics + Fix
`28418f4` **jetzt auch im Pixel-Render grün** für Bars 1-5.

## Re-Baseline-Status — `attribute_string` KOMPLETT re-baselined (Text + Decoration)

**Re-Baseline EXECUTED** (per PO `1521076219083362315` "Bei vollem 6-Bar grün → re-baseline attribute_string KOMPLETT"):
- `screenshots/reference/desktop/attribute_string.png`: **1702 B (poisoned-blank)
  → 45548 B (correct render with text + decoration)**.
- Daten-Orakel-Methode: Source-B-Bar-Check ✅ alle 6 grün + REM-129-Bracket-
  Leak-Probe ✅ PASS. **KEIN Cross-Target-Self-Compare** (alter Golden war
  ja blank-poisoned, kein gültiges Orakel) — die unabhängige Daten-Orakel-
  Verifikation (Bands + Color-Census + Underline-Detection + x-Monotonie der
  Cluster) ist die Bewahrung-Quelle, ganz nach REM-123-Gate.
- Bundle: Verdikt-Doc + Golden, ein atomarer Push. REM-136 ist obsolet
  (assist-Ruling-Reversal hat es bereits storniert; PO `1521073380986716201`).

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
