# TECHSPEC — REM-134: Component-Content Render-Pfad (DrawContent-Delegation) — FINAL

> **Status:** FINAL (assist-finalisiert aus dev-2-Scoping-Draft `3a8b43e` +
> `TECHSPEC-REM-134-component-content-render-DRAFT.md`). Decode-grounded gegen `attribute_string.rc`
> (266 ops dekodiert) + Render-Pfad-Map (RemoteComposePlayer / LayoutMeasure / CoreText / PaintContext).
> Story **REM-134** (verlinkt REM-72). **Kein Impl vor diesem Spec.** Upstream = Verhaltens-Ref
> (PROJECT_CONTEXT §5, kein Paste). **§2-Byte-Invariante bleibt oberste Regel: render-only, kein
> Binärformat-Touch.**

---

## 1. Entscheid (die Modell-Touch-Frage)

**Weder Option A noch Option B aus dem Draft — sondern Option C (Hybrid): DrawContent delegiert an
seine umschließende TextLayout-Komponente, verdrahtet über den BEREITS EXISTIERENDEN
LayoutMeasure-Tree.**

Begründung, decode- + map-gestützt:

- **Pure Option B (LAYOUT_TEXT malt inline an seiner Stream-Position) ist NICHT bloß ein
  Positions-Risiko — es ist ein Z-ORDER-BUG, decode-bewiesen.** Im `attribute_string`-Span
  „Yellow Background" steht die Op-Reihenfolge: `TextLayout`[138] → `BackgroundModifier`[140] →
  `DrawContent`[143]. BackgroundModifier malt an Stream-Position [140]. Malt TextLayout an [138]
  (seiner Deklaration), liegt der Text VOR dem gelben Hintergrund → der Hintergrund übermalt ihn
  → Text unsichtbar. Der einzig z-order-korrekte Paint-Punkt ist `DrawContent`[143] (nach den
  Modifiern). → **Text MUSS an der DrawContent-Position gemalt werden, nicht an der
  TextLayout-Deklaration.** Damit ist „LAYOUT_TEXT self-paint" raus.

- **Full Option A (zweiter, persistenter Component-Tree + `setComponent` im Player-Paint-Walk) ist
  überdimensioniert.** `LayoutMeasure` baut **bereits** einen Shallow-Component-Tree
  (`LayoutMeasure.kt:204–253`) und liefert gemessene Bounds an Ops via `setBounds()` /
  `setTextDraw()` / `setTextBox()` (`:392–402`). Einen ZWEITEN Tree im Player-Paint-Walk
  aufzubauen dupliziert vorhandene Infrastruktur und macht den Flat-Walk stateful — größter
  Eingriff, kein Gegenwert bei Blast-Radius 1.

- **Option C renutzt die bewiesene CoreText-Measure→Paint-Verdrahtung.** Genau wie `CoreText`
  (`CoreText.kt`, `Operation, PaintOperation`) heute vom Measure-Pass positioniert wird
  (`setTextDraw(x, baseline)` / `setTextBox(...)`) und sich dann im Paint-Walk an seiner Position
  selbst malt, setzt der Measure-Pass die aufgelöste Text-Draw-Info auf die **DrawContent**-Instanz
  (das ist der z-order-korrekte Paint-Punkt), und `DrawContent.paint()` malt den Text der
  umschließenden TextLayout über dieselben PaintContext-Primitiven (`drawTextRun` /
  `drawComplexText`). Kein Player-Walk-Refactor, kein zweiter Tree, z-order-korrekt. Upstream-treu
  im Geist (DrawContent IST der Delegations-Platzhalter — wir lösen die Delegation zur Measure-Zeit
  statt über einen Laufzeit-Tree).

**Maßstab:** Blast-Radius = exakt 1 Korpus-Doc (`attribute_string`; `paths_demos`-DrawContent ist
off-render-path no-op — beide vom Draft korpus-weit über alle 173 verifiziert). Option C ist die
verhältnismäßige Wahl, die trotzdem keine falsche Geometrie riskiert.

---

## 2. Was der Decode wirklich zeigt (Struktur, die das Design treibt)

`attribute_string.rc` (2459 B, 266 ops). Pro Span ein wiederholtes Muster (Bsp. ops [12–25]):

```
RowLayout (Span-Zeilen-Container, FILL, vPos=4)
  LayoutContent
    TextData      (DATA_TEXT id=42  text="AttributedString Demo:")   ← der String
    TextLayout    (LAYOUT_TEXT id=-9  text=42  color/fontSize/fontStyle/fontWeight/...) ← Text-Komponente
    AlignByModifier (line=NaN, flags=0)                              ← Baseline-Alignment auf der Komponente
    LayoutContent (EIGENER Content-Slot der TextLayout)
      CanvasOperations (CANVAS_OPERATIONS)
      DrawContent (DRAW_CONTENT)                                     ← „male meinen Content HIER"
    ContainerEnd ×N
```

Festgestellte Fakten (alle decode-verifiziert):
- **Text liegt in `TextLayout` (LAYOUT_TEXT, op 208)**, das eine `text`-id auf ein `TextData`
  (DATA_TEXT, op 102) referenziert + Style (color, fontSize, fontStyle, fontWeight, textAlign,
  overflow, maxLines) trägt. 24 TextLayout, 24 DrawContent, 24 AlignByModifier, 18 TextData
  (ids werden geteilt/wiederverwendet — z.B. text=43 „This is " bei [30]/[160]/[216]; text=47 „."
  mehrfach).
- **`DrawContent` sitzt im EIGENEN Content-Slot der TextLayout, NACH deren Modifiern** (Z-Order-Punkt).
- **Spans liegen in `RowLayout`s** (eine Row pro sichtbarer Zeile, mehrere Spans pro Row),
  Baseline-Alignment via `AlignByModifier(line=NaN)` pro Span. `line=NaN` = Baseline-Sentinel.
- **Underline/Strikethrough** (Spans „Underlined"/„Strikethrough") nutzen
  `ComponentValue(type=0|1, component=-48, value=55|56)` (→ Breite/Höhe der gemessenen Komponente
  in Float-ids) + `FloatExpression` + `DrawLine` — die Geometrie hängt an den **gemessenen Bounds
  der TextLayout-Komponente**.

---

## 3. Der Gap ist DREITEILIG (nicht „DrawContent malt nicht")

Map-bestätigt (RemoteComposePlayer / LayoutMeasure):

1. **LayoutMeasure kennt `LAYOUT_TEXT` (TextLayout) NICHT** als Node-Typ (`:214–247` baut Root/Box/
   Canvas/Row/Column/LayoutContent/CanvasContent/ComponentStart/**CoreText**/Modifier — TextLayout
   fehlt). → Span-Komponenten werden nie gemessen/positioniert → keine Bounds, keine Baseline,
   und `ComponentValue` für diese Komponenten kann nicht korrekt auflösen (Underline/Strike-Geometrie).
2. **`MODIFIER_ALIGN_BY` ist im Measure-Pass NICHT implementiert** (AlignByModifier-Op existiert
   byte-faithful, wird aber von LayoutMeasure nicht verarbeitet) → keine Baseline-Ausrichtung in der Row.
3. **`DrawContent` ist `Operation`-only** (`DrawContent.kt:28`, zero-payload, kein paint) → die
   Delegation, die den Text malen würde, existiert nicht.

**Wichtig:** Sub-Gap 1+2 (Measure-Arbeit) sind unter JEDER Option (A/B/C) nötig. Der einzige
Options-Unterschied ist der Paint-Mechanismus (Sub-Gap 3). Das ist der Kern, warum Option C
proportional ist: minimaler Paint-Mechanismus auf vorhandener Measure-Infrastruktur.

---

## 4. Design (Option C, normativ)

### 4.1 LayoutMeasure lernt TextLayout als Text-Node (Sub-Gap 1)
- TextLayout (LAYOUT_TEXT) wird im Tree-Build als **text-tragender Container-Node** behandelt
  (spiegelt das CoreText-Node-Handling): der Node hält `textId` + Style; sein eigener
  Content-Slot (LayoutContent → CanvasOperations → DrawContent) bleibt korrekt darunter genestet.
- **Sizing:** Intrinsische Bounds via `PaintContext.getTextBounds(textId, …)` (existiert,
  `PaintContext.kt:152`); `textLeft`/`textTop` (Baseline-Offset) speichern — exakt wie CoreText
  (`LayoutMeasure.kt:269–284`). Style (fontSize/fontStyle/fontWeight) muss VOR getTextBounds in den
  Mess-Paint-State (sonst falsche Bounds für „Big"=92px / Bold / Italic-Spans).
- **Effekt:** Komponente bekommt Bounds → `ComponentValue` (`LayoutMeasure.kt:152–166`) löst
  Breite/Höhe korrekt auf → Underline/Strike (`DrawLine`) landen an den richtigen Positionen.

### 4.2 AlignBy-Baseline im Row-Arrangement (Sub-Gap 2)
- `MODIFIER_ALIGN_BY(line=NaN)` = „richte diese Komponente an der gemeinsamen Text-Baseline der Row
  aus". Im Row-`assignPositions` (`LayoutMeasure.kt:328–366`): pro Span die Baseline berechnen
  (Top + Ascent aus den Text-Metriken), die Row-Baseline = max der Span-Baselines, jeden Span
  vertikal so verschieben, dass seine Baseline auf der Row-Baseline liegt.
- **Höchstes Positions-Risiko** (PO/dev-2 korrekt geflaggt) → harter Positions-Oracle (s. §6).
- Scope: nur `line=NaN` (Baseline-Sentinel, der einzige Wert im Korpus, decode-bestätigt). Andere
  AlignBy-line-Werte (explizite Linie) sind 0 Docs → loud-guard / deferred.

### 4.3 DrawContent malt die umschließende TextLayout (Sub-Gap 3 — der Paint-Mechanismus)
- `DrawContent : PaintOperation` (render-only; write/read unverändert — §5).
- Der **Measure-Pass** kennt (über den Tree) die umschließende TextLayout jeder DrawContent und
  setzt die aufgelöste Text-Draw-Info auf die DrawContent-Instanz — render-only-Felder analog
  `CoreText.setTextDraw(x, baseline)` / `setTextBox(...)`. (Das ist die „setComponent"-Auflösung,
  aber zur Measure-Zeit auf vorhandenem Tree, nicht als Laufzeit-Tree.)
- `DrawContent.paint(context, paint)`: malt den TextLayout-Text über **denselben Pfad wie CoreText**
  (`drawTextRun` einzeilig / `layoutComplexText`+`drawComplexText` bei wrap/underline/strike),
  mit dem Style der TextLayout (color/fontSize/fontStyle/fontWeight via `applyTextStyle`). Wenn keine
  Draw-Info gesetzt (unmeasured / nicht-Text-Komponente): no-op (fail-soft, kein Crash).
- **Scope-Lock (dispatch≠visual, ehrlich):** REM-134 implementiert DrawContent-Delegation für den
  **TextLayout-Content-Fall** (der einzige Korpus-Bedarf). DrawContent für andere
  Komponenten-Content-Typen (generische `component.drawContent`) ist **deferred** (0 Docs) →
  loud-guard/no-op, dokumentiert. Kein Anspruch auf generische Component-Content-Rekursion.

### 4.4 Decoration-Position (Underline/Strike) via transienten Matrix-Bracket — AMENDMENT 2026-06-29
> Reconciliation-Ruling (assist, nachdem dev-2 (a) korrekt re-scopt hat). Behebt eine zu-breite
> Formulierung in §4.3 + die §4.3↔§6-Spannung um die Decoration-Position.

- **Intent-Klarstellung zu §4.3:** „kein per-Component-Translate" war zu wörtlich formuliert. Der
  tatsächliche Intent von §1/§4.3 war, den **persistenten Component-Tree + `setComponent`-im-Player-
  Walk + stateful flat-walk** (Full-Option-A) zu vermeiden. Ein **transienter, stack-scoped
  Matrix-Bracket** ist davon NICHT erfasst — er ist bereits präzedenzierte Infrastruktur (REM-108
  Scroll-Bracket: `matrixSave`/`translate`/`restore`, in `walkGated` geöffnet und am matchenden
  `CONTAINER_END` geschlossen). §4.3-Intent bleibt mit so einem Bracket intakt.
- **Mechanismus (gewählt, statt Decoration-Follow-up):** der Span-Content wird in einen transienten
  `matrixSave → translate(span.x, span.y) → [Span-Content: Text + Decoration-DrawLines] → matrixRestore`
  gebrackt — ein span-LOKALER Koordinatenraum, der GENAU der `.rc`-Encodierung entspricht (span-lokale
  Line-Coords ab 0). Damit rendern Text UND Underline/Strike kohärent korrekt. DrawContent malt dann
  auf LOKALEN Coords (statt absolut) — visuell äquivalent, Baseline-Math unverändert (cy+maxAscent,
  nur Referenzrahmen verschoben).
- **§6-Decoration-Bar bleibt in REM-134** (NICHT descopt): das volle Daten-Orakel (≥24 Text-Draws +
  2 Decoration-DrawLines an Soll-Span-Bounds) ist der Korrektheits-Gate; EIN Golden-Re-Baseline nach
  dem Vollfix (kein deferred-partial-poisoned-Golden).
- **Bracket-Scope-Lock (Review-Gate):** Bracket öffnet/schließt sauber am Span-`CONTAINER_END`, KEIN
  Leak in Geschwister-Spans (REM-129-Klasse, matrix UND clip scoped — wie REM-108). Kein Regress auf
  andere Bracket-/CanvasOps-tragende Docs (test-3-Voll-Sweep).

---

## 5. §2 / Byte-Invariante (HARD GATE)
- **`write` / `read` / `companion read` / `equals` / `hashCode` von `DrawContent`, `TextLayout` und
  `AlignByModifier` UNVERÄNDERT** → 173-Byte-Conformance by-construction intakt (Muster REM-132/127:
  render-only Felder + paint/measure-Verdrahtung, Wire unangetastet). Keine Binärformat-Änderung.
- Render-only-Felder (Draw-Info, Bounds) sind nicht serialisiert (Muster `CoreText`/`BorderModifier.setBounds`).
- **Determinismus:** rein measure/paint-abgeleitet (gemessene Bounds + statischer Text), kein
  Live-Input → statischer Render deterministisch.

---

## 6. 🔴 Golden-Poisoning + Test-/Gate-Strategie (REM-123-Klasse — kritisch)

**Die bestehenden `attribute_string`-Goldens sind VERGIFTET** (blank-Text eingebacken; desktop-PNG
500×500 aber 1702 B / visuell weiß; alle 3 Targets). Konsequenz (PROJECT_CONTEXT §6 / REM-123):

- **Kein Cross-Target-Self-Compare / Parity-Sweep als Korrektheits-Gate** — der Defekt sitzt in
  geteiltem `commonMain` (Measure/Paint) → alle Targets rendern identisch-falsch → Parity GRÜN
  gegen blank. Das ist exakt die poisoned-golden-Falle.
- **Korrektheits-Gate = unabhängiges Daten-Orakel** (headless, aufgezeichnete Draw-Primitiven):
  - **≥24 Text-Draws** (`drawTextRun`/`drawComplexText`) statt aktuell 0 — einer pro Span.
  - Jeder an der **erwarteten Layout-Position** (x + Baseline-y), inkl. korrekter
    Baseline-Ausrichtung in der Row (AlignBy). dev-2 liefert die Soll-Span-Texte + Soll-Positionen
    als **Source-B** (Muster REM-127), test-3 disambiguiert/verifiziert.
  - Underline/Strike: die `DrawLine`s an den korrekten, jetzt-gemessenen Komponenten-Bounds.
- **Re-Baseline der Goldens ERST nach Fix + NUR via diesem Daten-Orakel** (REM-123-Gate am
  Golden-Promote). Bis dahin `attribute_string`-Golden als **known-poisoned** markieren (nicht als
  Pass werten). **Re-Baseline-Owner: test-3.**
- **§2:** Conformance-173 grün. **4-Target-Compile** grün (kein java.* in commonMain).

---

## 7. Slicing (PO koordiniert; Measure-Arbeit zuerst, Paint-Gate zuletzt)
- **S1 — LayoutMeasure: TextLayout-Text-Node + Sizing (§4.1).** Verifikation: TextLayout-Komponenten
  bekommen Bounds; `ComponentValue` löst Breite/Höhe auf → Underline/Strike-`DrawLine`-Positionen
  korrekt (messbar ohne den Text selbst). Kein Paint-Gate.
- **S2 — AlignBy-Baseline im Row-Arrangement (§4.2).** Verifikation: Span-Baseline-Positionen ==
  Soll (Source-B-Positions-Oracle, der hohe-Risiko-Teil). Kann mit S1 zu einer Measure-Slice
  gebündelt werden, wenn dev-2 das vorzieht.
- **S3 — DrawContent : PaintOperation + Measure-Verdrahtung (§4.3) + Daten-Orakel-Gate (§6).**
  Das ist das Korrektheits-Merge-Gate: ≥24 Text-Draws an Soll-Positionen.

---

## 8. Antworten auf die Draft-Offenen-Fragen
1. **A vs B → Option C** (Hybrid: Measure-verdrahtete DrawContent-Delegation auf vorhandenem
   LayoutMeasure-Tree). Pure-B ist z-order-falsch (decode-bewiesen, §1); Full-A ist redundanter
   zweiter Tree.
2. **Wie malt LayoutText upstream?** Upstream übersetzt LAYOUT_TEXT generativ nach CoreText/draw-
   Delegation; in unserem Port hält CoreText bereits den Text-Paint. REM-134 malt den TextLayout-
   Content über DIESELBEN CoreText-Paint-Primitiven (`drawTextRun`/`drawComplexText`), getriggert von
   DrawContent. Kein eigenes Text-Layout neu erfinden.
3. **Positions-/Baseline-Quelle?** `LayoutMeasure` — erweitert um TextLayout-Sizing (§4.1, via
   `getTextBounds`, Muster CoreText `setTextDraw`/`setTextBox`) + AlignBy-Baseline (§4.2).
4. **Re-Baseline-Owner + Oracle-Mechanik?** test-3; unabhängiges Daten-Orakel (≥24 Text-Draws an
   Soll-Positionen, Source-B von dev-2), REM-123-Gate. NICHT Cross-Target-Self-Compare.

## 9. Locks (im Review durchgesetzt)
- §2-guard: write/read/equals/hashCode der 3 Ops unangetastet; render-only-Felder nicht serialisiert.
- z-order: Text malt an der **DrawContent**-Position, nie an der TextLayout-Deklaration.
- Paint renutzt den CoreText-Primitiven-Pfad (kein paralleler Text-Renderer).
- AlignBy-Scope = nur `line=NaN` (Baseline); andere Werte loud-guard/deferred.
- DrawContent-Scope = TextLayout-Content; generische Component-Content-Delegation deferred (0 Docs),
  fail-soft no-op.
- Golden-Re-Baseline NUR via Daten-Orakel (REM-123), kein blank-Self-Compare; bis Fix known-poisoned.
- 4-Target-Compile + Conformance-173 grün.

## 10. assist-Verdikt
**GO auf Option C.** Decode-grounded (z-order-Bug von Pure-B bewiesen; Drei-Sub-Gap-Struktur
bestätigt), renutzt vorhandene LayoutMeasure-Tree- + CoreText-Paint-Infrastruktur statt einen
zweiten Tree zu bauen, §2 render-only by-construction, Golden-Poisoning korrekt über REM-123-Daten-
Orakel adressiert. Höchstes Restrisiko = AlignBy-Baseline-Positionierung (§4.2) → harter
Source-B-Positions-Oracle ist Pflicht, nicht optional.
