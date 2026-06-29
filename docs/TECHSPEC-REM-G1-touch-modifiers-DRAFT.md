# REM-G1 — Touch Event Modifiers (Creation-DSL Surface) — Scoping Draft

> **Status:** DRAFT (Dev-3, 2026-06-29). Mensch-GO routing → PO-review → impl-slices (msg
> 1521207612534947911). Read-only investigation done; this doc scopes the impl.
> **Branch:** `feature/REM-G1-touch-modifiers-scoping-draft` from develop `aea653c`.
> **Parent:** Census `ff27525` (Next-Lane #1 — G1 Touch event modifiers).

---

## §0 — What this lane exists to close

Census-`ff27525` ranked G1 Touch event modifiers as the next Creation-DSL lane (Next-Lane #1):
3 corpus-active fixtures (`c_modifier_on_touch_{down,up,cancel}.rc`) exist on the wire, but the
procedural-DSL has **no creation surface for any of them**. REM-108 wired player-side touch
evaluation; the creation-DSL bottleneck is what blocks emitting these documents from a server /
DSL-only context.

**This lane closes:**
1. **`MODIFIER_TOUCH_DOWN/UP/CANCEL`** — 3 1-byte modifier ops that open a `ListActionsOperation`
   scope holding action ops; mirror the existing `MODIFIER_SCROLL` group-emission pattern.
2. **`VALUE_INTEGER_CHANGE_ACTION`** — the action body all 3 corpus fixtures use inside the
   touch-event scope. Closes G7-action #1 as a side effect (the action is what makes the
   touch-event meaningful).
3. **Standalone `TOUCH_EXPRESSION`** — the RPN-driven float helper already emitted internally by
   `scroll()`; this lane exposes it as a public helper (mirror `floatExpression`) for touch-axis-
   driven float bindings.

**Out of scope (deferred):**
- `MODIFIER_OFFSET / GRAPHICS_LAYER / DIMENSION_CONSTRAINTS / MARQUEE / RIPPLE / MULTI_CLICK`
  (G1 touch-tail, Census #5 — pair after this lane lands).
- Other actions inside touch-event scope (HOST_ACTION, RUN_ACTION, etc.) — wire them as needed
  in follow-up lanes per corpus reach.
- E6-Compose-Creation-DSL touch-event surface (parallel layer; mirrors this procedural surface
  once it lands — same Q4-element-list pattern as REM-130/144).

---

## §1 — Empirical decode (Bug-#2 lesson, transient probe deleted)

### §1.1 — `c_modifier_on_touch_down.rc` (225 B, PROFILE_ANDROIDX|EXPERIMENTAL 0x201, 500×500, contentDescription="DemoModifierOnTouchDown")

```
[0] HEADER w=500 h=500 profiles=0x201 (=PROFILE_ANDROIDX|PROFILE_EXPERIMENTAL)
[1] LAYOUT_ROOT id=-2
[2] DATA_INT id=42 value=0                                  ← counter init (region-0 first id)
[3] LAYOUT_BOX id=-3 (POS_START=1, POS_TOP=4)
[4..6] MODIFIER_WIDTH(EXACT,200), MODIFIER_HEIGHT(EXACT,200), MODIFIER_BACKGROUND(BLUE)
[7] MODIFIER_TOUCH_DOWN                                     ← 1-byte op (opcode 0xdb)
    hex: db
[8] VALUE_INTEGER_CHANGE_ACTION valueId=42 value=2          ← action body inside ListActions scope
[9] CONTAINER_END                                           ← closes the TOUCH_DOWN ListActions
[10] LAYOUT_CONTENT id=-4
[11..12] DATA_TEXT(id=43, "Press Down"), CORE_TEXT(textId=43, params=3)
[13] LAYOUT_CONTENT id=-6
[14..18] 5× CONTAINER_END
```

### §1.2 — `c_modifier_on_touch_up.rc` (223 B, same shape)
- Opcode `0xdc` (TOUCH_UP), action `VALUE_INTEGER_CHANGE_ACTION valueId=42 value=3`, text "Release Up", green background.

### §1.3 — `c_modifier_on_touch_cancel.rc` (229 B, same shape)
- Opcode `0xe1` (TOUCH_CANCEL), action `VALUE_INTEGER_CHANGE_ACTION valueId=42 value=4`, text "Touch Cancel", dark-grey background.

**Pattern (all 3 fixtures, mirrors `MODIFIER_SCROLL` group):**
```
MODIFIER_TOUCH_{DOWN/UP/CANCEL}        ← 1 byte (opens ListActions)
  <action ops>                          ← e.g. VALUE_INTEGER_CHANGE_ACTION (9 bytes: 1 + 4 + 4)
  ...
CONTAINER_END                           ← 1 byte (closes scope)
```

This is structurally identical to `MODIFIER_SCROLL`'s emission (`scroll()` already emits
`ScrollModifier + TouchExpression + ContainerEnd`). The pattern + REM-96's `ListActionsOperation`
+ trailing-`ContainerEnd` discipline carry over verbatim.

### §1.4 — Op-class state (verified)
- `TouchDownModifier`, `TouchUpModifier`, `TouchCancelModifier` — all exist in
  `shared/.../core/operations/layout/` as zero-field classes (just opcode).
- `ValueIntegerChangeAction(valueId: Int, value: Int)` — exists in the same package.
- `TouchExpression(id, value, min, max, velocityId, touchEffects, exp, stopLogic, stops,
  easing)` — exists; only used internally via `scroll()`'s group emission.

**No new op classes needed.** All wire ops are decoded + writable; this lane is **purely
creation-DSL surface plumbing**.

### §1.5 — touch1.rc / touch2.rc / touch_wrap.rc — standalone TouchExpression demos
- 757 B / larger fixtures; emit MANY `ANIMATED_FLOAT` + a single `TOUCH_EXPRESSION(id=51, …)` op
  with full RPN. Demonstrates the standalone-TouchExpression use case.

---

## §2 — Procedural-DSL API surface (new, additive on `:shared` commonMain)

### §2.1 — 3 touch-event modifier methods on `LayoutModifier`

Each follows the **same pattern as REM-96 `scroll()`** — a modifier-chain method that appends an
emitter lambda. The emitter opens the `MODIFIER_TOUCH_*` op (which opens a `ListActions` scope)
and then runs an **actions block** the caller provides:

```kotlin
fun onTouchDown(actions: RemoteComposeContext.() -> Unit): LayoutModifier
fun onTouchUp(actions: RemoteComposeContext.() -> Unit): LayoutModifier
fun onTouchCancel(actions: RemoteComposeContext.() -> Unit): LayoutModifier
```

Emit pseudo-code (mirror `scroll()`):
```kotlin
fun onTouchDown(actions: RemoteComposeContext.() -> Unit): LayoutModifier {
    emitters.add { ctx ->
        ctx.add(TouchDownModifier())
        ctx.actions()                  // caller's actions inside the ListActions scope
        ctx.add(ContainerEnd())        // closes the ListActions scope
    }
    return this
}
```

`onTouchUp` / `onTouchCancel` are identical shape with `TouchUpModifier()` / `TouchCancelModifier()`
respectively.

### §2.2 — `valueIntegerChange(valueId: Int, value: Int)` action helper

Public top-level extension on `RemoteComposeContext` (mirror existing `addInt` / `addText`
pattern). Emits `VALUE_INTEGER_CHANGE_ACTION(valueId, value)` directly; designed to be called
inside the `onTouchDown { … }` actions block:

```kotlin
fun RemoteComposeContext.valueIntegerChange(valueId: Int, value: Int) {
    add(ValueIntegerChangeAction(valueId, value))
}
```

### §2.3 — Standalone `touchExpression(...)` helper (optional, MVP-eligible)

Public top-level extension on `RemoteComposeContext` (mirror `floatExpression(vararg Float):
Float`):

```kotlin
fun RemoteComposeContext.touchExpression(
    value: Float,
    min: Float,
    max: Float,
    velocityId: Float = 0f,
    touchEffects: Int = 0,
    exp: FloatArray,
    stopLogic: Int = 0,
    stops: FloatArray = floatArrayOf(),
    easing: FloatArray = floatArrayOf(),
): Float {
    val id = ids.nextId()
    add(TouchExpression(id, value, min, max, velocityId, touchEffects, exp, stopLogic, stops, easing))
    return WireTypes.asNan(id)
}
```

**MVP-decision Q1 below — include or defer?** Standalone TouchExpression is `touch1.rc` / `touch2.rc`
corpus (medium-corpus reach), but more complex (10 fields vs ~2). May split into sub-slice.

### §2.4 — Profile gating

All 3 touch-event modifiers + `VALUE_INTEGER_CHANGE_ACTION` live in the
**ANDROIDX_EXPERIMENTAL_OVERLAY** per corpus profile=0x201. Caller must open the document with
`Profile(operationsProfiles = PROFILE_ANDROIDX or PROFILE_EXPERIMENTAL)`.

Standalone `TouchExpression` opcode lives in DEFAULT_SET (already emitted by `scroll()` under
PROFILE_ANDROIDX 0x200), so it has wider profile reach.

---

## §3 — §2-Anchor plan (Triple-Pin, mirror REM-141 borderColorRef style)

Per Mensch-Steering / PO-routing: byte-anchored slices like E6-T1–T4. The full anchor matrix:

| Modifier | Strategy | What |
|----------|----------|------|
| `onTouchDown` (S1) | **Stage-2 full-doc** vs `c_modifier_on_touch_down.rc` | `box + width(200) + height(200) + background(BLUE) + onTouchDown { valueIntegerChange(42, 2) }` + the contentDescription / DATA_INT / CORE_TEXT corpus shape. Profile=ANDROIDX|EXPERIMENTAL. |
| `onTouchUp` (S1) | **Stage-2 full-doc** vs `c_modifier_on_touch_up.rc` | Same shape, opcode 0xdc, value=3, GREEN. |
| `onTouchCancel` (S1) | **Stage-2 full-doc** vs `c_modifier_on_touch_cancel.rc` | Same shape, opcode 0xe1, value=4, DARK-GREY. |
| `valueIntegerChange` (S1) | **Sub-span byte-anchor** vs `c_modifier_on_touch_down.rc` MODIFIER_VALUE_INTEGER_CHANGE_ACTION 9-byte op | Op is ID-decoupled (caller passes valueId + value as ints — no allocator coupling at the op level). Direct corpus byte-anchor. |
| `touchExpression` (S2, optional) | **Stage-1 compose==procedural** + Stage-2 sub-span vs `touch1.rc` TOUCH_EXPRESSION 49-byte op | The op has complex field layout (10 fields, NaN-id-refs); sub-span at virgin-id-pool position. |

**Bug-#2 watchpoint:** the corpus contentDescription is non-empty ("DemoModifierOnTouchDown" etc.)
→ in map-form api=7 PROFILE_EXPERIMENTAL the description is a header property. Per REM-141
DocumentDsl fix, **non-empty contentDescription** reserves id 42, so the DATA_INT in the corpus
lands at the **first user-allocated id = 42** ... but wait, **DATA_INT id=42 in the corpus** —
that conflicts with the auto-reservation. **Verify-step required:** the DocumentDsl reservation
might need to be skipped for these fixtures, OR the corpus has its own DATA_INT(42) emitted
**before** the auto-reservation, OR the description isn't actually reserving id=42 in EXPERIMENTAL
docs. **First S1 task is to confirm the id-allocation behaviour empirically — same Bug-#2-
lesson as REM-141.**

---

## §4 — `:creation-compose` Compose-DSL surface (S3, follows after procedural lands)

Mirror the E6-T1–T4 pattern (REM-130 / REM-141). Each procedural touch-event modifier gets a
Compose surface:

```kotlin
fun RemoteModifier.onTouchDown(actions: ActionsScope.() -> Unit): RemoteModifier
fun RemoteModifier.onTouchUp(actions: ActionsScope.() -> Unit): RemoteModifier
fun RemoteModifier.onTouchCancel(actions: ActionsScope.() -> Unit): RemoteModifier
```

The `ActionsScope` receiver provides Compose-friendly action emitters (e.g. `valueIntegerChange`)
that build a data-class element list (Q4 lock — equals/hashCode on the action list + applyTo
LayoutModifier delegates to the procedural surface).

**Alternative:** the actions block returns a `List<ActionElement>` and the Compose modifier
stores them directly. Either pattern preserves Q4. **Slice S3 picks** — likely the latter for
simplicity (no special receiver type needed).

---

## §5 — Open questions for PO close

| #  | Question | Recommendation |
|----|----------|----------------|
| Q1 | Include standalone `touchExpression(...)` in this lane, or split as separate sub-slice? | **Include as S2** — the corpus (`touch1.rc` etc.) uses it standalone; closing it here keeps the touch-creation surface complete. Sub-span anchor + Stage-1 compose==procedural. |
| Q2 | Actions-block shape: `RemoteComposeContext.() -> Unit` (inline, mirror scroll) or `ActionsScope.() -> Unit` (typed receiver)? | **Inline `RemoteComposeContext.() -> Unit`** for the procedural side (matches REM-96 scroll pattern). Compose-side gets `ActionsScope` for Q4-data-classness. |
| Q3 | Bug-#2 ID-allocation: corpus shows DATA_INT id=42 + non-empty contentDescription. How does this reconcile with REM-141's DocumentDsl `nextId()` reservation? | **S1 first-task: empirical decode + id-allocation verification.** If reservation conflicts, the DocumentDsl fix may need a profile-conditional path, OR the test passes `contentDescription` differently. Stop-before-impl if id mismatches at first compile. |
| Q4 | Should the Compose-DSL surface expose **only** the procedural modifiers + valueIntegerChange action, or also a higher-level RPN-DSL for action sequences? | **Procedural-mirror only** for this lane. Higher-level action-DSL is T4-style polish (post-MVP). |
| Q5 | Coordination with dev-2 Particles-S3-Touch: any shared seam? | **None expected.** dev-2 is player-side (TouchExpression evaluation against TouchState — REM-108 lane); REM-G1 is creation-side modifier surface. The two **converge at the wire** — both emit/consume the same MODIFIER_TOUCH_* + TOUCH_EXPRESSION ops. **No code conflict; complementary completion.** |
| Q6 | Naming: `onTouchDown` vs `touchDown`? | **`onTouchDown`** — mirror Compose / Android idiom for event handlers (the leading `on` signals "callback"). |

---

## §6 — Slice plan (for PO routing after OQ-close)

| Slice | Touches | Tests added | Status |
|-------|---------|-------------|--------|
| **S1** | `:shared` commonMain — `LayoutModifier.onTouchDown/Up/Cancel(actions block)` + `valueIntegerChange(valueId, value)` extension. + 3 Stage-2 full-doc byte-anchors vs `c_modifier_on_touch_{down/up/cancel}.rc` + 1 sub-span anchor for VALUE_INTEGER_CHANGE_ACTION op. | 4–6 tests in new `TouchModifierByteTest.kt`. | Start immediately on PO OQ-close. |
| **S2 (optional)** | `:shared` commonMain — `touchExpression(value, min, max, …): Float` standalone helper. + 1 Stage-2 sub-span vs `touch1.rc` TOUCH_EXPRESSION op. | 1–2 tests. | After S1 merges. May fold into S1 if scope stays small. |
| **S3** | `:creation-compose` commonMain — `RemoteModifier.onTouchDown/Up/Cancel(actions: List<ActionElement>)` + 3 ActionElement data classes + equals tests + Stage-2 Compose-DSL full-doc byte-anchors. | 3 anchor + N equality tests. | After S1+S2 merge. Mirrors REM-130/141 pattern. |

S1+S2 can ship together (~150 LOC procedural + 4–6 tests). S3 follows in a separate PR (~200 LOC
Compose-DSL + 3 + N tests).

---

## §7 — §2-Risk register (Watchpoints)

| W# | Watch | Mitigation |
|----|-------|------------|
| W12 | **ID-allocation drift** at non-empty contentDescription + PROFILE_ANDROIDX|EXPERIMENTAL. Corpus shows DATA_INT id=42 with description carrying text — needs empirical verification before S1 anchor. | First S1 task = empirical decode + write a baseline test that prints id-allocator state; only then commit to the anchor. |
| W13 | **ListActions scope leakage**: if `onTouchDown { ... }` forgets the trailing ContainerEnd, following modifiers get sucked into the action list (same trap as REM-96 scroll iter-1). | The emitter always emits `ContainerEnd()` after the actions block runs — single-source. Defensive test: `onTouchDown { … } + background(...)` → background must follow the trailing ContainerEnd. |
| W14 | **NaN-bit preservation** in `touchExpression(...)` (S2) — the `exp: FloatArray` carries NaN-encoded operator ids + variable refs. | `FloatArray` raw storage — same as REM-141 W2 belt-and-suspenders pattern. Test pins `Float.fromBits` round-trip. |
| W15 | **Profile gating** — touch-event ops are EXPERIMENTAL-overlay. Caller using PROFILE_ANDROIDX (without EXPERIMENTAL) hits a decode-time unknown-opcode error. | Document the profile requirement in the helper kdoc + smoke-test asserts the doc decodes under PROFILE_ANDROIDX|EXPERIMENTAL. |

---

## §8 — Coordination with dev-2 Particles-S3-Touch

PO noted dev-2 is parallel-scoping Particles-S3-Touch (player-side TouchExpression evaluation,
impulse / animation against TouchState). The seams:

| Seam | Dev-3 (REM-G1) | Dev-2 (Particles-S3-Touch) | Shared |
|------|----------------|-----------------------------|--------|
| MODIFIER_TOUCH_DOWN/UP/CANCEL wire shape | **Creation-side** — DSL helpers emit the op group. | Render-side — player consumes the actions. | The corpus byte-anchor in S1 IS the contract: same bytes both sides agree on. |
| VALUE_INTEGER_CHANGE_ACTION | **Creation-side** — `valueIntegerChange()` helper. | Player evaluates the action at touch time. | Same wire-anchor seam. |
| TouchExpression | **Creation-side** — standalone `touchExpression()` helper (S2). | Player evaluates RPN against TouchState (REM-108 already wired). | REM-108 has player-side anchor; G1 closes creation-side. **No code overlap.** |

**No code conflict.** PO confirmed the lanes are complementary completions of the touch
interactivity stack (§0-live core-FC). Merge order: REM-G1 can land first or in any order
relative to Particles-S3-Touch.

---

## §9 — Hand-off

Branch `feature/REM-G1-touch-modifiers-scoping-draft` carries only this doc (read-only
investigation; transient probe deleted). Awaiting PO OQ-close + assist/Mensch review per the
Mensch-Steering routing. Next step is **S1 impl** (empirical-ID-allocation-verify first per W12
+ Q3, then the 3 modifier helpers + valueIntegerChange + 4 byte-anchors).
