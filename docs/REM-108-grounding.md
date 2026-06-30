# REM-108 — Interaction-API: Mechanism Grounding & Design-Input (Dev-1)

> Source-grounded against `./androidx` (read-only ref) + in-repo inventory of `KmpRemoteCompose`.
> Lane: REM-108 (Epic REM-154), design-first, base develop `3c2ff54`. **No impl** — input for assist's TechSpec.

## 0. Headline: REM-108 is NOT greenfield — a mature reader/eval seam is already merged

The §2 byte-layer + eval seam + player dispatch already exist on develop. Design-first should target the
**remaining delta to a documented public API**, not re-spec the seam. Credit the prior slices (S1/S2/S2b/S3b, REM-145 S1).

**Already merged (in-repo, file:line):**
- Reserved touch vars — `RemoteContext.kt:445-452`: `ID_TOUCH_POS_X/Y=13/14`, `ID_TOUCH_VEL_X/Y=15/16`, `ID_TOUCH_EVENT_TIME=29` (match upstream).
- Byte ops decoded + registered (`LayoutOps.kt:91-99`): `TouchExpression`(157), `TouchDown/Up/CancelModifier`(219/220/225), `ScrollModifier`.
- TouchExpression eval (S2 Phase-A) — `TouchExpression.kt`: `touchDown/touchDrag/touchUp/touchCancel(ctx)` + `apply`, persists `currentValue` across frames; clamp/wrap/snap.
- Player dispatch (S2b) — `RemoteComposePlayer.kt:474-502`: public `touchDown/touchDrag/touchUp/touchCancel(doc,ctx,x,y)` + private `dispatchTouch` + `TouchState`/`TouchPhase` state machine.
- Creation writer (REM-145 S1) — `LayoutContainerHelpers.kt:408-443`: emits `MODIFIER_TOUCH_DOWN/UP/CANCEL` as `ListActionsOperation` scope + `CONTAINER_END`.
- Slices proven: `Rem108TouchSeamTest` (S1: vars, `touchIdsUsed`, stop-mode reach-guard 0–6 / mode-7 absent, render-invariance), `Rem108TouchEvalTest` (S2: drag→value, clamp/wrap, notch-snap), `Rem108S3bScrollLayoutTest` (S3b scroll layout).

## 1. The mechanism — two interaction families (both upstream-grounded)

### A. Click-events = component-scoped action modifiers
- `TouchDown/Up/CancelModifierOperation extends ListActionsOperation implements TouchHandler` (upstream `remote-core/.../layout/`).
- On hit: `applyActions()` → for each `ActionOperation`: `runAction(ctx, doc, component, x, y)` (`ListActionsOperation.java:113-145`).
- Action vocabulary (`remote-creation-compose/.../action/`): `ValueChange` (Float/Int/String + `*ExpressionChange`), `Scroll` (`scrollBy`/`scrollTo` → `ValueFloatExpressionChange` on a position state), `HostAction` / `PendingIntentAction` (**Android-bound**), `CombinedAction`, `RunAction`.
- Creation surface: `RemoteModifier.onTouchDown(action)` → `TouchActionModifier(DOWN/UP/CANCEL)` → writer (`TouchActionModifier.java`, `TouchDownActionModifier.kt`).
- Dispatch (upstream `CoreDocument.touchDown/Up/Drag/Cancel`): walks `mRootLayoutComponent` recursively; `mAppliedTouchOperations` tracks hit components so a later up/cancel reaches the same component.

### B. Live-scroll / gestures = TouchExpression (`addTouch`)
- `TouchExpression` (op 157): a global `TouchListener` (`context.addTouchListener`); an RPN expr maps `ID_TOUCH_POS_X/Y` → a remote variable. Powers sliders, knobs, joysticks, live-scroll.
- Stop modes: `GENTLY`(inertia) / `INSTANTLY` / `ENDS` / `NOTCHES_EVEN` / `NOTCHES_PERCENTS` / `NOTCHES_ABSOLUTE` / `ABSOLUTE_POS` / `SINGLE_EVEN`. Plus wrap-mode, `VelocityEasing` (maxTime/accel/vel), haptics (`touchEffects` → `hapticEffect`).
- Scoped to the enclosing component's bounding box.
- Corpus: `touch1`(POS_X slider) · `touch2`(POS_Y) · `touch_wrap`(rotary knob, wrap + NOTCHES_EVEN) · `c_modifier_on_touch_down/up/cancel`(HostAction click).

## 2. The real gap (what design-first must resolve)

1. **Click-event action EXECUTION is not wired.** Our `TouchDownModifier : Operation` (`TouchDownModifier.kt`) is a **bare opcode-only byte op** — no `TouchHandler`, no `applyActions`. The action ops (`ValueFloatChange`, `ValueIntegerChange`, `ValueStringChange`, `ValueFloatExpressionChangeAction`, `HostAction`, `RunAction`) are byte-decoded but **have no `runAction` execution path**. → a click currently does nothing live. **Biggest gap.**
   - **TechSpec decision:** make the modifier a `ListActionsOperation`-style container that owns + runs its nested actions (upstream model), or keep ops flat + add a separate dispatch walk that collects the actions between `MODIFIER_TOUCH_*` and `CONTAINER_END`. Either way the §2 byte shape must stay identical.
2. **Velocity glide (inertia) deferred** — `TouchExpression.kt:112` snaps immediately; the post-release glide animation (`VelocityEasing`) is unimplemented.
3. **Live pointer/mouse UI hookup deferred** — `player.touchDown(...)` is the low-level entry; the per-target Compose `pointerInput`/mouse wiring that feeds real events into it is absent. **This is where §0 capability-staffing lands.**
4. **Public API surface** — current entry is the low-level `player.touchDown(doc,ctx,x,y)`. A "documented public API for external teams" needs a clean Compose-modifier-level surface + docs.

## 3. §2 byte-invariance watch-points (non-negotiable)

- `TouchExpression.write/read`: `(touchMode << 16) | len` packing; NaN-id encoding of min/max/defValue (`readNanId`; `min`=NaN with id==0 ⇒ wrap-mode); expr / stopSpec / easingSpec as NaN-encoded float arrays.
- Touch modifiers: opcode + nested action ops + trailing `CONTAINER_END`. Any container/flat refactor must preserve the exact emitted byte sequence (REM-145 S1 already pins the writer shape).
- Conformance must stay green; no binary-format change without a conformance test (§2/§6).

## 4. Touch-coordinate version flag — pin one

`LayoutManager.FIX_TOUCH_EVENT=1` (= `DEFAULT_TOUCH_VERSION`), read via `RemoteContext.getTouchVersion()`. Two code paths exist
(component-local `0..w/h` vs window-absolute cumulative-parent coords) in both `TouchExpression.touchUp/updateBounds` and `ListActionsOperation.applyActions`. **The TechSpec must nail down which version we target** (recommend FIX_TOUCH_EVENT = the upstream default = component-local).

## 5. Capability-staffing map (§0 — Mobile full / Desktop / Web / Floor=static)

| Capability | Mobile | Desktop | Web (wasmJs) | Floor |
|---|---|---|---|---|
| Touch pos/drag (POS_X/Y) | pointer | mouse drag | pointer events | — (static frame) |
| Click-event actions | ✓ | ✓ (mouse click) | ✓ | — |
| Velocity / inertia glide | ✓ | ✓ | ✓ | — |
| Haptics (`touchEffects`) | ✓ | no-op | no-op | — |
| `HostAction` / `PendingIntent` | Android | host-callback or no-op | host-callback or no-op | — |

Floor = static render frame, unchanged by touch (already guaranteed by S1 render-invariance).

## 6. Byte-level tradeoff — container-vs-flat click-action dispatch (the TechSpec's pivot)

The wire sequence for a click modifier is **identical in both models**:
`[MODIFIER_TOUCH_DOWN opcode][action-op bytes…][CONTAINER_END opcode]`. The choice is purely the **in-memory
op-tree shape + who owns the runtime walk**, not the bytes — so §2 byte-invariance can be satisfied either way.
The deciding factor is *which path we reopen*.

### Option A — Container (upstream-faithful): modifier owns + runs its actions
- `TouchDownModifier` becomes a `ListActionsOperation`-style container: on read it **consumes ops until its
  matching `CONTAINER_END`** into its own child list; on write it emits its opcode, replays each child, then
  emits `CONTAINER_END` **itself** (CONTAINER_END is *not* a standalone op in the flat list).
- **Pro:** faithful to upstream object model; `runAction` is local to the modifier; clean execution semantics.
- **Byte risk:** reopens the **already-conformance-green decode/encode path**. Two failure modes to prove against:
  (1) double-end (writer emits CONTAINER_END *and* a stray standalone one), (2) the reader's nesting must stop at
  the correct depth (nested containers inside the action list). → **requires a fresh byte round-trip + conformance
  test as its gate** (§2/§6). Touches a green area.

### Option B — Flat + dispatch-walk (minimal-§2-risk): keep decode flat, group at runtime
- Leave the current flat decode untouched (`MODIFIER_TOUCH_DOWN`, the action ops, and `CONTAINER_END` stay flat
  sibling `Operation`s — **already byte-green, zero change**). Add a dispatch component that, on a touch-hit, walks
  the flat op list from the modifier to its matching `CONTAINER_END` (depth-counted) and runs the `ActionOperation`s
  in between.
- **Pro:** the proven §2 byte layer is **not touched** → byte risk ≈ 0; no new round-trip gate needed for encoding.
  **Well-precedented in our codebase** — the particle decoder already groups a container body exactly this way
  (`ParticleSystemDecoder.bodyOps` walks `loopIndex`→matching `CONTAINER_END` via `RemoteComposePlayer.opensContainer`).
- **Con:** the runtime re-derives the modifier→actions grouping that the bytes already imply (a scan); slightly less
  faithful to upstream's object model.

**Dev-1 recommendation:** **Option B (flat + dispatch-walk)** — §2 is the top invariant and Option B leaves the
conformance-green byte path entirely alone while reusing an existing container-walk idiom. Choose Option A only if
assist wants strict upstream object-fidelity, and then gate it on a dedicated byte round-trip + conformance proof.
Either way: **no binary-format change ships without a conformance test (§6)**, and the REM-145 S1 writer shape is the
byte reference both must reproduce.

## 7. Recommended decisions for the TechSpec
1. Click execution model: container-owns-actions vs flat+dispatch-walk — see §6 byte-level tradeoff. **Dev-1 recommends Option B (flat+walk)** to leave the conformance-green byte path untouched; Option A only with a dedicated byte round-trip gate.
2. Public surface: a capability-staffed Compose `Modifier` (e.g. `.rcInteractive()`) wrapping `pointerInput`/mouse per target, feeding `player.touchDown/...`.
3. Velocity-glide: in-scope now or a follow-up slice? (S2 deferred it.)
4. Touch-version: pin FIX_TOUCH_EVENT.
5. `HostAction`/`PendingIntent` cross-platform contract: host-callback interface (no Android `PendingIntent` in commonMain — §5).
