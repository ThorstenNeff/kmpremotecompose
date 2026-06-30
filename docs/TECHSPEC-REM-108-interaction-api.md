# TechSpec — REM-108 Interaktions-API (öffentlich, Fremd-Team-Konsum)

> **Autor:** PO-Assistent (Reviewer) · **Status:** **Draft v1** (grounded gegen `develop=3c2ff54`;
> dev-1 `./androidx`-Grounding `docs/REM-108-grounding.md` pending → vor Final-GO eingearbeitet).
> **Epic:** REM-154 (externe App-Teams: Taxi=Mobile, Agent-Tool=Desktop/Wasm). **Branch-Base:**
> `origin/develop=3c2ff54`. **Bindend:** `PROJECT_CONTEXT.md` (§0 Capability-Staffing, §2 Byte-Invariante).
> **Lieferstandard:** *dokumentierte öffentliche API* (Fremd-Teams konsumieren) **+** conformance-gateable
> (test-1: „nicht beobachtbar = nicht im Lieferstandard"). Verifikation = §6 Maestro/Echo, **nicht** byte.

---

## 0. Kernaussage (TL;DR)

REM-108 ist **NICHT greenfield**. Ein reifer Reader-/Eval-/Dispatch-Seam liegt schon auf `develop`
(S1/S2/S2b/S3b + REM-145 S1). Diese TechSpec spezifiziert **nur das Rest-Delta zur dokumentierten,
capability-gestaffelten Public-API** — sie baut den Seam **nicht** neu. Drei reale Lücken:

1. **Kein öffentlicher Callback-/Listener-Vertrag.** Der Player rendert + verarbeitet Input intern, aber
   reicht **nichts an die konsumierende App** zurück (Click-Element-id, Scroll-Offset).
2. **Click-Action-Exekution ist NICHT verdrahtet** (der größte Gap, von dev-1 bestätigt): die Action-/
   Click-Modifier sind dekodierte Byte-Ops **ohne `runAction`-Pfad** → ein Klick tut zur Laufzeit nichts.
3. **Zwei Observability-Hooks fehlen** (test-1): `rc-action-echo`, `rc-scroll-offset` — ohne sie ist die
   Fähigkeit nicht conformance-gateable.

**Slicing:** ein **NoOp/zero-risk-Foundation-Slice zuerst** (reine additive Callback-Surface, default no-op
= §0-Floor, ändert kein Verhalten, §2-irrelevant), dann Scroll-Observability, dann der Click-Exekutions-Gap.

---

## 1. Ist-Stand auf `develop=3c2ff54` (was NICHT neu gebaut wird — grounded Anker)

**Input-Akquise (capability-unifiziert, schon da):**
- `RemoteComposeApp(loadRc, modifier)` (`shared/.../RemoteComposeApp.kt`) hängt CMP
  `detectDragGestures` auf das `rc-canvas` (`RemoteComposeApp.kt:208-213`) → `TouchState.down/move/up/cancel`.
  CMP unifiziert **Touch/Maus/Pointer** über alle Targets in **einem** Detektor — der Capability-Floor ist
  „kein Pointer / `live==false` → `touchState=null` → statischer deterministischer Frame".
- `TouchState` (`player/core/TouchState.kt`): persistenter Gesten-Holder (`x/y`, `phase`
  IDLE/DOWN/DRAG/UP/CANCEL, `touchEventTime`=id29, `hasTouched`), überlebt den per-Frame-frischen `RemoteContext`.

**Touch→Doc-Dispatch (schon da):**
- `RemoteComposePlayer.paint(..., touchState, sensorSource, hapticActuator)` (`player/core/RemoteComposePlayer.kt:62-72`)
  — die host-seitige Render-Entry. `dispatchTouch` (`:502-516`) konsumiert **eine** Transition/Frame →
  `touchDown/Drag/Up/Cancel` (`:474-499`) → lädt `ID_TOUCH_POS_X/Y` (13/14) + benachrichtigt jede
  `TouchExpression`. **LIVE-only** (statisch ⇒ kein Dispatch ⇒ Determinismus).
- Touch-Float-ids reserviert (`RemoteContext.kt:444-456`): POS_X/Y=13/14, VEL_X/Y=15/16, EVENT_TIME=29.
  Korpus liest nur 13/14; VEL/29 decode-present aber korpus-unexercised.

**Live-Scroll (end-to-end verdrahtet — reifer als „nur Offset-Schicht offen"):**
- `ScrollModifier.scrollOffset(context)` (`operations/layout/ScrollModifier.kt:65-73`): liest den gepaarten
  TouchExpression-Output aus dem Float-Store, **klemmt** `min(maxScroll, scroll)` → `-offset`, Achse per
  `direction` (VERTICAL=0/HORIZONTAL=1). Defensiv-Clamp wenn Layout noch nicht lief.
- `applyScrollBounds(context, maxScroll, contentDimension)` (`:86-89`) publiziert die Bounds; Sequencing
  dokumentiert (layout-measure → applyScrollBounds → eval(TE) → scrollOffset → apply).
- `LayoutMeasure.measure` liefert `scrollBrackets`; `RemoteComposePlayer.openScrollBracket` (`:249-265`)
  macht `matrixSave` + `clipRect(viewport)` + `translate(offset)` + Restore am `CONTAINER_END`. → Der Pfad
  **Drag→TouchExpression→Store→scrollOffset→Clip+Translate** ist **bereits geschlossen**.

**Observability-Hooks (teilweise da):**
- `rc-rendered` (gated `committed && drawCount>0`), `rc-error`, `rc-doc`, `rc-draw-count`, `rc-frame-count`
  (monoton, live), `rc-touch-echo` (letzte Pointer-Pos „x,y") — `RemoteComposeApp.kt:305-322`. Auf wasmJs in
  DOM gespiegelt (`RenderMarkerMirror.wasmJs.kt`). Der `drawCount`-Honest-Render-Gate (`RemoteContext.kt:396-407`).
- **FEHLT:** `rc-action-echo`, `rc-scroll-offset`.

**Click-Bausteine (Byte-Ops da, Laufzeit NICHT verdrahtet — der Gap):**
- `ClickModifier` (MODIFIER_CLICK) — „marks component clickable", wire-only; `LayoutContainerHelpers.kt:301`
  sagt explizit **„action wiring is out of scope"**.
- `ClickArea(id, left, top, right, bottom, metadata)` (`operations/layout/ClickArea.kt`) — deklarative,
  **identifizierte hittbare Region** auf dem Wire (opcode CLICK_AREA=64). Keine PaintOperation.
- `TouchDownModifier`/`TouchUpModifier`/`TouchCancelModifier` (219/220/225, REM-145 S1) — bare Byte-Ops,
  **kein TouchHandler/applyActions**. `ValueIntegerChangeAction(valueId, value)` (212) — Action-Payload,
  **nicht gefeuert**. `MODIFIER_CLICK`/`MODIFIER_MULTI_CLICK`/`RUN_ACTION` sind container-öffnend
  (`RemoteComposePlayer.CONTAINER_OPENING_OPCODES`) → die Action-Ops liegen **genested im Modifier-Container**.
- **Es gibt KEINE** Hit-Test-Registry, **keinen** Tap-Detektor, **keinen** `runAction`-Pfad, **keinen**
  App-Callback. Das ist das Delta dieser Spec.

---

## 2. Zwei Mechanismus-Familien (dev-1-Grounding)

Der Contract muss **beide** upstream-Familien capability-gestaffelt abbilden:

| Familie | Upstream-Mechanismus | Scoping | Aktionen | Capability-Punkt |
|---|---|---|---|---|
| **(A) Click / Action** | component-scoped **Action-Modifier** (ListActions + TouchHandler), on-hit `runAction` | Component-BBox (ClickArea/clickable) | `ValueChange` · `Scroll` · `Combined` · `RunAction` · **`HostAction`** | **HostAction/PendingIntent ist Android-gebunden** → Floor: ValueChange/Scroll laufen überall in-doc; HostAction nur Mobile, sonst via `onClick`-Callback an die App. |
| **(B) Live-Scroll / Gesten** | **`TouchExpression`/`addTouch`** (globaler Listener, RPN POS→Var, StopModes/Wrap/Velocity/Haptik) | Component-BBox-scoped | kontinuierlicher Offset/Wert | Geste via CMP unifiziert (Touch/Maus/Pointer); Floor = statischer Frame. |

Familie (B) ist **fertig** (§1). Familie (A) ist der **Exekutions-Gap** (§1 letzter Block).

---

## 3. Capability-Staffing-Modell (§0)

**Prinzip: ein cross-target-unifizierter CMP-Gesten-Layer, der zum statischen Frame degradiert — never-hard-fail.**

| Target | Click (Tap) | Live-Scroll/Geste | HostAction | Floor-Verhalten |
|---|---|---|---|---|
| **Mobile** (Taxi) | CMP `detectTapGestures` (Touch) | `detectDragGestures` (da) | nativ (PendingIntent) | — |
| **Desktop** (Agent-Tool) | `detectTapGestures` (Maus) | da | **kein** PendingIntent → `onClick`-Callback an App | — |
| **Web/Wasm** (Agent-Tool) | `detectTapGestures` (Pointer) | da | **kein** PendingIntent → `onClick`-Callback an App | DOM-Mirror der Hooks |
| **Floor** (kein Pointer / `live==false`) | — | — | — | statischer deterministischer Frame, **kein** Echo, Gate **degradiert sauber statt hart zu failen** |

In-doc-Aktionen (ValueChange/Scroll, Familie B) sind **capability-unabhängig** (reine Float-Store-Mutation
→ reaktiver Re-Render). Nur **HostAction** (App-Intent) ist gestaffelt: Mobile nativ, sonst Callback.

---

## 4. Öffentliche API (dokumentiert — der Lieferstandard)

**Designprinzip:** Eine **optionale, default-NoOp** `InteractionCallbacks`-Senke, additiv an die bestehende
Entry (`RemoteComposeApp` / `RemoteComposePlayer.paint`) gehängt. Default = Floor (kein Callback) ⇒
bestehendes Verhalten **byte-/render-identisch**, alle Tests grün by construction.

```kotlin
// commonMain — neue, rein additive Public-Surface (Signaturen indikativ, final nach dev-1-Doc)

/** Ein Klick auf ein identifiziertes, gerendertes Element. */
data class RcClickEvent(
    val elementId: Int,        // ClickArea.id; sonst Component-id (§9 offen); -1 = unbekannt
    val metadata: Int,         // ClickArea.metadata (app-definiert)
    val docX: Float,           // Tap-Position, doc-space
    val docY: Float,
)

/** Beobachteter Scroll-Zustand eines scrollbaren Components nach einem Drag-Frame. */
data class RcScrollEvent(
    val componentId: Int,
    val offset: Float,         // = ScrollModifier.scrollOffset (geklammert, vorzeichenbehaftet)
    val axis: Int,             // ScrollModifier.VERTICAL=0 / HORIZONTAL=1
)

/** Capability-gestaffelte Senke. Alle Member optional (default no-op = Floor). Fremd-Team-Surface. */
interface RcInteractionCallbacks {
    /** Ein in-doc-Klick traf ein Element. Die in-doc-Action (ValueChange/Scroll) ist BEREITS ausgeführt;
     *  dieser Callback ist das App-Signal (insb. der Träger für HostAction auf Nicht-Mobile). */
    fun onClick(event: RcClickEvent) {}
    /** Der Scroll-Offset eines Components hat sich nach einem Drag geändert (Beobachtung, keine Steuerung). */
    fun onScroll(event: RcScrollEvent) {}
    companion object { val NoOp = object : RcInteractionCallbacks {} }   // expliziter Floor
}
```

**Einhängung (additiv, default = heutiges Verhalten):**
```kotlin
@Composable
fun RemoteComposeApp(
    loadRc: (String) -> ByteArray,
    modifier: Modifier = Modifier,
    callbacks: RcInteractionCallbacks = RcInteractionCallbacks.NoOp,   // <-- neu, optional
)
// RemoteComposePlayer.paint(..., callbacks: RcInteractionCallbacks = NoOp)  // <-- neuer optionaler Param
```

**Click-Exekutions-Vertrag (der Gap, §7-S2):** ein Tap → Hit-Test gegen die Element-Registry → **(1) führe
die in-doc-gebundene Action aus** (`ValueIntegerChangeAction`/Scroll → Float-Store-Mutation = upstream-treu,
hält reaktiven in-doc-Text/State funktional) **UND (2)** emittiere `onClick` an die App. **Beides**, nicht
entweder/oder (Empfehlung §9-Q1): (1) ist upstream-Parität, (2) ist der Fremd-Team-Bedarf.

**Design-Tradeoff (dev-1, in den Contract aufgenommen — Impl-Entscheid mit Byte-Folge):**
- **(a) Modifier-als-self-running-Action-Container** (upstream-treu): der `MODIFIER_CLICK`-Container „runnt"
  seine genesteten Action-Ops selbst beim Hit. Näher an upstream, weniger Sonder-Walk.
- **(b) Flat + separater Dispatch-Walk**: Action-Ops flach, ein eigener Post-Hit-Walk feuert sie.
- **Beide in den Contract**; Empfehlung **(a)** (upstream-Treue → weniger Divergenz-Risiko an der
  Byte-nahen Action-Reihenfolge), final nach dev-1s Byte-Tradeoff-Analyse. **§2 unberührt in beiden** (kein
  Wire-Change; nur Laufzeit-Interpretation der schon dekodierten Container).

---

## 5. Observability / Conformance-Gate-Surface (test-1 — erstklassiger Teil des Contracts)

„Nicht beobachtbar = nicht im Lieferstandard" ist ein **explizites Akzeptanzkriterium pro Capability.**

- **`rc-action-echo`** — surfaced den nach Click→`VALUE_INTEGER_CHANGE_ACTION` mutierten `DATA_INT`(z.B. id42)
  als testTag/DOM-Marker. Macht das **Kausal-Gate** beobachtbar (Action feuerte ⇄ Int änderte sich), genau wie
  `rc-touch-echo` (REM-143-S3) Touch→Impulse sichtbar machte. Fallback fehlte bisher → der gerenderte Text
  bleibt das statische Label, also ist der Echo der **einzige** Beobachtungspfad.
- **`rc-scroll-offset`** — surfaced den `scrollOffset` nach Drag. **Zweiter, render-basierter Verify-Pfad:**
  test-1s bestehende S3b-Clip-Window-Visual-Methode (greift auch ohne Hook).

**Leitplanken (im Contract festgeschrieben):**
1. **Capability-gestaffelt** — Hooks sind **live-only Debug-/Test-Surface**; der Floor-Target (statischer
   Frame) liefert keinen Echo → das Gate **degradiert sauber statt hart zu failen**.
2. **§2 null-write/read** — Echo liest **nur** render-only-State (mutated-int / offset), schreibt **nie** in
   `.rc` `write()/read()/equals()/hashCode()` — dieselbe Disziplin wie die nachweislich
   `static-render unaffected` rc-touch-echo/rc-frame-count-Hooks.
3. Gleicher Honest-Gate (`committed && drawCount>0`) wie `rc-rendered` — kein Echo vor einem realen Paint.

---

## 6. §2-Invariante (null-write / null-read) — die oberste Regel

Diese Spec führt **keine neuen Wire-Ops ein und ändert kein `write()/read()/equals()/hashCode()`** einer
bestehenden Operation. Sämtlicher Interaktions-Zustand ist **render-only** (das etablierte Muster:
`TouchState`, `scrollOffset`, Touch-/Sensor-ids, `hapticActuator`, `drawCount` — alle bereits in-code als
„Runtime-only → §2-safe" / „173 byte-conformance intact" markiert). `ClickArea`/`ClickModifier`/
`ValueIntegerChangeAction` sind **bereits** serialisierte Doc-Ops; wir fügen nur **Laufzeit-Interpretation**
hinzu. **Mandat pro Slice:** ein `*Test` mit explizitem **null-write/null-read-Assert** (Op write/read/
equals/hashCode unverändert) + 173-Conformance grün — sonst No-Go (REM-123/§6-Disziplin).

---

## 7. Slice-Zerlegung (Foundation-first, NoOp/zero-risk zuerst — parallel dev-1/dev-2)

| Slice | Inhalt | Owner | Risiko / Abhängig |
|---|---|---|---|
| **S0 Foundation (NoOp, zero-risk, blockierend)** | Public `RcInteractionCallbacks` + `RcClickEvent`/`RcScrollEvent` (commonMain) **als optionale default-NoOp-Params** an `RemoteComposeApp`/`RemoteComposePlayer.paint` gehängt. **Kein Verhaltens-/Byte-/Render-Change** (Floor). Definiert den Public-Contract. + die zwei fehlenden Echo-testTags (`rc-action-echo`/`rc-scroll-offset`) **NoOp-bis-gefüttert** verdrahtet. | dev-1 | **zero-risk** (rein additiv); — |
| **S1 Scroll-Observability** | `onScroll` + `rc-scroll-offset` über den **schon berechneten** `scrollOffset` (read-only über `openScrollBracket`/`scrollOffset`). Fallback = S3b-Clip-Window-Visual. | dev-2 | low (liest bestehende Runtime); S0-Contract |
| **S2 Click-Exekution + Action-Echo (DER Gap)** | (a) **Hittable-Element-Registry**: `LayoutMeasure` publiziert id+abs-Bounds aus `ClickArea` + clickbaren Components (reuse der Measure-Bounds, BackgroundModifier-`setBounds`-Muster). (b) `detectTapGestures` auf `rc-canvas` → Hit-Test (topmost-wins, §9-Q3) → **`runAction`-Pfad**: in-doc-Action ausführen (ValueChange→Store) **+** `onClick` emittieren **+** `rc-action-echo`. Impl-Variante (a)/(b) aus §4. Capability-gestaffelt (Tap via CMP; HostAction Mobile-nativ sonst Callback). | dev-1 (+dev-2 für Bounds-Registry) | **Hauptarbeit**; S0; Bounds aus LayoutMeasure |
| **S3 Conformance-/Maestro-Gate** | Flows: nach Tap ändert sich `rc-action-echo`; nach Drag ändert sich `rc-scroll-offset`/Clip-Window. Pro Target (test-1=Android/iOS, test-2=Web, test-3=Desktop). + null-write/read-Assert + 173-Conformance grün (§6). | test-1/2/3, dev-enabled | S1+S2 |
| **D1 (deferred) Velocity/Fling** | Touch-VEL-ids (15/16) — reserved, korpus-unexercised; Fling-Physik. | dev-1 | nach S2 |
| **D2 (deferred) Multi-Click / RunAction-Vollvokabular** | `MODIFIER_MULTI_CLICK`, `Combined`, volle HostAction-Surface. | dev-1 | nach S2 |

**Parallelität:** S0 (NoOp-Contract) zuerst, dann dev-1→S2 (Click-Gap, Hauptarbeit) und dev-2→S1
(Scroll-Observability) + Bounds-Registry-Hälfte von S2 gegen den S0-Contract als Stub.

---

## 8. Verifikation (§6)

Pro Capability ein **beobachtbares** Gate (sonst nicht im Lieferstandard): Click→`rc-action-echo`-Delta,
Scroll→`rc-scroll-offset`/Clip-Window-Delta, je auf ≥1 Target via Maestro; Floor-Target rendert statisch
ohne Hard-Fail. **+** der §2-null-write/read-Assert + 173-Conformance je Slice. „Kompiliert" ≠ „funktioniert";
ein Click-Pfad gilt erst als fertig, wenn ein Flow das Echo-Delta auf einem Target zeigt.

---

## 9. Offene Punkte (PO/Mensch — Risk-/Scope-Entscheide)

- **Q1 (Click-Semantik):** in-doc-Action **UND** `onClick`-App-Callback? **Empfehlung: beide** (Parität +
  Fremd-Team-Bedarf). — *risikoarm, default annehmbar.*
- **Q2 (Element-Identität für `onClick`):** `ClickArea.id` wo vorhanden; bare `MODIFIER_CLICK` ohne id →
  Component-id aus dem Measure-Pass; sonst synthetisierter stabiler Index. **Empfehlung: ClickArea.id →
  Component-id → -1.** — *an PO/dev-1 bestätigen.*
- **Q3 (Hit-Test-Z-Order):** überlappende `ClickArea`s → **topmost/last-in-doc-order wins.** — *bestätigen.*
- **Q4 (Impl-Variante §4 a vs b):** Modifier-self-run (a, empfohlen) vs flat+dispatch-walk (b) — **Byte-naher
  Action-Order-Tradeoff**, final aus dev-1s `docs/REM-108-grounding.md`. — *Reviewer-Urteil nach Doc.*
- **Q5 (HostAction-Staffing):** Desktop/Web ohne PendingIntent → reicht der `onClick`-Callback als
  Ersatz-Vertrag, oder braucht es eine separate `RcHostActionRequest`-Surface? **Empfehlung: onClick trägt
  HostAction-Payload (action-uri/extras in metadata).** — *Risk-Posture (App-Intent) → an PO/Mensch.*

---

## 10. Pending — dev-1 `./androidx`-Grounding (vor Final-GO einzuarbeiten)

`docs/REM-108-grounding.md` (Branch `feature/REM-108-grounding`) verfeinert: präzise upstream
ListActions/TouchHandler-`runAction`-Semantik, das volle Action-Vokabular (ValueChange/Scroll/Combined/
RunAction/HostAction), und die **Byte-Tradeoff-Analyse** für §4-(a) vs (b). Diese v1 ist gegen den
develop-Code grounded; §2/§7-Slicing/Capability-Modell stehen, §4-Impl-Variante + §9-Q4 final nach der Doc.
