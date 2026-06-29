# REM-141 — E6 T3 Variable-Primitive Compose-DSL — Scoping Draft

> **Status:** DRAFT (Dev-3). Awaiting PO/assist Open-Question close before TechSpec finalisation
> and impl-slice routing.
> **Branch:** `feature/REM-141-e6-t3-variable-primitive` from develop `cabb25b`.
> **Parent epic:** REM-128 (E6 Compose-Creation-DSL).

---

## §0 — What this slice exists to close

E6-T2 (REM-130, gemergt `1688f00`) shipped 10 of the 14 REM-96 modifiers at ~95% corpus parity.
Two T2 anchors were **explicitly deferred to T3** because they reference a region-0 id that an
upstream **primitive** had to emit before the modifier could read it:

| Deferred T2 anchor                 | Why deferred (REM-130 NOTES)                                   |
|------------------------------------|----------------------------------------------------------------|
| `visibility` full-doc Stage-2      | `c_modifier_visibility.rc` emits `FloatExpression(id=42, …)`   |
|                                    | before the BoxLayout; `MODIFIER_VISIBILITY valueId=42` is a    |
|                                    | back-ref to that id.                                           |
| dynamic-color `border` full-doc    | `c_modifier_dynamic_border.rc` emits `ColorExpression(id=42,   |
|                                    | HSV …)` before the BoxLayout; `MODIFIER_BORDER colorId=42`     |
|                                    | with `flags=2` is a back-ref to that id.                       |

REM-141 introduces **primitive `@Composable`s** (FloatExpression + the 6 ColorExpression modes) to
the Compose-Creation surface, plus the **modifier overloads** that consume their allocated ids,
plus a **procedural-DSL `border()` overload** that emits the `flags=2 / colorId=N / rgba=(0,0,0,0)`
wire shape (the existing procedural `border(color: Int)` only emits `flags=0`).

**Out of scope (T4+, not this slice):** other variable-primitive consumers (e.g. dynamic
background, dynamic-text, value-binding for non-modifier draw helpers); the FloatAnimation
sub-spec on `FloatExpression`; FloatConstant convenience composable (covered by the existing
procedural helper inlined inside `scroll()` — no Compose surface needed yet).

---

## §1 — Empirical decode (Bug-#2 lesson, NOT assumed)

Probe over `c_modifier_visibility.rc` + `c_modifier_dynamic_border.rc` (transient, deleted before
push). Verified properties — **every byte that goes on the wire is grounded in the corpus, not
inferred from upstream Java**.

### §1.1 — `c_modifier_visibility.rc` (154 B, PROFILE_ANDROIDX 0x200, 400×400, contentDescription="")

```
[0] HEADER w=400 h=400 profiles=512 (api 7 map-form) — header property 9="" (zero-length string)
[1] LAYOUT_ROOT id=-2
[2] ANIMATED_FLOAT id=42 value[3]
    hex: 51 00 00 00 2a 00 00 00 03 ff 80 00 01 40 00 00 00 ff b1 00 05
    value[0]: 0xff800001 = asNan(1)                  -- system var, id 1
    value[1]: 0x40000000 = 2.0f                       -- literal float
    value[2]: 0xffb10005 = asNan(0x310005)            -- RPN operator id 5
[3] LAYOUT_BOX id=-3 (POS_CENTER, POS_CENTER)
[4..6] MODIFIER_WIDTH(EXACT, 200), MODIFIER_HEIGHT(EXACT, 200), MODIFIER_BACKGROUND(red, shape=0)
[7] MODIFIER_VISIBILITY valueId=42                    -- raw int back-ref (5-byte op, REM-130 pin)
[8..9] 2× CONTAINER_END
```

- `ANIMATED_FLOAT` opcode 0x51 = `Operations.ANIMATED_FLOAT` (class `FloatExpression` —
  the conceptual primitive is `FloatExpression`; the doc-nit in REM-130 that said "ANIMATED_FLOAT
  primitive" referred to the opcode name, but the assist-flagged correct term is **FloatExpression**,
  and REM-141 will use that name consistently).
- The 3-float RPN: `asNan(1) , 2.0 , asNan(0x310005)` — interpretable as `<sys-var-1> 2.0 <op-5>`
  in RPN. **id=1 + op=5 to be cross-referenced against `RemoteContext.ID_*` and `RcExpression.*`
  in §3 below.**

### §1.2 — `c_modifier_dynamic_border.rc` (157 B, PROFILE_ANDROIDX 0x200, 400×400, contentDescription="")

```
[0] HEADER (same shape as visibility)
[1] LAYOUT_ROOT id=-2
[2] COLOR_EXPRESSIONS id=42 params=[0x00FF0004, 0x3F800000, 0x3F333333, 0x3F666666]
    hex: 86 00 00 00 2a 00 ff 00 04 3f 80 00 00 3f 33 33 33 3f 66 66 66
    param1 = 0x00FF0004 → mode=4 (HSV) | alpha=0xFF (255) in high-16
    param2 = 0x3F800000 → hue        = 1.0f
    param3 = 0x3F333333 → saturation = 0.7f (approx)
    param4 = 0x3F666666 → value      = 0.9f (approx)
[3] LAYOUT_BOX id=-3 (POS_CENTER, POS_CENTER)
[4..5] MODIFIER_WIDTH(EXACT, 200), MODIFIER_HEIGHT(EXACT, 200)
[6] MODIFIER_BORDER flags=2 colorId=42 reserve1=0 reserve2=0 borderWidth=5.0 roundedCorner=1.0
                    r=0.0 g=0.0 b=0.0 a=0.0 shape=1
[7..8] 2× CONTAINER_END
```

- `COLOR_EXPRESSIONS` op (opcode 0x86) — class `ColorExpression`. Procedural API
  `colorExpressionHsv(hue, saturation, value, alpha)` already exists
  (`ColorExpressionHelpers.kt:94-112`) and emits exactly this byte shape, returning the raw int id.
- `MODIFIER_BORDER` carries `flags=2` (= "use colorId" — not in the existing procedural `border()`
  helper, which hardcodes `flags=0`). `colorId=42` references the `ColorExpression` id. `r/g/b/a`
  are all zero (sentinel — the player reads from the colorId path, ignores rgba).
- `shape=1` differs from the static border fixture (`shape=2`). Decoded value lifted verbatim; no
  semantic interpretation in the byte-anchor.

---

## §2 — Architecture: Phase-A → Phase-B id handoff

The Compose-DSL is two-phase (TechSpec REM-128 §1): Phase-A composition builds the node tree
(W1: NO emit), Phase-B render walks the tree and calls procedural-DSL helpers (which allocate ids
and emit ops). The challenge: **a modifier that references an id (visibility / dynamic-border)
needs to know the id of a primitive that the SAME tree walk allocates later**.

### §2.1 — The Slot pattern (recommended)

A **slot object** is created once at the call site; the primitive composable binds the slot to a
freshly-allocated id at emit time; the modifier composable reads from the slot at apply time.

```kotlin
@Composable
fun Demo() {
    RemoteRoot {
        // 1. Caller creates a stable slot (rememberable, one per call site).
        val visSlot = rememberRemoteFloatSlot()

        // 2. Primitive composable binds the slot to its writer-allocated id when render()
        //    runs in Phase B. Tree order = emit order = id-allocation order.
        RemoteFloatExpression(
            slot = visSlot,
            // RPN: asNan(sysVar=1), 2.0f, asNan(operatorId=...)
            value = floatArrayOf(systemVarNan(SysVar.X), 2f, opNan(Op.MOD)),
        )

        // 3. Modifier overload reads `slot.id` at applyToLayoutModifier(lm) time — the
        //    primitive has already run (tree order) so the id is bound.
        RemoteBoxLeaf(
            modifier = RemoteModifier
                .width(DimensionType.EXACT, 200f)
                .height(DimensionType.EXACT, 200f)
                .background(color = 0xffff0000.toInt())
                .visibility(slot = visSlot),
        )
    }
}
```

**Why this works under W1:**
- `rememberRemoteFloatSlot()` allocates a `RemoteFloatSlot` instance — a holder with
  `mutableStateOf<Int>(-1)` initial.
- `RemoteFloatExpression(slot, value)` creates a `RemoteFloatExpressionNode(slot, value)` —
  Phase A only.
- `.visibility(slot)` adds a `VisibilityFromSlotElement(slot)` to the `RemoteModifier`'s element
  list — Phase A only.
- Phase B render walks tree in order. When it hits `RemoteFloatExpressionNode`, it calls
  `ctx.floatExpression(*value)` (the procedural helper), capturing the returned NaN-id, extracts
  the plain id via `WireTypes.fromNan`, stores it in `slot.idState`.
- When it hits the modifier on the next node, `VisibilityFromSlotElement.applyToLayoutModifier(lm)`
  reads `slot.idState.value` (now bound) and calls `lm.visibility(id)`.

**Q4 (data-class equals/hashCode) holds**: the slot object's identity is the same across
recomposition (rememberable), so `VisibilityFromSlotElement(slot)` data-class equality compares
the slot reference, not its mutable contents — `update { set(modifier) }` change-detection skips
unchanged compositions correctly.

### §2.2 — Alternative considered (rejected for MVP)

**`@Composable` returning the id directly** (`val id = rememberRemoteFloatExpression(...)`).
Rejected because the id isn't known during composition (Phase A); we'd have to surface a deferred
`State<Int>` anyway, and the caller couldn't write the integer literally into the modifier (would
still need a slot-like indirection). The Slot pattern is the same machinery with explicit naming.

---

## §3 — Public API surface (Slice scope)

### §3.1 — Primitives (`:creation-compose` commonMain, additive)

```kotlin
// Slots
class RemoteFloatSlot internal constructor() { val idState: MutableIntState }
class RemoteColorSlot internal constructor() { val idState: MutableIntState }

@Composable fun rememberRemoteFloatSlot(): RemoteFloatSlot
@Composable fun rememberRemoteColorSlot(): RemoteColorSlot

// Float primitive — FloatExpression (ANIMATED_FLOAT wire op)
@Composable
fun RemoteFloatExpression(slot: RemoteFloatSlot, vararg value: Float)
@Composable
fun RemoteFloatExpression(slot: RemoteFloatSlot, value: FloatArray, animation: FloatArray? = null)

// Color primitive — ColorExpression (COLOR_EXPRESSIONS wire op), 6 modes mirror procedural
@Composable fun RemoteColorExpressionInterpolate(slot, color1: Int, color2: Int, tween: Float)
@Composable fun RemoteColorExpressionInterpolateIdLit(slot, colorId1: Int, color2: Int, tween: Float)
@Composable fun RemoteColorExpressionInterpolateLitId(slot, color1: Int, colorId2: Int, tween: Float)
@Composable fun RemoteColorExpressionInterpolateIds(slot, colorId1: Int, colorId2: Int, tween: Float)
@Composable fun RemoteColorExpressionHsv(slot, hue: Float, saturation: Float, value: Float, alpha: Int = 255)
@Composable fun RemoteColorExpressionArgb(slot, alpha: Float, red: Float, green: Float, blue: Float)
@Composable fun RemoteColorExpressionArgbById(slot, alphaId: Int, red: Float, green: Float, blue: Float)
```

Plus a small **RPN-helper surface** so callers don't compose raw NaN-int bit-fiddly: re-export
`com.tneff.kmpremotecompose.remote.creation.RcExpression` constants (TIME_IN_SEC, ADD, MUL, etc.)
and a `systemVarNan(SysVar)`/`opNan(Op)` convenience pair.

### §3.2 — Modifier consumers (`:creation-compose` commonMain, additive overloads)

```kotlin
// On RemoteModifier — new slot-accepting overloads alongside the existing raw-int forms.
fun RemoteModifier.visibility(slot: RemoteFloatSlot): RemoteModifier
fun RemoteModifier.border(
    borderWidth: Number,
    roundedCorner: Number,
    colorIdSlot: RemoteColorSlot,
    shape: Int = 0,
    useLegacy: Boolean = true,
): RemoteModifier
```

Plus 2 internal `RemoteModifierElement` data classes:
- `VisibilityFromSlotElement(slot: RemoteFloatSlot)` — `applyToLayoutModifier(lm) { lm.visibility(slot.id) }`
- `BorderFromSlotElement(borderWidth, roundedCorner, slot, shape, useLegacy)` — calls the **new**
  procedural overload (§3.3 below).

### §3.3 — Procedural-DSL gap (`:shared` commonMain, ADDITIVE — 1 new fn)

The existing `LayoutModifier.border(color: Int, …)` hardcodes `flags=0` and decomposes the int.
A new overload is needed to emit the **`flags=2 / colorId=N / rgba=(0,0,0,0)`** wire shape:

```kotlin
/**
 * `MODIFIER_BORDER` — dynamic-color form (colorId-ref). Mirrors upstream
 * `addModifierBorder(..., colorId, shape)` with `flags=2 / colorId / r/g/b/a=0`.
 */
fun border(
    borderWidth: Number,
    roundedCorner: Number,
    colorId: Int,
    shape: Int = 0,
    useLegacy: Boolean = true,
): LayoutModifier
```

**This is the only commonMain change in this slice** — all other work is `:creation-compose`
commonMain (additive) or jvmTest. The procedural addition gets its own REM-96-style byte-anchor
test in `LayoutModifierByteTest` (`border_dynamicColor_fullByteEquality_vsCorpusFixture_dynamicBorder`).

---

## §4 — §2-Gate: Triple-Pin per modifier

Per REM-130 PO-dispatch: §2-Gate = Triple-Pin: (Stage-1 compose==procedural) · (Stage-2 ==
Korpus-Oracle byte-for-byte = **der Gate**) · (Stage-3 Determinismus 2×).

### §4.1 — visibility full-doc Stage-2

| Stage | Test                                                                 | What it pins                          |
|-------|----------------------------------------------------------------------|---------------------------------------|
| 1     | `stage1_visibility_compose_equals_procedural_with_floatExpression`   | Compose-DSL bytes == procedural-DSL bytes for the same expression. |
| 2     | `stage2_visibility_full_doc_matches_c_modifier_visibility_oracle`    | **Full-doc byte-equal to `c_modifier_visibility.rc` (154 B).** This is the deferred T2 anchor now closed. |
| 3     | `stage3_visibility_two_renders_byte_identical`                       | Captured twice in the same JVM → equal bytes (determinism, no id-leak between renders). |

### §4.2 — dynamic-color border full-doc Stage-2

| Stage | Test                                                                 | What it pins                          |
|-------|----------------------------------------------------------------------|---------------------------------------|
| 1     | `stage1_dynamicBorder_compose_equals_procedural`                     | Compose-DSL bytes == procedural-DSL bytes for `colorExpressionHsv(...) + border(…, colorId)`. |
| 2     | `stage2_dynamicBorder_full_doc_matches_c_modifier_dynamic_border_oracle` | **Full-doc byte-equal to `c_modifier_dynamic_border.rc` (157 B).** |
| 3     | `stage3_dynamicBorder_two_renders_byte_identical`                    | Determinism. |

### §4.3 — Per-primitive equals/hashCode (Q4-lock)

- `RemoteFloatExpressionNode` / `RemoteColorExpression*Node` — node identity is the slot reference
  + the immutable `value` floats (data class on the value arrays — `contentEquals` semantics
  required because primitive floats need bit-equality, not float-equality, for NaN preservation).
- `VisibilityFromSlotElement` / `BorderFromSlotElement` — data classes on the slot reference (the
  slot's `idState` is excluded from equality, only the slot object identity matters; modifier
  identity should NOT change as id allocation evolves).
- Existing `RemoteModifierEqualsTest` extended with slot-overload equality tests.

### §4.4 — §5.9 4-target compile gate

`jvm + iosSimulatorArm64 + wasmJs + androidMain + androidHostTest` — same hard gate REM-128
established. The new procedural `border(colorId)` overload + new `:creation-compose` composables
all live in commonMain → must compile clean on every target.

---

## §5 — Open questions for assist / PO close

| #  | Question                                                                                              | Recommendation                                              |
|----|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Q1 | Slot pattern vs `@Composable`-returning-`State<Int>`?                                                 | Slot — explicit, no Compose runtime surprise. §2.1.          |
| Q2 | Procedural `border(colorId)` — new fn or extend existing `border()` with `colorId: Int? = null` param? | New fn — keeps existing helper byte-stable; clear intent.    |
| Q3 | FloatExpression — expose full RPN vararg `Float` API (mirror procedural) or wrap in higher-level DSL? | Full RPN (mirror procedural). Higher-level wrappers (e.g. `RemoteFloatExpression.timesConstant(2)`) are T4 polish. |
| Q4 | Scope this slice: visibility + dynamic-border only? Or include other primitive-consumers (dynamic background, dynamic text)? | Visibility + dynamic-border only. Other consumers → T4.    |
| Q5 | Should the primitive composables emit even when their slot has no modifier consumer (dead code)? Or skip-if-unused?                              | Emit always. Skip-if-unused requires a 2-pass walk; out of scope. |
| Q6 | What corpus value gets pinned at the visibility FloatExpression `value[3]`? `[asNan(1), 2.0, asNan(0x310005)]` — confirm `id=1` and `op=5` are the right symbolic names (system var X? a specific RPN op?).      | Use the raw decoded bytes verbatim in the test (no symbolic interpretation needed for the byte-anchor). Symbolic mapping is for the helper convenience surface (§3.1 RPN-helpers), can be deferred. |

---

## §6 — Slice plan (for PO routing after open-questions close)

| Slice | Touches                                                                                  | Tests added                                 |
|-------|------------------------------------------------------------------------------------------|---------------------------------------------|
| S1    | `:shared` commonMain — new `LayoutModifier.border(colorId, …)` overload + REM-96 byte-anchor test against `c_modifier_dynamic_border.rc` (Stage-2 sub-span on `MODIFIER_BORDER flags=2`). | 1 byte-anchor test in `LayoutModifierByteTest`. |
| S2    | `:creation-compose` commonMain — Slot types, `rememberRemoteFloatSlot` / `rememberRemoteColorSlot`, `RemoteFloatExpression` composable, 6× `RemoteColorExpression*` composables. | Triple-Pin on visibility & dynamic-border full-doc. Existing equals tests extended. |
| S3    | `:creation-compose` commonMain — `RemoteModifier.visibility(slot)` + `RemoteModifier.border(…, colorIdSlot)` overloads. Stage-2 full-doc anchors against `c_modifier_visibility.rc` + `c_modifier_dynamic_border.rc`. | Equals tests for slot-overloads. |

S1 may merge independently (procedural-only change); S2+S3 ship together (interlocked).

---

## §7 — §2-Risk register (Watchpoints)

| W# | Watch                                                                            | Mitigation                                                                 |
|----|----------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| W1 | **id-allocation order**: corpus emits primitive id=42 first (virgin allocator). | Tests use no contentDescription header property and no other id-allocating composables → first `nextId()` = 42. Hardcoded in the byte-anchor. |
| W2 | **NaN-bits preservation** in `FloatExpression.value` (asNan operator refs).      | Element holds `FloatArray`, `applyToLayoutModifier` passes verbatim to `floatExpression(*value)` — no float arithmetic that could repack NaN. Probe-confirmed corpus has `asNan(1) = 0xFF800001` and `asNan(0x310005) = 0xFFB10005`. |
| W3 | **Slot used across multiple recompositions**: idState must remain stable.        | Slot.idState is `mutableIntStateOf(-1)`, set once per render — Phase-B clear at start of next render resets it. Determinism test (Stage-3) catches drift. |
| W4 | **Slot ref'd before primitive emits**: caller writes `.visibility(slot)` modifier BEFORE the primitive composable → modifier applies before slot has an id → wire emits id=-1, byte-diverges. | Phase-B render-walk emits modifiers AS PART OF the container's open-op (after the surrounding leaf-Box's open). Test pattern: primitive composable must be placed BEFORE the leaf consumer in tree order. Lint/compile-time check is out-of-scope; runtime `check(slot.id >= 0)` in `applyToLayoutModifier` catches the misuse with a clear failure. |
| W5 | **`@Composable` returning value (Q1 alternative)**: rejected, but documented for the record. | §2.2 — slot pattern is the explicit, debuggable choice. |

---

## §8 — Non-goals (explicit)

- No `RemoteFloatConstant(slot, value: Float)` composable — `FloatConstant` is only used internally
  by `scroll()`; no Compose-level use case identified in T3 corpus. Out of T3 scope.
- No FloatAnimation wiring — `FloatExpression.animation: FloatArray?` is round-tripped on the
  wire (the existing procedural helper accepts it) but no corpus fixture exercises animation in
  T3 scope. Players' animation execution is Epic-D1.
- No Compose-level RPN expression builder (e.g. `Expression { TimeInSec * 2 }`) — direct
  `floatArrayOf(...)` with raw `asNan(...)` constants stays the surface. Higher-level RPN DSL is
  T4 polish.
- No `NamedVariable` Compose composable — the procedural `addNamedVariable` / `setNamedVariable`
  helpers exist (`ColorExpressionHelpers.kt:191-206`); Compose-level naming UX is T4.

---

## §9 — assist/PO close needed before TechSpec → impl

PO routes scoping → assist reviews/finalises TechSpec (per REM-128 / REM-130 pattern). The
open-question close in §5 + the slice plan in §6 are the asks. Branch
`feature/REM-141-e6-t3-variable-primitive` (only this doc + transient probe) ready for review.
