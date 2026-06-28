# REM-117 / REM-129: Verdikt-Flip-Verifikation — Upstream-Source = Intent-Orakel

**Branch:** `bugfix/REM-117-postwave-sweep-3940280` (dritter Commit auf dem Sweep-Branch).
**Datum:** 2026-06-29.
**Vorgeschichte:**
- Commit 1 `8bd048e` — Post-Wave-Sweep-Befund (2 FLAGs).
- Commit 2 `96afd98` — REM-123-Gate-Verdikt = visuell-REGRESS-Hypothese (CLIP#96
  persistiert in Tag-Ring-Draw).
- **Dieser Commit** — REM-129-Flip-Verifikation: dev-2 hat den Code direkt am
  Upstream-Source `androidx/.../Clock.kt` `RcSimpleClock1` untersucht und
  identifiziert, dass der **volle Tag-Ring NIE der Intent war** — der pre-fix
  NaN-Clip hatte das Day-Complication-Clip-Pattern maskiert.

**PO-Routing:** Discord `1520928692853801090` — Verifizier dev-2s Source-Zitat
direkt + check NEW = upstream-treu; bestätigt → re-baseline + Branch schließen.

## Source-Zitat verifiziert

Datei: `/Users/thorstenneff/remote_compose/androidx/compose/remote/integration-tests/player-view-demos/src/main/java/androidx/compose/remote/integration/view/demos/examples/Clock.kt`,
Funktion `RcSimpleClock1`, Lines **281–300**:

```kotlin
// =============== DAY Complication ===============
val dayCenterX = centerX + rad - 280f
val dayLeft = dayCenterX - 46f
val dayRight = dayCenterX + 46f

clipRect(dayLeft, dateTop, dayRight, dateBottom) {
    drawCircle(
        Color.LightGray.paint(),
        RemoteOffset(centerX, centerY),
        dateLeft - centerX,
    )
    for (i in 0 until 7) {
        val anim = remote.animateFloat((timeSeconds + i.toFloat()) * 360f / 7f, 0.2f)
        rotate(anim, RemoteOffset(centerX, centerY)) {
            drawAnchoredText(
                days[6 - i].rs,
                dayCenterX,
                centerY,
                0f.rf,
                0f.rf,
                paint = Color.Black.paint(textSize = 40f),
            )
        }
    }
}
```

**Intent-Pattern (verifiziert direkt am Quellcode):**
1. `clipRect(dayLeft, dateTop, dayRight, dateBottom) { ... }` — **scoped clip**
   um das DAY-Complication-Window.
2. **Innerhalb des Scopes:**
   - `drawCircle(Color.LightGray, (centerX, centerY), dateLeft - centerX)` —
     der „Tag-Ring" (graue Kreis-Backplate). **Hat seinen Center auf der Canvas-
     Mitte, aber wird absichtlich aufs Day-Slot-Fenster geclippt** → nur der
     Ring-Ausschnitt im Slot ist sichtbar.
   - `for (i in 0 until 7) rotate(...) { drawAnchoredText(days[6-i], ...) }` —
     7 rotierende Wochentag-Texte um den Canvas-Mittelpunkt. Auch geclippt
     aufs Day-Slot-Fenster → nur der aktuell rein-rotierte Tag erscheint.
3. **Der Kotlin-DSL-Block `clipRect(...) { ... }` ist scoped** (entspricht
   Save→Clip→…→Restore-Pattern in `.rc`-Bytes).

**Bestätigung von POs Aussage:** „der volle Ring war NIE der Intent" = **korrekt**.
Der Tag-Ring-LightGray-Disc steht definitionsgemäß **inside** des
clipRect-Blocks; sein voller r-Sichtbarkeits-Range ist **nicht erwünscht** —
nur sein Ausschnitt durch das Day-Slot-Fenster.

## Source-A-(Render)-Check gegen Intent

**Probes auf NEW-Render gegen den day-complication-clip-rect [480..638, 360..440]:**

`experimental_gmt`:

| Position | Drinnen/Draußen | Baseline-Pixel | New-Pixel | Verdikt |
|---|---|---|---|---|
| (490, 390) | **drinnen** | `#7a7b7b` Tag-Ring | `#7a7b7b` Tag-Ring | = identisch |
| (520, 400) | **drinnen** | `#7a7b7b` | `#7a7b7b` | = identisch |
| (560, 400) | **drinnen** | `#7a7b7b` | `#7a7b7b` | = identisch |
| (600, 400) | **drinnen** | `#7a7b7b` | `#7a7b7b` | = identisch |
| (620, 420) | **drinnen** | `#7a7b7b` | `#7a7b7b` | = identisch |
| (630, 365) | **drinnen** | `#7a7b7b` | `#7a7b7b` | = identisch |
| (200, 400) | draußen | `#7a7b7b` ✗ leak | `#323288` Face ✓ | ≠ NEW = upstream-treu |
| (400, 600) | draußen | `#7a7b7b` ✗ leak | `#323288` Face ✓ | ≠ NEW = upstream-treu |
| (250, 250) | draußen | `#7a7b7b` ✗ leak | `#323288` Face ✓ | ≠ NEW = upstream-treu |
| (550, 550) | draußen | `#7a7b7b` ✗ leak | `#323288` Face ✓ | ≠ NEW = upstream-treu |

`experimental_solar_gmt`: identisches Muster.

**Voll-Region-Check inside-clip [480..638, 360..440] (12,640 px):**
- Beide Docs: **100,0 % pixel-identisch baseline ↔ new** (12640/12640).
- Top-5 Farbverteilung identisch in Baseline und New (Tag-Ring-grau 91,1 %,
  Face-blue-Edge 5,3 %, AA-Misch 0,2..0,2..0,2..0,2 %).

**Voll-Region-Check outside-clip (627,360 px):**
- ALLE Pixel mit alt-Tag-Ring-grau-leak werden in NEW durch Face-Disc-blau ersetzt.
- Pure-Red `0xff0000` (Hand/Wave) byte-identisch (1772 / 2840 px in gmt/solar).
- BG-Night `0x1a1a5e` (deep blue) NEU sichtbar (+74,613 px in gmt) — REM-124-
  ClipRect-Fix korrigiert auch die BG-Day-CLIP#2 (separater positiver Effekt,
  bereits in Commit 2 als „LEGIT" verifiziert).

## Verdikt-Flip

**Mein Commit-2-Verdikt war eine Intent-Inferenz aus dem kaputten Alt-Golden.**

Ich hatte angenommen, „Tag-Ring backing für MON..SUN-Labels" wäre der Design-
Intent, weil der Alt-Golden den vollen Ring zeigte. Tatsächlich ist der Alt-
Golden der pre-REM-124-NaN-Clip-Bug — er hat das Day-Complication-Clip-Pattern
zufällig zu „Voll-Ring + Voll-Label-Span" entstellt. Das ALT-Golden ist **kein
gültiges Intent-Orakel**.

Das **gültige Intent-Orakel ist der Upstream-Source** `RcSimpleClock1` (Lines
281–300 oben zitiert). Dort steht **schwarz auf weiß**, dass Ring + 7 Tag-Texte
in einen scoped clipRect gehören.

**Korrektes Verdikt:**
- ✅ **REM-124-ClipRect-VariableSupport-Fix = LEGIT** (war Commit 2 schon).
- ✅ **NEW-Render = upstream-treu**: Tag-Ring-Ausschnitt nur im Day-Slot
  sichtbar, restliche Pixel byte-identisch zu Baseline.
- ✅ **ALT-Golden = poisoned**: voller Tag-Ring war NaN-Clip-Bug-Artefakt.
- ✅ **REM-129 (CLIP-Persistenz-Bug-Hypothese) = NICHT-BUG**: es gibt keinen
  Player-CLIP-Stack-Fix nötig; der Player-ClipRect war schon korrekt
  save/restore-scoped (PO-Bestätigung Discord `1520928692853801090`).

## Lehre (memory-würdig)

**Goldens sind kein gültiges Intent-Orakel.** Wenn ein Render visuell vom
Golden abweicht UND der ursprüngliche Bug-Kontext (hier: NaN-Clip vor REM-124)
das Golden **vermutlich** verzerrt hat, muss das Intent-Orakel **außerhalb des
Golden** liegen — Upstream-Source-Code, Test-Util-Referenz, byte-equivalence-
Check gegen Upstream-Writer.

Mein Commit-2-Verdikt-Fehler war: ich nahm die Voll-Tag-Ring-Visual aus dem
Alt-Golden als „Design-Intent" und schloss daraus auf einen Player-CLIP-Bug.
Tatsächlich war der Alt-Golden selbst die Manifestation des Bugs. Diese
Bug-Class-Zirkularität ist genau das, was die `feedback_goldens_bug_class_
relative`-Memory adressiert — Goldens sind bug-class-relativ trustworthy, und
**hier wurde eine NaN-Clip-Bug-Klasse irrtümlich als „Tag-Ring-Visual-Intent"
gelesen**. Memory-Update ergänzt.

## Dev-2s t=0-Soll cross-check (Source-B-update)

PO `1520929846941974598` lieferte dev-2s explizites t=0-Soll
(`1782688298886-1520928958663889026.md`) — drei Behauptungen:
1. Tag-Ring nur im Slot `[480,360..638,440]` als horizontaler grauer Streifen
   rechts der Mitte auf Höhe y≈400 → **bestätigt** durch meine 100%-Pixel-
   Identitäts-Region inside-clip + Outside-Tag-Ring-Verschwinden.
2. Sichtbares Day-Label @ t=0 = genau „SUN" @ ~(559, 400) → **bestätigt** durch
   Source-B-Inventar `#104 TEXT "SUN" @(558.9,399.9)` (gmt) und `#119` (solar);
   (559, 400) liegt im Slot [480..638, 360..440].
3. MON..SAT-Anker außerhalb des Slots → **bestätigt** durch Source-B-Anker:
   - MON @(499.1, 524.3) — y=524 > 440 → außerhalb ✓
   - TUE @(364.6, 555) — x=365 < 480 + y=555 > 440 → außerhalb ✓
   - WED @(256.7, 468.9) — x=257 < 480 + y=469 > 440 → außerhalb ✓
   - THU @(256.7, 331) — x=257 < 480 + y=331 < 360 → außerhalb ✓
   - FRI @(364.6, 244.9) — x=365 < 480 + y=245 < 360 → außerhalb ✓
   - SAT @(499.1, 275.6) — x=499 in range + y=276 < 360 → außerhalb ✓

Damit dreifach-konfirmiert: **Upstream-Source-Code (Lines 281–300)** +
**dev-2s t=0-Soll-Memo** + **Rendered-Pixel-Probes** stimmen überein. NEW-
Render ist upstream-treu, ALT-Golden ist poisoned.

## Re-Baseline ausgeführt

Dieser Commit überschreibt:
- `screenshots/reference/desktop/experimental_gmt.png`:
  113638 → 92868 Bytes (NEU = upstream-treu, REM-117-anker → 3940280-anker).
- `screenshots/reference/desktop/experimental_solar_gmt.png`:
  128385 → 112139 Bytes (NEU = upstream-treu, REM-117-anker → 3940280-anker).

Sweep-CSV (`screenshots/reference/desktop/_sweep.csv`) wird **NICHT** punktuell
angefasst — sie hängt am REM-117-Anker und ist im Wave-Kontext insgesamt
veraltet; ein eigener `REM-117-postwave-baseline-refresh` würde sie sauberer
refresh'en. PO entscheidet, ob das ein separates Routing wird.

## Branch-Status

Dieser Branch (`bugfix/REM-117-postwave-sweep-3940280`) trägt jetzt:
1. `8bd048e` — Sweep-Befund.
2. `96afd98` — REM-123-Gate-Verdikt-Hypothese (REGRESS) — **historisch erhalten
   als Methoden-Beweis-Anker**, nicht entfernt.
3. **Diesen Commit** — Verdikt-Flip-Verifikation + Re-Baseline der 2 Goldens.

PO-Routing: kann mergen + Branch schließen + REM-129 als „NOT-A-BUG, falsch
gerouted, korrekt via Upstream-Source-Cross-Check aufgelöst" schließen.
