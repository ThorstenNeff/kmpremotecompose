# L2 Render-Binding-Gap — Evidenz (test-2 → PO)

> **Befund (QA, verifiziert per Code-Read @ develop `ce91836`, S1+S2 gemergt):** Der Op→Paint-**Seam**
> ist **unverdrahtet**. S1 (Walk + `PaintContext`-Interface) und S2 (Canvas-Primitive) sind beide gebaut
> und isoliert getestet — aber **kein produktiver Draw-Op ruft die Primitive auf**. Folge: der Player
> rendert für **jedes echte `.rc` 0 Pixel** (Blank-Frame, kein Fehler).
> **Kein Patch durch test-2 — Dev-Arbeit.** Hier nur die Evidenz + die Gate-Konsequenz.

## Was gebaut ist (funktioniert isoliert)
- **Walk-Skelett** (`RemoteComposePlayer.paint`): läuft alle Ops, dispatcht `op.paint(ctx, paint)` **nur
  für `op is PaintOperation`**; alles andere wird still übersprungen.
- **Canvas-Primitive** (S2): `GeometryPaintDelegate.drawCircle → canvas.drawCircle`,
  `ComposePaintContext.drawCircle → geometry?.drawCircle`, etc. — getestet via `RecordingGeometryDelegate`
  + `PathGeometryTest`. **Die Primitive zeichnen, wenn man sie ruft.**

## Was FEHLT (der Seam)
**Kein produktiver `Operation` implementiert `PaintOperation`.** Der **einzige** Implementierer im ganzen
Baum ist `FakeCircleOp` — ein **Test-Fake** in `PlayerFoundationTest.kt`:

```kotlin
// PlayerFoundationTest.kt:88 — der Test-Fake zeigt das ERWARTETE Muster:
private class FakeCircleOp(val r: Float) : PaintOperation {
    override fun paint(context: RemoteContext, paint: PaintContext) = paint.drawCircle(0f, 0f, r)
}
```

Der reale `DrawCircle` (und `DrawRect`/`DrawOval`/`DrawArc`/`DrawSector`/`DrawLine`/`DrawRoundRect`/
`DrawPath`/`ClipRect`/`ClipPath`/`Particles*`/Matrix-/Paint-Ops) ist **`: Operation` only**, **ohne**
`paint()`. Die „PaintOperation"-Erwähnungen in deren Dateien sind **KDoc**, keine Deklarationen.

**Verifikation:**
- `grep` „: PaintOperation" über `shared/src` → **nur** `FakeCircleOp` (Test). 0 produktiv.
- `grep` „paint." in `operations/draw/*.kt` → **0 Treffer** (kein Op ruft ein Primitiv).
- `RemoteComposePlayer` referenziert von: nur sich selbst + `PlayerFoundationTest` (Fake). **Keine App,
  kein Real-Doc-Render-Test.**
- `PlayerFoundationTest` beweist: der Walk dispatcht **einen Fake** korrekt — nicht, dass ein echtes
  `.rc` zeichnet.

## Konsequenz für die Doc→S2-Abdeckung (PO-Frage)
**Aktuell rendern 0 der 72 Geometrie-Docs Pixel** — nicht mangels Primitiven, sondern weil der
Op→Primitiv-Seam unverdrahtet ist. Der Render-Sweep „welche Docs voll rendern" ist erst aussagekräftig,
**nachdem** die Draw-Ops `PaintOperation` implementieren (Muster = `FakeCircleOp`, real je Op).

## Konsequenz für mein Smoke-Gate (kritisch — false-green-Loch)
`render_smoke.yaml` grünt über `rc-rendered` = **nach erstem Frame-Commit**. Mit unverdrahtetem Seam ist
der erste Frame ein **Blank-Canvas** → `rc-rendered` würde auf **leerem Bild grün** = **false green** —
**genau** der Fehlermodus, gegen den der Hook-Contract gehärtet wurde. **Sobald dev-2 die App gegen
meinen Vertrag baut, würde der Smoke fälschlich grünen, obwohl nichts gezeichnet ist.**

**Hook-Contract-Amendment (test-2-Lane, Vorschlag):** `rc-rendered` darf erst feuern, wenn der
Player-Walk im committeten Frame **≥1 Draw-Primitiv ausgeführt** hat (nicht nur „ein Frame committed").
Konkret: der Player/`RemoteContext` zählt ausgeführte `paint.*`-Aufrufe; die App setzt `rc-rendered` nur
bei Count > 0, sonst (Decode ok, aber nichts gezeichnet) `rc-error` „rendered empty". Das schließt das
Loch **jetzt** — und macht den Smoke ehrlich rot, solange der Seam fehlt (korrekter Test-first-Zustand),
statt false-green.

## Empfehlung (an PO)
1. **Op→Paint-Binding als eigene Aufgabe** an einen Dev (jeder Draw-Op implementiert `PaintOperation`,
   ruft das passende Primitiv — Muster steht im `FakeCircleOp`). Das ist die **echte** „rendert"-Gate,
   nicht die App-Hülle. Prüfen, ob dev-2s gerade dispatchte „App-Wiring gegen den App-Vertrag" das
   abdeckt — der App-Vertrag (Shell/Hooks/Deep-Link) tut es **nicht**; das ist Core-Player-Arbeit.
2. **Smoke bis dahin rot/ungescharf** lassen (mit dem Count>0-Amendment), nicht auf Blank-Frame grünen.
3. Sobald gebunden: ich fahre den Render-Sweep gegen die real zeichnenden Ops → echte Doc→S2-Abdeckung.
