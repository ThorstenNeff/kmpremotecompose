# REM-123-Gate: `experimental_gmt` + `experimental_solar_gmt` — Post-Wave-Sweep-FLAG

**Branch:** `bugfix/REM-117-postwave-sweep-3940280` (zweiter Commit auf dem
Sweep-Branch, wie von PO geroutet Discord `1520920057360941067`).
**Datum:** 2026-06-29.
**Methode:** REM-123 Daten-Orakel-Gate, Variante A (pure-data Cross-Check).
**Anti-Anchoring-Disziplin:** Source B (dev-2 Coord-Orakel `1520922501918752858`)
**vor** Source A (Render-PNGs) gelesen.

## Quellen

**Source B — Coord-Orakel (dev-2, t=0):**
- `1782686542581-1520922147910979776.md` — Befund-Memo mit Geometrie-Hypothesen.
- `1782686542829-1520922148150181950.txt` — Voll-Inventar (113 + 128 Draws,
  Device-Space-BBox + Paint-Style pro Draw, Voll-Player @ t=0 mit Recording-
  PaintContext; Eval RpnFloatEvaluator/MonotonicSpline; self-validated über
  „erkennbar korrekte Uhr" inkl. Tag-Ring + MON..SUN-Labels).
- **Source-B-Caveat (dev-2 dokumentiert):** Recording-Context erfasst Save/
  Restore/Rotate/Translate/Scale, **NICHT** CLIP. Source-B-BBox = Shape-BBox
  ohne aktive Clip-Anwendung.

**Source A — Renders:**
- Baseline-PNGs: `screenshots/reference/desktop/{experimental_gmt,
  experimental_solar_gmt}.png` (`8e8013d` REM-117-Anker bzw. `0050282`
  REM-102-Anker, je nach Doc).
- New-PNGs: `/tmp/rem-postwave-sweep/{…}.png` (develop `3940280`, density=1.0).

## ⚠️ Schritt 1 — Korrektur der „-20%-Pixel-Drop"-Aussage aus dem Sweep-Report

**Mein voriger Befund** (REM-117-postwave-sweep-3940280.md): `experimental_gmt
-23%` / `experimental_solar_gmt -19%` Non-bg-Pixel-Drop.

**Korrektur:** Die `-20%`-Zahl ist ein **Mess-Artefakt** der Metrik
„Non-bg = total − most-common-color":
- Baseline-most-common = `0x7a7b7b` (grau, Tag-Ring + Labels, 183k px).
- New-most-common = `0x323288` (blau, Face-Disc, 288k px).
- Die „bg-Definition" verschob sich zwischen den beiden Renders, weil der
  dominante Farbton kippte.

**Korrektur-Metrik (white-fixed-bg = canvas-Hintergrund):**

| Doc | Baseline non-white | New non-white | Δ |
|---|---:|---:|---:|
| `experimental_gmt` | 512,767 | 512,767 | **±0** |
| `experimental_solar_gmt` | 507,483 | 507,432 | **-51 (-0.01%)** |

→ Die **tatsächlich gefüllte Canvas-Content-Fläche ist byte-identisch**
(gmt) bzw. um <0,02 % verschoben (solar). Es ist **kein Content-Volume-Drop**.
Was sich geändert hat, ist die **Farbe an bestehenden Positionen** — eine
Umfärbung großer Bereiche. Mein voriger Sweep-Report war an dieser Stelle
**irreführend**; korrigiere das hier formal.

## Schritt 2 — Wo ist die Umfärbung?

**Per-pixel-Diff:** gmt 240,755 px (37,6 %), solar 165,091 px (25,8 %).

**Farb-Verteilung (Top-5, base→new):**

`experimental_gmt`:
| Farbe | Quelle (Source B) | Baseline | New | Δ |
|---|---|---:|---:|---:|
| `0x323288` Mid-Blau | #30 Face-Disc r=320 | 126,287 | 287,933 | **+161,646** |
| `0x7a7b7b` Grau | #97 Tag-Ring r=243 + Labels | 183,226 | 25,652 | **-157,574** |
| `0x5e1a1a` Rot | #3 BG-Day clipped lower-half | 150,404 | 75,791 | **-74,613** |
| `0x1a1a5e` Tief-Blau | #1 BG-Night full 800² | 0 (!) | 74,613 | **+74,613** |
| `0xffffff` Weiß | Canvas-BG | 127,233 | 127,233 | 0 |

`experimental_solar_gmt`:
| Farbe | Quelle (Source B) | Baseline | New | Δ |
|---|---|---:|---:|---:|
| `0x323288` Mid-Blau | #1 Face-Disc r=320 | 119,499 | 269,380 | **+149,881** |
| `0x7a7b7b` Grau | #112 Tag-Ring r=243 + Labels | 182,764 | 26,453 | **-156,311** |
| `0xffffff` Weiß | Canvas-BG | 132,517 | 132,568 | +51 |
| `0x000000` Schwarz | Text / Stroke | 118,804 | 118,340 | -464 |
| `0x0000ff` Blau | ARC-Gradient | 25,786 | 25,786 | 0 |

**Pure-Red `0xff0000`** (PATH#111 / PATH#170 — die roten FILL+STROKE-Regionen,
inkl. solar's off-canvas-y=946-Region): **byte-identisch** (gmt: 1772 ↔ 1772;
solar: 2840 ↔ 2840).

→ POs Hypothese "solar PATH#170 off-canvas-Clipping erklärt -19 %" ist
**falsifiziert**: PATH#170 (red) ist pre/post **identisch**. Die roten
FILL+STROKE-Regionen sind nicht der Swing.

## Schritt 3 — Sample-Probes (Source-B-positioniert)

Für beide Docs identisches Muster bei Tag-Ring-Disc-Positionen
(r=243 ab Center (400,400)):

| Position | Source-B-Erwartung | Baseline | New | ≠? |
|---|---|---|---|---|
| (400, 600) ring-S | Grau Tag-Ring | `#7a7b7b` ✓ | `#323288` ✗ | ≠ |
| (200, 400) ring-W | Grau Tag-Ring | `#7a7b7b` ✓ | `#323288` ✗ | ≠ |
| (250, 250) ring-NW | Grau Tag-Ring | `#7a7b7b` ✓ | `#323288` ✗ | ≠ |
| (550, 550) ring-SE | Grau Tag-Ring | `#7a7b7b` ✓ | `#323288` ✗ | ≠ |
| **(600, 400) ring-E** | Grau Tag-Ring | `#7a7b7b` ✓ | `#7a7b7b` ✓ | = |
| **(550, 400) CLIP#96-area** | Grau Tag-Ring | `#7a7b7b` ✓ | `#7a7b7b` ✓ | = |
| (400, 90) face-N (außerhalb Ring) | Blau Face | `#7a7b7b`* | `#7a7b7b`* | = |

* Bei (400, 90) zeigt baseline+new beide grau – beide haben hier eine Label-
  oder Stundenzahl, das ist kein Disagreement.

**Kritisches Muster:** Die Tag-Ring-Grau-Pixel überleben in NEW **NUR** in der
Region [480..638]×[360..440] (E-Seite, exakt CLIP#96-Area aus Source B Line 96
für gmt / Line 111 für solar: `CLIP dev=[480.0,360.0 .. 638.0,440.0] ~158x80
area=12640`). Außerhalb dieser Rect-Fläche wird Tag-Ring **NICHT gezeichnet**
und das darunterliegende Face-Disc-Blau bleibt sichtbar.

## Schritt 4 — Cross-Check & Mechanismus-Verdikt

**Verifizierbar:**
- ✅ **REM-124-ClipRect-VariableSupport-Fix funktioniert mechanisch korrekt.**
  Beleg: `0x1a1a5e` (Tief-Blau, BG-Night-#1) ist in NEW (+74,613 px) sichtbar,
  in BASELINE = 0 px. Pre-fix war CLIP#2 [0,400..800,800] mit NaN-Bounds
  degeneriert → BG-Day-#3 zeichnete unkontrolliert über die obere Nacht-Hälfte.
  Post-fix CLIP#2 resolved korrekt → Nacht-BG bleibt oben sichtbar. **Legit
  Overdraw-Korrektur.**

- ✅ **PATH#170 (solar's „off-canvas-by-27%"-Region) ist NICHT der Swing.**
  Red-Pixel-Count pre/post **identisch**. PATH#170 war pre-fix bereits korrekt
  am Canvas-Rand y=800 abgeschnitten. POs off-canvas-Clipping-Hypothese
  beschreibt nicht den beobachteten Effekt.

**Newly exposed:**
- ⚠️ **CLIP#96 (label-Area [480..638]×[360..440]) persistiert in den Tag-Ring-
  Draw (#97 gmt / #112 solar).** Pre-fix war CLIP#96 unter NaN-Bounds bedeu-
  tungslos → Tag-Ring zeichnete voll (r=243 ≈ 185k px Grau). Post-fix
  resolved CLIP#96 zu seiner echten 158×80-Box und schneidet den Tag-Ring auf
  exakt diese Fläche zu (12,6k px statt 185k px).

Source-B-Caveat („Recording-Context erfasst CLIP NICHT") erklärt, warum
Source B trotz aktivem CLIP#96 den Tag-Ring mit voller r=243-BBox listet —
Source B zeigt **Intent (BBox der Shape)**, Render zeigt **Effekt (mit aktivem
Clip)**.

**Design-Intent (aus Source B's „erkennbar-korrekte Uhr"-Memo):** Tag-Ring r=243
als grauer Disc backing MON..SUN-Labels (#98..#104 sind exakt am r=200-Ring
positioniert, lesen brauchen graue Backplate). Tag-Ring-auf-12,6k-px = **Design-
Intent verfehlt** für beide Docs.

## Verdikt

**Mechanismus = LEGIT.** REM-124-ClipRect-VariableSupport-Fix arbeitet korrekt;
der vorhergesagte Effekt (CLIP-NaN-Resolution) ist verifiziert (BG-Night-Sicht-
barkeit, BG-Day-korrekt-clipped).

**Visuelles Outcome für `experimental_gmt` + `experimental_solar_gmt` = REGRESS.**
Tag-Ring-Disc visuell von ~185k px auf ~12,6k px reduziert, weil CLIP#96
(Label-Area-Clip) jetzt korrekt resolved aber **vor #97/#112 nicht
zurückgesetzt wird**. Pre-fix-NaN-CLIP hatte das Problem **zufällig maskiert**.

**Ursache liegt nicht in REM-124, sondern in der CLIP-Stack-/Save-Restore-
Semantik** zwischen #96-Label-Clip und #97/#112-Tag-Ring-Draw (gleicher Bug
in beiden Docs — vermutlich gleiche `.rc`-Encoder-Idiom, das pre-fix unter dem
NaN-Maskierungs-Schleier funktionierte).

**PO-Routing-Mapping:**
- **Nicht „legit Overdraw-Korrektur"** im PO-Sinn → **kein** Re-Baseline der
  beiden Goldens (die NEU-Goldens haben kein Tag-Ring; die ALT-Goldens hatten
  ihn zufällig korrekt).
- **Ja „Regress"** im visuellen Sinn (Design-Intent verfehlt) →
  **Bugfix-Ticket** für die CLIP-Persistenz-Semantik.

**Empfehlung für das Bugfix-Ticket (Notiz für dev):**
- Doc-Locus: CLIP #96 in gmt (Line 96 des Inventars) / #111 in solar (Line 111
  des Inventars), beide `dev=[480,360..638,440] area=12640`.
- Question: ist nach dem Label-Text-Render (#95/#110 jeweils) ein implizites
  Save/Restore oder explizit-encoded Clip-Restore vorgesehen, das aktuell vom
  Player nicht erkannt/respektiert wird?
- Bug-class-relativ: nicht NaN-Resolution (das ist REM-124, done), sondern
  CLIP-Scope/Stack-Management.
- Reproduzierbar: nur diese 2 Docs (vermutlich gleiches Encoder-Idiom), keine
  weiteren Sweep-Treffer.
- Test: ein synthetischer Mini-Test, der CLIP + FILL + (intent-restore) + FILL
  ausführt und prüft, dass der zweite FILL nicht auf den CLIP eingeschränkt
  ist.

## File-Disjunktheit

Branch enthält ausschließlich:
- Vorherigen Sweep-Verdikt-Commit `8bd048e` (REM-117-postwave-sweep-3940280.md).
- Diesen zweiten Verdikt-Commit unter `artifacts/REM-117-postwave-gmt-gate.md`.
- Keine Code-/PNG-/Sweep-CSV-Änderungen. Goldens unangetastet.
