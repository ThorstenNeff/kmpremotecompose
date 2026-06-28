# TECHSPEC — REM-108 Epic-F S3b: ScrollModifier interactive (FINAL)

> **Status:** FINAL (assist-finalized from dev-1 draft + dev-2 layout input a/b/c). Supersedes
> `TECHSPEC-REM-108-S3b-scroll-DRAFT.md`. **Impl gate: waits S2b test-1-on-device-GO** (no stacking on
> unproven S2b). Upstream grounded vs `./androidx` (behavior, not paste — PROJECT_CONTEXT §5).
> S3a (notch-mode validation) is independent — see §7.

## 1. Problem
`ScrollModifier` (`MODIFIER_SCROLL` = 226) is `: Operation` only — a byte-faithful carrier (`direction`,
`position`, `max`, `notchMax`) with **no render effect**. Scroll docs decode + round-trip byte-exact (§2
intact) but **don't scroll**: `c_modifier_vertical_scroll`, `c_modifier_horizontal_scroll`.

## 2. Upstream model (`ScrollModifierOperation.java`)
- Extends `ListActionsOperation` (container) + `ScrollDelegate` + `TouchHandler`; holds a paired
  `TouchExpression` (positionally resolved from its child list).
- `position` = NaN-encoded float-id ref → the id the paired TE writes its output to.
- `paint`: eval children (incl. TE) → `position = getFloat(idFromNan(positionRef))` →
  `mScrollY = -min(maxScrollY, position)` (VERTICAL) / `mScrollX` (HORIZONTAL).
- `layout`: `loadFloat(idFromNan(max), maxScroll)` + `loadFloat(idFromNan(notchMax), contentDimension)`.
- `onTouchDown/Drag/Up`: forwards to the TE, offsetting pointer by current scroll (`y + mScrollY`) in the
  non-FIX_TOUCH_EVENT path.
- Content translate + clip is done by the scrollable **Component** reading `mScrollX/Y`; a `ClipRectModifier`
  bounds the window (present in the corpus doc).

## 3. Mapping onto our architecture (the key simplification)
In our player the paired TE is **already evaluated (S2-Core Phase-A) AND touch-driven (S2b `dispatchTouch`
notifies all TEs) every frame** — its output already sits in the float store under its output id. Therefore
**ScrollModifier does NOT re-own/re-eval the TE for the value**: it reads
`scroll = getFloat(idFromNan(position))`, then `offset = -clampToMax(scroll, resolvedMax)`. S3b is
**read + expose (dev-1) + apply (dev-2)**, not a re-eval.

## 4. Decode grounding — a/b/c RESOLVED (decode + both lanes concur)
Decoded `c_modifier_vertical_scroll`:
```
SCROLLMOD dir=0(VERTICAL) position=var(id=42) max=var(id=43) notchMax=var(id=44)
TOUCHEXPR id=42 stopMode=0(GENTLY) min=0.0 max=var(id=43) exp=[ TOUCH_POS_Y(14), -1.0, MUL ]
```
- **(a) TE binding via `position` id-ref — CONFIRMED (dev-1 decode + dev-2 concur).** `idFromNan(position)=42
  == TE.id=42` → `getFloat(idFromNan(position))` IS the TE output. No container/positional TE ownership for
  the value path. `max=id43`/`notchMax=id44` are **shared ids** with the TE (`TE.max=id43`) → layout
  `loadFloat(id43,…)`/`loadFloat(id44,…)` feed the TE's own clamp = clean id-based coupling, exactly upstream.
- **(b) Touch coords raw for MVP — CONFIRMED (dev-1 decode + dev-2: raw MVP, +mScrollY deferred).** TE exp =
  `-TOUCH_POS_Y`, `stopMode=0` (delta: `value = valueAtDown + (raw_now − raw_down)`) → a constant coord
  offset cancels in the delta → global raw doc-space dispatch (S2b) drives the scroll correctly for the
  corpus docs. The upstream `touchDrag(y + mScrollY)` content-space adjustment only matters when `mScrollY`
  changes *during* the drag (continuous/over-scroll precision) → **deferred refinement, not an MVP blocker.**
- **(c) content dimension source — RESOLVED (dev-2): from `LayoutMeasure`.** The scrollable component's
  measured content dimension feeds the layout `loadFloat(max=maxScroll, notchMax=contentDimension)`.

## 5. Cross-lane contract (FINAL — PO coordinates the merge order)
**dev-1 (ScrollModifier runtime — ADDITIVE, see §6):**
- `scrollOffset(context): Float = -clampToMax(getFloat(idFromNan(position)), getFloat(idFromNan(max)))` per
  `direction` (VERTICAL→Y, HORIZONTAL→X).
- Layout hook: `loadFloat(idFromNan(max), maxScroll)` + `loadFloat(idFromNan(notchMax), contentDimension)`,
  fed by dev-2's measured content dimension.

**dev-2 (layout/component walk):**
- supplies measured **contentDimension / maxScroll** (input to dev-1's layout loads) from `LayoutMeasure`;
- reads `ScrollModifier.scrollOffset` → **translates the scrollable component's children by the offset +
  clips** (the `ClipRectModifier` already bounds the window) during the layout/draw walk.

**🔑 Sequencing requirement (assist-added, MUST hold):** the layout `loadFloat(max/notchMax)` must run
**BEFORE** the Phase-A eval of the TE that frame — else the TE clamps against a stale/zero `max` on the first
frame (visible first-frame scroll glitch). Lock: layout-measure → loadFloat(max,notchMax) → eval-phase(TE)
→ scrollOffset read → apply. If our walk can't guarantee layout-before-eval, dev-1 must clamp defensively
(treat unset max as "no clamp" not "clamp to 0").

## 6. §2 / determinism (HARD GATE)
- **ScrollModifier `read`/`write`/`equals`/`hashCode` UNCHANGED** → 173 byte-conformance intact. New runtime
  is additive (`scrollOffset()` reads the store + a layout-side `loadFloat`), exactly the REM-124/TouchExpression
  pattern (body-level runtime, wire untouched). **A wire/equals/hashCode change here = blocked.**
- **Determinism:** scroll offset is live-only (driven by the live touch path). Static mode → TE output at
  default → offset stable → goldens deterministic (same invariant as S2/S2b). The layout `loadFloat(max/
  notchMax)` is value-deterministic (from measured dims, not touch) → safe in static too.

## 7. Slicing
- **S3a (independent, NO S2b dep):** notch-mode validation. Modes 3/4/5 already implemented in S2-Core
  `getStopPosition`. Tester validates `thumb_wheel1/2`, `demo_flick`, `stop_notches_even/percents/absolute`
  render/eval correctly — likely already green. No dev-1 code expected.
- **S3b (this spec, waits S2b test-1-GO):** ScrollModifier interactive per §3–§5. Corpus:
  `c_modifier_vertical_scroll`, `c_modifier_horizontal_scroll`.

## 8. Deferred (documented, NOT in S3b MVP)
- **(b-refinement) mScrollY-adjusted touch coords** (`touchDrag(y + mScrollY)`): needed only for continuous/
  over-scroll precision OR a non-delta-mode / scroll-offset-dependent TE expression. The corpus scroll docs
  are delta-mode → unaffected. Revisit if a corpus/Creation-DSL doc uses absolute-mode scroll.
- **Positional TE ownership** (routing touch through the ScrollModifier instead of global notify): only the
  enabler for the above; not needed for the value path.

## 9. Verification (the "done" bar)
- 173 byte-conformance + round-trip remain green (§6 — additive proof).
- `c_modifier_vertical_scroll` + `c_modifier_horizontal_scroll` **render-scroll on ≥1 target** (Maestro
  on-device/Chromium): a live drag moves the content within the clip window; static render = deterministic
  baseline (no scroll). This is the merge gate (assist GO necessary-not-sufficient; tester render is the proof).
- Determinism pin: static render of the two scroll docs == pre-S3b baseline (offset never applied without live touch).

## 10. assist verdict
**GO on the design.** The value-path simplification (read TE output from the store via the `position` id-ref
rather than re-evaluating) is sound and decode-grounded (id42==TE.id42); §2 is additive (wire untouched);
determinism preserved (live-only offset). a/b/c are resolved with both lanes concurring. Locked requirements:
§6 (no wire change — hard gate), §5 sequencing (layout-loads-max before eval, or defensive clamp). Deferred
items (§8) are correctly out of MVP and decode-justified. Impl waits S2b test-1-on-device-GO.
