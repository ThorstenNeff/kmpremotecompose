# REM-144 — E6 T4 Polish — TechSpec

> **Status:** PO-approved 2026-06-29 (msg 1521177479639728381). All 7 §4 open questions closed
> with Dev-3 recommendations; 4-slice plan approved. **Start S1 + S2 in parallel; S3 depends
> on S1; S4 (sample-app screen) last with test-1 Android Maestro routing post-S4.**
> **Branch:** `feature/REM-144-e6-t4-polish-scoping-draft` from develop `9532010` (E6-stable
> tip; REM-142 / REM-139 non-E6 work intentionally excluded from the merge base).
> **Parent epic:** REM-128 (E6 Compose-Creation-DSL).

---

## §0 — What this slice exists to close

E6 reached **byte-FC-completeness** at REM-141 (`7e2f9b8`): all 14 REM-96 modifiers
Compose-accessible, both REM-130-T3-deferred posten (visibility full-doc + dynamic-color border
full-doc) closed via the Slot-pattern variable primitives. The corpus byte-anchor coverage is
exhaustive within the modifier set.

REM-144 finishes E6 by closing the **3 explicit T4-polish posten** PO routed (msg
1521175810868773056):

1. **Higher-level RPN-DSL** — ergonomic builder over `floatExpression(vararg Float)` so callers
   don't hand-assemble `[asNan(id), 2f, RcExpression.MOD]`. Approach: operator-overloaded value
   class with `+ - * / %` etc. emitting the right RPN sequence.
2. **dynamic-background** — Modifier overload analog to dynamic-border (REM-141 S1/S2/S3),
   colorId-Slot-driven (plus raw `colorId: Int` for system colour refs). Closes the
   `c_modifier_background_id.rc` corpus byte-anchor (currently no Compose-DSL surface for it).
3. **§6 Maestro End-to-End Flow Proof** — Compose-Creation-DSL emits .rc → Sample-App renders
   via existing RemoteComposePlayer → Maestro flow asserts the rendered output. Closes PROJECT
   §6 `"done means Maestro-proven"` bar for the entire E6 line.

**Out of scope (T5+, never-in-E6):** other primitive consumers (dynamic text, dynamic dimensions),
NamedVariable Compose surface, FloatAnimation runtime wiring (Epic-D1), 0-corpus modifier
long-tail (zIndex/click/canvas — those are existing procedural-DSL but lack corpus byte-anchors,
hence parked).

---

## §1 — Posten 1: Higher-level RPN-DSL

### §1.1 — Why it's a polish item

REM-141's `RemoteFloatExpression(slot, vararg Float)` API works but pushes the RPN structure
onto callers. Compare:

```kotlin
// Today (REM-141): caller hand-builds RPN
RemoteFloatExpression(slot, floatArrayOf(
    RcExpression.CONTINUOUS_SEC,
    2f,
    RcExpression.MOD,
))

// REM-144 high-level (proposed):
RemoteFloatExpression(slot, RcExpression.CONTINUOUS_SEC.rcVar() % 2f.rcLit())
// or with smart-conversion implicits/extensions:
RemoteFloatExpression(slot, CONTINUOUS_SEC % 2f)
```

The high-level builder must produce **byte-identical RPN** to the equivalent vararg call — same
NaN bits, same operator ids, same array order.

### §1.2 — Mechanism

A value class wrapping a `FloatArray` of RPN, with operator overloads producing concatenated RPN:

```kotlin
@JvmInline
value class RcExpr internal constructor(val rpn: FloatArray)

// Construction:
fun Float.rcLit(): RcExpr = RcExpr(floatArrayOf(this))         // 1.0f.rcLit()
fun Int.rcId(): RcExpr = RcExpr(floatArrayOf(WireTypes.asNan(this)))  // 42.rcId()
fun Float.rcVar(): RcExpr = RcExpr(floatArrayOf(this))         // CONTINUOUS_SEC.rcVar() (already NaN-encoded)

// Operators — emit standard RPN: lhs.rpn + rhs.rpn + [opcode-as-NaN]
operator fun RcExpr.plus(rhs: RcExpr): RcExpr = RcExpr(rpn + rhs.rpn + floatArrayOf(RcExpression.ADD))
operator fun RcExpr.plus(rhs: Float): RcExpr = this + rhs.rcLit()
operator fun RcExpr.minus(rhs: RcExpr): RcExpr = RcExpr(rpn + rhs.rpn + floatArrayOf(RcExpression.SUB))
operator fun RcExpr.times(rhs: RcExpr): RcExpr = RcExpr(rpn + rhs.rpn + floatArrayOf(RcExpression.MUL))
operator fun RcExpr.div(rhs: RcExpr): RcExpr = RcExpr(rpn + rhs.rpn + floatArrayOf(RcExpression.DIV))
operator fun RcExpr.rem(rhs: RcExpr): RcExpr = RcExpr(rpn + rhs.rpn + floatArrayOf(RcExpression.MOD))
// Unary:
fun RcExpr.sqrt(): RcExpr = RcExpr(rpn + floatArrayOf(RcExpression.SQRT))
fun RcExpr.abs(): RcExpr = RcExpr(rpn + floatArrayOf(RcExpression.ABS))
// ... (full operator set)

// New RemoteFloatExpression overload accepting RcExpr:
@Composable
fun RemoteFloatExpression(slot: RemoteFloatSlot, expression: RcExpr) {
    RemoteFloatExpression(slot, value = expression.rpn)  // delegates to FloatArray overload
}
```

### §1.3 — Byte-anchor strategy

**Stage-2 corpus anchor: REUSE `c_modifier_visibility.rc`** — its FloatExpression carries the RPN
`[asNan(1), 2.0, MOD]` which is exactly `CONTINUOUS_SEC % 2`. The high-level builder must reproduce
this byte-for-byte:

```kotlin
@Test
fun stage2_rpnDsl_visibility_matchesCorpus() = runBlocking {
    val produced = captureSingleRemoteDocument(...) {
        RemoteRoot {
            val slot = rememberRemoteFloatSlot()
            RemoteFloatExpression(slot, RcExpression.CONTINUOUS_SEC.rcVar() % 2f.rcLit())
            RemoteBoxLeaf(modifier = RemoteModifier...visibility(slot))
        }
    }
    assertContentEquals(RcCorpus.readFixture("corpus/c_modifier_visibility.rc"), produced)
}
```

**This is a §2-strong anchor:** the same corpus that REM-141 hit with the vararg-Float API is hit
again with the high-level DSL — both must produce identical bytes. If the high-level DSL repacks
NaN bits silently or reorders RPN operands, the test catches it.

### §1.4 — §2 watchpoint

- **W7 NaN-bit preservation through value-class boxing.** `@JvmInline value class RcExpr(val rpn:
  FloatArray)` should not touch float bits — `FloatArray` stores raw IEEE 754. Belt-and-suspenders
  test: round-trip `Float.fromBits(0xff800001)` through `.rcVar().rpn[0].toRawBits()` and assert
  bit-equality.

---

## §2 — Posten 2: dynamic-background

### §2.1 — Corpus anchor (empirical decode, transient probe deleted)

`c_modifier_background_id.rc` (145 B, PROFILE_ANDROIDX, 400×400, contentDescription=""):

```
[0] HEADER w=400 h=400 profiles=512 props=[5=400, 6=400, 9=, 14=512]
[1] LAYOUT_ROOT id=-2
[2] LAYOUT_BOX id=-3 (POS_CENTER, POS_CENTER)
[3] MODIFIER_WIDTH(EXACT, 200), [4] MODIFIER_HEIGHT(EXACT, 200)
[5] MODIFIER_BACKGROUND flags=2 colorId=1 rgba=(0,0,0,0) shape=0    -- DYNAMIC FORM
    hex: 37 00 00 00 02 00 00 00 01 00 00 00 00 00 00 00 00 …
[6] MODIFIER_PADDING(10,10,10,10)
[7..8] 2× CONTAINER_END
```

**Surprise finding:** `colorId=1` is **not** a region-0 allocated id (those start at 42 in
contentDescription="" docs). It's a **system colour id** — likely a theme colour. Upstream's
NamedVariable infrastructure defines colour-ids in a separate well-known range (e.g.
`color.system_accent1_0` etc.). The corpus document does NOT emit a `COLOR_EXPRESSIONS` or
`COLOR_CONSTANT` op for id=1 — it just references the system colour directly.

**Implication for the Compose API:** dynamic-background must support **TWO** flavours:
- **Raw-int form** (`background(colorId: Int)` taking a system colour id like `1` — no primitive
  composable required); reproduces `c_modifier_background_id.rc`.
- **Slot form** (`background(colorIdSlot: RemoteColorSlot)` paired with a `RemoteColorExpression*`
  primitive composable that allocates a region-0 colour id); the symmetric counterpart to
  `border(colorIdSlot)` from REM-141.

### §2.2 — Procedural-DSL gap (`:shared` commonMain, ADDITIVE — 1 new fn, mirror REM-141 S1)

The existing `LayoutModifier.background(color: Int)` hardcodes `flags=0` and decomposes the int
to r/g/b/a floats. A new helper is needed for the **`flags=2 / colorId / rgba=(0,0,0,0)`** wire
shape (parallel to REM-141 S1's `borderColorRef`):

```kotlin
/**
 * REM-144 — `MODIFIER_BACKGROUND` dynamic-color form. Mirrors upstream
 * `addModifierBackground(int colorId, int shape)` overload: `flags=2 / colorId / rgba=0`.
 * Distinct from `background(color: Int)` which hardcodes `flags=0`. Separate name avoids the
 * same-signature ambiguity (Q2 lesson, REM-141 S1).
 */
fun backgroundColorRef(colorId: Int, shape: Int = 0): LayoutModifier
```

REM-96-style byte-anchor test in `LayoutModifierByteTest.kt`: sub-span vs
`c_modifier_background_id.rc` MODIFIER_BACKGROUND 37-byte op.

### §2.3 — `:creation-compose` API

```kotlin
// Raw-int form — for system colour refs (parallel to existing RemoteModifier.visibility(valueId: Int)):
fun RemoteModifier.background(colorId: Int, shape: Int = 0): RemoteModifier

// Slot form — paired with a RemoteColorExpression* primitive:
fun RemoteModifier.background(colorIdSlot: RemoteColorSlot, shape: Int = 0): RemoteModifier
```

Two new internal data-class elements:
- `BackgroundColorRefElement(colorId, shape)` — raw int form
- `BackgroundColorRefFromSlotElement(slot, shape)` — slot form

### §2.4 — Byte-anchor strategy

| Stage | Test                                                           | What                                                    |
|-------|----------------------------------------------------------------|---------------------------------------------------------|
| 1     | `backgroundColorRef_emitsFlags2_andStoresColorIdAndZeroRgba`   | REM-96 field-level pin in LayoutModifierByteTest.       |
| 2     | `backgroundColorRef_fullByteEquality_vsCorpusFixture_backgroundId` | REM-96 sub-span byte-anchor vs `c_modifier_background_id.rc`. |
| 2     | `stage2_background_raw_int_matchesCModifierBackgroundIdOracle` | `:creation-compose` full-doc byte-anchor (raw-int form). |
| 2     | `stage2_background_slot_form_matchesProcedural` (Stage-1)      | Slot form has no dedicated corpus fixture (the corpus uses a system id, not a region-0 ColorExpression). Stage-1 compose==procedural through the new procedural helper — transitive §2 via the REM-96 sub-span anchor (which IS corpus-anchored). |

### §2.5 — Watchpoints

- **W8 colorId disambiguation:** `background(color: Int)` vs `background(colorId: Int)` — same
  signature. **Solution:** use named-only param (`shape: Int = 0` differs in default, but the
  Kotlin resolver picks the first by positional types). **Safer:** the slot form has a different
  arg type (`RemoteColorSlot` not `Int`), no conflict. **For the raw-int form:** use a different
  name (`backgroundColorRef(colorId: Int)` — parallel to `borderColorRef`). Same Q2 lesson.

---

## §3 — Posten 3: §6 Maestro End-to-End Flow Proof

### §3.1 — The bar

PROJECT §6: *"Maestro auf einer schlanken Beispiel-App, früh und durchgehend. Jeder Meilenstein
wird validiert, indem echte `.rc`-Dokumente in einer minimalen Android- + iOS-Beispiel-App
gerendert und per Maestro-Flows geprüft werden."*

E6 is currently byte-anchored end-to-end but lacks the **create-side-driving-render-side** Maestro
proof. The bar: a Maestro flow runs against a sample-app that, **at runtime**, builds an `.rc`
document via the Compose-Creation-DSL, hands it to `RemoteComposePlayer`, and renders it on
screen. Maestro asserts that the rendered output matches the expected visual.

### §3.2 — Mechanism

**Dev-3 deliverable (Compose-Creation side):**
- A new sample-app screen (`E6CreationProofScreen` in `androidApp/iosApp` shared sample-app
  surface) that on activation:
  1. Calls `captureSingleRemoteDocument { ... }` with a Compose-DSL recipe (e.g. a column with
     2 coloured BoxLeafs, similar to `c_modifier_padding.rc` but with deterministic content).
  2. Hands the bytes to `RemoteComposePlayer` to render in-place.
  3. Exposes a Maestro-friendly test-id on the rendered widget.

**Tester deliverable (Maestro + sample-app integration):**
- A Maestro flow (.yaml) that:
  - Launches the sample-app
  - Navigates to the E6CreationProofScreen
  - Captures a screenshot
  - Asserts visual state (either via test-id assertion + content-description, or via
    Maestro's `assertVisible: "..."` if text is involved)

**Tester runs:** test-1 (Android/iOS Maestro flows) or test-3 (Desktop sample-app) — PO routes
post-scoping.

### §3.3 — Acceptance recipe

The Compose-DSL recipe should be **simple, deterministic, visually-distinct** — easy for Maestro
to assert. Proposal:

```kotlin
@Composable
fun E6CreationProofContent(player: RemoteComposePlayer) {
    val bytes = remember {
        runBlocking {
            captureSingleRemoteDocument(width = 300, height = 100, profile = androidx, contentDescription = "") {
                RemoteRoot {
                    RemoteRow(modifier = RemoteModifier.width(DimensionType.FILL, Float.NaN)) {
                        RemoteBoxLeaf(modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 100f)
                            .background(color = 0xffff0000.toInt()))
                        RemoteBoxLeaf(modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 100f)
                            .background(color = 0xff00ff00.toInt()))
                        RemoteBoxLeaf(modifier = RemoteModifier
                            .width(DimensionType.EXACT, 100f).height(DimensionType.EXACT, 100f)
                            .background(color = 0xff0000ff.toInt()))
                    }
                }
            }
        }
    }
    player.load(bytes)
    player.render(this)
}
```

Three coloured rects side-by-side. Maestro asserts the rendered output has the expected pixel
colours at three known positions.

### §3.4 — §2 / acceptance bar

This is a **render-correctness** test, not a byte-anchor. Acceptance:
- Compose-DSL builds bytes successfully (already byte-anchored by E6-T1/T2/T3 tests).
- `RemoteComposePlayer` renders the bytes successfully (already proven by existing
  player tests against the same corpus).
- Maestro flow passes (= the integrated round-trip works in a real app).

**§2 already covered**: the byte-anchor is in the E6-T1/T2/T3 tests; Maestro adds the
render-side proof on top.

---

## §4 — Open questions — ALL RESOLVED (PO-close 2026-06-29, msg 1521177479639728381)

All 7 OQs closed with Dev-3 recommendations. The table is preserved as the audit trail; the
**Close** column is the canonical decision now folded into the slice plan §5.

| #  | Question                                                                                              | Recommendation                                              | Close                                              |
|----|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|----------------------------------------------------|
| Q1 | Higher-level RPN-DSL — value class `RcExpr(rpn: FloatArray)` with operator overloads?                  | Yes. Idiomatic Kotlin. Stage-2 anchor via c_modifier_visibility.rc reuses the existing corpus oracle. | ✅ ACCEPTED — `@JvmInline value class RcExpr(rpn: FloatArray)` + operator overloads. |
| Q2 | dynamic-background raw-int form: separate name `backgroundColorRef` or overload via different signature? | Separate name (Q2 lesson, REM-141 S1). | ✅ ACCEPTED — separate name `backgroundColorRef` (REM-141 S1 precedent). |
| Q3 | Maestro recipe — what should the sample app draw? Trivial 3-color row (my proposal §3.3) vs something using a T2/T3 modifier (padding/border/dynamic-color)? | Start with trivial 3-color row → simpler Maestro assertions. Add a T2/T3-modifier screen as follow-up if needed. | ✅ ACCEPTED — trivial 3-color row first. T2/T3 modifier screens deferred. |
| Q4 | Maestro on which target (Android, iOS, Desktop)? Just one for E6-close, or all three? | One Android (most stable Maestro path) for E6-close. iOS / Desktop are post-E6 stretch. | ✅ ACCEPTED — one Android for E6-close (§6 = "≥1 Target"); iOS/Desktop stretch post-E6. |
| Q5 | Is the sample-app integration screen Dev-3's commit, or a Tester's? | I write the screen + recipe (Compose-Creation side); Tester writes the Maestro flow. PO routes the Tester. | ✅ ACCEPTED — Dev-3 screen+recipe; **test-1 (Android)** Maestro-flow; PO routes test-1 post-S4. |
| Q6 | Higher-level RPN naming: `rcLit`/`rcVar`/`rcId` extension shorthand vs verbose `RcExpr.literal(f)` companion?    | Extension shorthand — cleaner call site, easier-to-read tests. | ✅ ACCEPTED — extension shorthand. |
| Q7 | RPN-DSL — operator coverage in this slice? All 32 RcExpression ops or just the ~10 common ones (+/-/*/⁄/%/sqrt/abs/sin/cos/clamp)? | Common 10 — extensible later. Full coverage is mechanical follow-up if needed. | ✅ ACCEPTED — ~10 common ops; full coverage = mechanical follow-up if needed. |

**Additional PO §2-note (msg 1521177479639728381, slot-form transitive §2-soundness):** the
MODIFIER_BACKGROUND `flags=2` op-encoding is corpus-byte-anchored via the raw-int sub-span anchor
(`c_modifier_background_id.rc`); the slot-form (which sources its `colorId` from a region-0
`ColorExpression` instead of a system id) emits the **same op shape** — only the colorId int
field differs in value. Slot-form is therefore **transitively §2-sound via the raw-int sub-span
anchor** (no separate corpus fixture needed). Same shape of argument as REM-141 borderColorRef
slot-vs-raw-int. Folded into §6 W8 below.

---

## §5 — Slice plan (PO-approved 2026-06-29)

| Slice | Touches                                                                                 | Tests added                                 | Routing |
|-------|-----------------------------------------------------------------------------------------|---------------------------------------------|---------|
| S1    | `:shared` commonMain — new `LayoutModifier.backgroundColorRef(colorId, shape)` helper + REM-96 byte-anchor vs `c_modifier_background_id.rc`. | 2 byte-anchor tests in `LayoutModifierByteTest`. | **Dev-3 — start now, parallel with S2.** |
| S2    | `:creation-compose` commonMain — RPN-DSL value class `RcExpr` + extension shorthands + operator overloads (~10 common ops) + new `RemoteFloatExpression(slot, RcExpr)` overload. | Stage-2 byte-anchor reusing `c_modifier_visibility.rc` (RPN expressed via DSL). NaN-bit round-trip test. | **Dev-3 — start now, parallel with S1.** |
| S3    | `:creation-compose` commonMain — `RemoteModifier.background(colorId: Int)` + `RemoteModifier.background(colorIdSlot: RemoteColorSlot)` overloads. | Stage-2 full-doc byte-anchor vs `c_modifier_background_id.rc` (raw-int form). Slot-form Stage-1 compose==procedural (transitively §2-sound via S1 sub-span anchor — same op shape, different colorId source). Equals tests for new elements. | **Dev-3 — after S1 merges.** |
| S4    | sample-app — `E6CreationProofScreen` (Compose-side, Dev-3). + Maestro flow (Tester, separate PR). | Render-only verification at Compose compile time. Maestro acceptance via test-1 (Android). | **Dev-3 writes screen+recipe last; PO routes test-1 for Maestro flow.** |

**Sequencing:** S1+S2 start together (independent); S3 follows after S1 merges; S4 last (depends
on full E6 surface being stable + merged). test-1 (Android) Maestro-flow routing happens **after
S4 is pushed**.

---

## §6 — §2-Risk register (Watchpoints)

| W# | Watch                                                                            | Mitigation                                                                 |
|----|----------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| W6 | Higher-level RPN-DSL allocates `FloatArray`s during operator chains — perf could matter under recomposition. | Acceptable for MVP — composition is not on the hot rendering path. Optimisation (e.g. pooled arrays) is later if needed. |
| W7 | NaN-bit preservation through `@JvmInline value class` boxing.                    | Belt-and-suspenders test (round-trip `Float.fromBits(0xff800001)` through `.rcVar()`). |
| W8 | Background dynamic-color same-signature ambiguity (`background(Int)` vs new form). | **Procedural-DSL:** separate name `backgroundColorRef` (PO-confirmed Q2-close — REM-141 S1 precedent). **Compose-DSL slot-form:** distinct param type `RemoteColorSlot` (unambiguous against `background(color: Int)`). **Compose-DSL raw-int form:** uses the SAME name `background` overloaded via a marker — to avoid the procedural's ambiguity at the Compose surface, we use a dedicated overload that takes a non-ambiguous companion-marker or a wrapper type. **Detail to nail down in S3:** if Kotlin overload resolution flags ambiguity between `background(color: Int)` and `background(colorId: Int)` (same positional type), fall back to a distinct name `backgroundColorRef(colorId: Int)` matching the procedural side. Slot-form is unambiguous either way. |
| W11| **Slot-form §2-soundness without dedicated corpus fixture** (PO §2-note, msg 1521177479639728381). | Slot-form emits the SAME op shape as the raw-int form — only the `colorId` value field differs (region-0 from ColorExpression vs system id). Raw-int form's sub-span anchor against `c_modifier_background_id.rc` (S1) IS the byte-anchor for the op shape; slot-form is transitively §2-sound via that anchor + Stage-1 compose==procedural test that proves the new `background(colorIdSlot)` Compose surface emits bytes identical to the new procedural `backgroundColorRef(colorId)` helper (which IS corpus-anchored). Same shape of argument as REM-141 borderColorRef slot-vs-raw-int. |
| W9 | Maestro flow flakiness — pixel-perfect rendering may vary across runs/devices.   | Use a coarse-grain visual oracle (e.g. test-id + content-description) rather than exact-pixel match. Tester picks the right granularity. |
| W10| Sample-app screen lifecycle and `captureSingleRemoteDocument`-suspend integration in non-coroutine UI scope. | Use `LaunchedEffect` to drive the capture asynchronously; cache the bytes in `remember`. |

---

## §7 — Non-goals (explicit)

- No higher-level RPN-DSL that introduces named-variable bindings or compound expressions over
  multiple statements (`val x = ...; val y = x + ...`) — single-expression scope only.
- No dynamic-color binding for shapes/strokes other than background + border (already covered) —
  e.g. text colour, gradient stops remain T5+.
- No Maestro coverage beyond ONE screen for E6-close. Per-modifier Maestro flows are T5+.
- No multi-target Maestro (Android only for E6-close; iOS / Desktop deferred).
- No FloatAnimation runtime integration (Epic-D1).

---

## §8 — Status (PO-canonical)

PO approved 2026-06-29 (msg 1521177479639728381). All 7 §4 OQs resolved, 4-slice plan in §5 is
canonical. **Dev-3 starts S1+S2 in parallel.** This TechSpec is the canonical reference for the
implementation; future amendments require a separate Spec-amendment commit.
