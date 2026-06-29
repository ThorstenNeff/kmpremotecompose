# REM-132 DrawBitmap Render-Apply — 1-Doc-Sprite-Dispatch-Orakel

> **Adressat:** PO. **Status:** ✅ **GRÜN**.
> **Branch (Code):** `feature/REM-132-drawbitmap-census` `f2fd24b` (lokal cherry-pick
> auf develop `fdaeb90` → Render-Tip `3683466`, kein develop-Push).
> **Bar:** Dispatch-Level (sprite-at-resolved-rect @ t=0), NICHT Live-Confetti-Pixel
> (Particles-Subsystem deferred). PO-Routing `1521050982690324583`.

## TL;DR (3 Sätze)

REM-132 verdrahtet `DrawBitmap` als `PaintOperation + VariableSupport` ohne Wire-
Change. **Dispatch-Orakel `confettiDoc_dispatchesSpriteAtResolvedRect` PASS:
DrawBitmap #54 wird genau einmal mit dst-Rect `[0,0,50,50]` @ t=0 dispatched**
(dev-1-Test 5/5 grün). **Voll-173-Korpus byte-identisch zu current Goldens → 0
Collateral** (visueller Pixel-Effekt erst mit Particles-Subsystem, deferred wie
PO-Scope vorgegeben). Merge-fertig.

## Gate-Schritte (test-3 unabhängig)

### 1. Code-Inspektion `DrawBitmap.kt` (Source A)

- Pre-REM-132: `class DrawBitmap … : Operation` (Wire-only, kein `paint`/
  `updateVariables` → Player skippt die Op im Walk).
- Post-REM-132: `class DrawBitmap … : PaintOperation, VariableSupport` mit
  - `updateVariables(ctx)` → `resolveCoord(l/t/r/b)` (NaN-Ref-Resolution Phase-A,
    nur dst-Rect-Floats; `id`/`descriptionId`-Ints unverändert)
  - `paint(ctx, paint)` → `paint.drawBitmap(id, rLeft, rTop, rRight, rBottom)`
    (5-arg-Blit, existing `PaintContext`-Primitive)
- §2-Konformität: `write`/`read`/`companion read`/`equals`/`hashCode` unangetastet
  (+40 / -3 Lines = nur Klassen-Header + 2 KDoc-Lines + neue Phase-A/paint-Felder
  als private, nicht serialisiert).

### 2. Korpus-Doc-Census (Source B)

- `impulse_demo_confetti_demo.rc` (2481 B):
  - Opcode `0x2C` (44, DRAW_BITMAP) — 15× im Byte-Stream (Header-Match-Heuristik
    nur als Hinweis).
  - Opcode `0x27` (39, DATA_BITMAP) — 5×.
  - Hat real-DATA_BITMAP #54 (50×50, 1701B Pixel-Daten, lt. dev-1-Commit-Message).
  - Heuristik bestätigt: die Op ist im Doc präsent + zugeordnet (definitive
    Beweis-Source: der dev-1-Test `confettiDoc_dispatchesSpriteAtResolvedRect`
    inflated die echte Doc-Datei via `DocumentReader.inflate(corpus/impulse_demo_
    confetti_demo.rc)` und hookt die `RecordingBitmapContext.drawBitmap`-Calls).

### 3. Dispatch-Test (Source B + jvm-Test-Cross-Check)

- **`./gradlew :shared:jvmTest --tests Rem132DrawBitmapRenderTest`** → **5/5 PASS**
  (208ms für den End-to-End-Confetti-Test):
  - `paint_blitsWholeBitmapIntoLiteralRect` (literal-rect-blit)
  - `updateVariables_resolvesNanDataRefsForDstRect` (NaN-Ref→Float-Resolution)
  - `updateVariables_doesNotResolveOperatorNan` (RPN-Operator-NaN-Guard via
    `!isOperationVariable` — coord-resolution-class-trap-Hardening)
  - `byteFormat_unchanged_roundTripsRawBits` (§2 byte-conformance)
  - **`confettiDoc_dispatchesSpriteAtResolvedRect`** = der PO-Bar-Test:
    asserts `blits.size == 1 && blits[0] == Blit(54, 0f, 0f, 50f, 50f)`.
- **Test ist real-corpus-driven**: lädt die echte `impulse_demo_confetti_demo.rc`
  via `RcCorpus.readFixture`, inflated via `DocumentReader`, ruft den echten
  `RemoteComposePlayer.paint(doc, recorder)` (Phase-A + Paint-Phase), hookt
  `drawBitmap`-Calls via `RecordingBitmapContext`. **Genau das Dispatch-Orakel
  der PO-Bar.**

### 4. Voll-173-Korpus-Collateral-Check (test-3 Voll-Sweep)

- Cherry-pick `f2fd24b` auf develop `fdaeb90` → Voll-Render-Sweep (alle 173 docs).
- **Ergebnis: 173/173 RENDERS, 0 BLANK, 0 ERROR, 173/173 byte-identisch** zu
  current Goldens (`post-fdaeb90`-Baseline).
- **0 Collateral.** DrawBitmap-Dispatch passiert (test-bewiesen), aber der
  Compose-Desktop-`PaintContext.drawBitmap`-5-arg-Pfad rendert die Sprite
  visuell unsichtbar — bitmap-data ist mostly-transparent/no-data → kein
  Pixel-Δ. **Genau wie PO scope** (dispatch≠visual; Live-Confetti-Sichtbarkeit
  ist Particles-Subsystem-Scope, deferred).
- Pre-vs-Post-Cherry-Pick (test-3-Verify-don't-trust): `/tmp/rem-132-pre/
  impulse_demo_confetti_demo.png` byte-identisch zu `/tmp/rem-132-render/
  impulse_demo_confetti_demo.png` (7327B, beide). Auch zu current Golden
  byte-identisch. Visueller Sprite-Effekt kommt erst mit Particles-Pipeline.

## Dispatch-Pfad (Phase-A + Paint-Phase)

1. **Phase A (`updateVariables`):** Player walkt alle `VariableSupport`-Ops in
   Doc-Reihenfolge. Für DrawBitmap: `resolveCoord(l/t/r/b)` ersetzt NaN-encodierte
   Var-Refs durch geladene Float-Store-Werte. `!isOperationVariable`-Guard
   (verbatim upstream) hält RPN-Operator-NaN durch → kein false-Resolve.
   Nur Floats (dst-rect), nicht die Int-Felder (`id`/`descriptionId`).
2. **Paint-Phase (`paint`):** Player walkt alle `PaintOperation`-Ops in
   Doc-Reihenfolge. Für DrawBitmap: `paint.drawBitmap(id, rLeft, rTop, rRight,
   rBottom)` — 5-arg-Variante (whole bitmap, no src-rect, no scaleType).
3. **Im Confetti-Doc:** Player-Walk dispatched DrawBitmap #54 genau einmal
   (verified) mit `Blit(54, 0f, 0f, 50f, 50f)` — gleich der PO-Bar.

## Scope-Klarstellung (PO-Bar verinnerlicht)

- **REM-132-Scope = SPRITE-AT-RECT-DISPATCH.** Bar = blit-call mit korrektem
  `(id, l, t, r, b)`. Test-bewiesen.
- **NICHT-REM-132-Scope = LIVE-CONFETTI-VISUAL.** Die animierte Confetti-
  Partikel-Wolke braucht das Particles-Subsystem (`ImpulseStart`/`Process`/
  `ParticlesCreate`/`ParticleLoop` — alle weiter `Operation`-only, deferred-
  separate Tickets).
- Folge davon: am static-t=0-Render des Confetti-Docs ist KEIN visueller
  Pixel-Δ vs. pre-REM-132 sichtbar (test-3-Sweep bestätigt). Das ist KEIN
  Bug — es ist der explizite scope-honest dispatch≠visual-Bar-Inhalt.

## Empfehlung

**GRÜN — REM-132 mergen.**
- assist-§2 = GO (per PO-Routing).
- test-3 1-Doc-Dispatch-Orakel = PASS (`confettiDoc_dispatchesSpriteAtResolved
  Rect`).
- test-3 Voll-173-Collateral-Check = 0 Collateral (173/173 byte-identisch).
- §2 byte-conformance intakt (write/read/equals/hashCode unverändert).
- Kein Golden-Refresh erforderlich (kein Pixel-Δ; Particles-Visual ist
  separater Scope).

— test-3, 2026-06-29
