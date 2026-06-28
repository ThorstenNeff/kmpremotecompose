# TECHSPEC DRAFT — REM-108 Epic-F S3b: ScrollModifier interactive (dev-1 draft → assist finalizes §7)

> **Status:** dev-1 design draft for assist review/finalization. **Design-only — no impl until S2b test-1-GO**
> (no stacking on unproven S2b). S3a (notch-mode validation) is independent — see end.
> Upstream grounded against `./androidx` (read-only reference; behavior, not paste — PROJECT_CONTEXT §5).

## 1. Problem

`ScrollModifier` (`MODIFIER_SCROLL` = 226) is currently `: Operation` only — a byte-faithful carrier
(`direction`, `position`, `max`, `notchMax`) with **no render effect**. So scroll docs decode + round-trip
byte-exact (§2 intact) but **don't scroll**: `c_modifier_vertical_scroll`, `c_modifier_horizontal_scroll`.

## 2. Upstream model (`ScrollModifierOperation.java`)

- Extends `ListActionsOperation` (a container) + implements `ScrollDelegate` + `TouchHandler`.
- Holds a paired **`TouchExpression`** (resolved positionally from its child op-list:
  `if (op instanceof TouchExpression) mTouchExpression = op`).
- `position` is a **NaN-encoded float-id ref** → the id the paired `TouchExpression` writes its output to.
- `paint(context)`:
  1. `for (op in mList) op.apply(ctx)` — evaluates the children (incl. the TouchExpression).
  2. `position = ctx.getFloat(idFromNan(mPositionExpression))`.
  3. `mScrollY = -min(mMaxScrollY, position)` (VERTICAL) / `mScrollX = -min(mMaxScrollX, position)` (HORIZONTAL).
- `layout(...)`: computes max from the content dimension; `loadFloat(idFromNan(mMax), max)` +
  `loadFloat(idFromNan(mNotchMax), contentDimension)`.
- `onTouchDown/Drag/Up(...)`: forwards to `mTouchExpression.touchDown/Drag/Up`, offsetting the pointer by
  the current scroll (`x + mScrollX`, `y + mScrollY`) in the non-FIX_TOUCH_EVENT path.
- The **content translate + clip** itself is done by the scrollable **Component** reading `mScrollX/mScrollY`
  (the `ScrollDelegate`); a `ClipRectModifier` bounds the visible window (present in the corpus doc).

## 3. Mapping onto OUR architecture (the key simplification)

In our player, **the paired `TouchExpression` is already evaluated AND touch-driven every frame** by
S2-Core (Phase-A eval) + S2b (`dispatchTouch` notifies *all* `TouchExpression`s). Its output is already in
the float store under its output id. Therefore:

- **ScrollModifier does NOT need to own/eval the TouchExpression for the VALUE.** It reads the scroll value
  straight from the store: `scroll = getFloat(idFromNan(position))`, then `offset = -clampToMax(scroll)`.
- Binding (open Q (a)) is cleanest **via the `position` id-ref**: the scroll value is whatever the TE wrote
  to `idFromNan(position)`. No container/positional TE ownership needed for the value path. (Positional
  ownership is only needed for the scroll-offset-adjusted touch coords — see §5 nuance.)

So S3b is mostly a **read + expose + (dev-2) apply**, not a re-eval.

## 4. Cross-lane contract (dev-1 ↔ dev-2) — PO to coordinate

**dev-1 (this slice):** `ScrollModifier` gains runtime behavior (additive; `read`/`write`/`equals`/`hashCode`
UNCHANGED → §2 intact):
- `scrollOffset(context): Float` = `-clampToMax(getFloat(idFromNan(position)), resolvedMax)` per `direction`.
- On layout: `loadFloat(idFromNan(max), maxScroll)` + `loadFloat(idFromNan(notchMax), contentDimension)`
  — **needs the content dimension from dev-2's layout** (input to this).
- (Optional refinement) touch-coord adjustment by current offset — see §5.

**dev-2 (layout/component walk):** the scrollable `LayoutComponent`:
- reads `ScrollModifier.scrollOffset` → **translates its children by the offset + clips** (the `ClipRectModifier`
  already bounds the window) during the layout/draw walk;
- supplies the **content dimension / maxScroll** to the modifier (input for §4 dev-1 layout loads);
- routes pointer down/drag/up that hit the scrollable component → the ScrollModifier/TouchExpression
  (today S2b notifies all TEs globally, which already covers the value; explicit routing is the §5 refinement).

## 5. Open design questions (for assist + dev-2 via PO)

- **(a) TE binding:** confirm the id-ref path (`position` NaN-id → TE output id) is sufficient for our flat
  decode (TE + ScrollModifier are sibling ops in our op-list, not nested). Expectation: yes — the value flows
  through the store. Positional ownership only matters for (b).
- **(b) scroll-offset-adjusted touch coords:** upstream forwards `touchDown(x + mScrollX, y + mScrollY)`.
  Our global `dispatchTouch` sends raw doc-space coords. For `c_modifier_vertical_scroll`'s TE expression,
  does the un-adjusted coord suffice (MVP) or must we offset? Verify against the doc's decoded expression;
  if needed, route touch through the ScrollModifier (positional TE ownership) instead of global notify.
- **(c) max/contentDimension source:** the exact dev-2 layout hook that yields content dimension for the
  `loadFloat(max/notchMax)`.

## 6. §2 / determinism

- No `write`/`read`/`equals`/`hashCode` change to `ScrollModifier` → **173 byte-conformance intact**.
- Scroll offset is live-only (driven by the live touch path); static mode → TE output at default → offset
  stable → goldens deterministic (same invariant as S2/S2b).

## 7. Slicing

- **S3a (independent, no S2b dep):** notch-mode validation. Modes 3/4/5 are **already implemented** in
  S2-Core `getStopPosition` (stops-array populated). A tester validates `thumb_wheel1/2`, `demo_flick`,
  `stop_notches_even/percents/absolute` render/eval correctly — likely already green. No dev-1 code expected.
- **S3b (this draft, waits S2b test-1-GO):** ScrollModifier interactive per §3–§4. Corpus:
  `c_modifier_vertical_scroll`, `c_modifier_horizontal_scroll`.

## 8. Corpus reach

Decode-grounded: `c_modifier_vertical_scroll` = ScrollModifier×1 + TouchExpression×1 + ClipRectModifier×1.
`thumb_wheel1`/`demo_flick` = pure TouchExpression (no ScrollModifier) → S3a/S2-Core territory.

## 9. Decode grounding — (a) & (b) ANSWERED (c_modifier_vertical_scroll)

Decoded the actual ops:
```
SCROLLMOD  dir=0(VERTICAL)  position=var(id=42)  max=var(id=43)  notchMax=var(id=44)
TOUCHEXPR  id=42  stopMode=0(GENTLY)  min=0.0  max=var(id=43)  exp=[ TOUCH_POS_Y(14), -1.0, MUL ]
```

- **(a) CONFIRMED — binding via `position` id-ref.** `idFromNan(position)=42` == `TouchExpression.id=42`.
  So `getFloat(idFromNan(position))` IS the TE's output → no container/positional TE ownership needed for the
  value path. Bonus: `max=id43`/`notchMax=id44` are **shared ids** between ScrollModifier and the TE
  (`TouchExpression.max=id43`), so the layout-side `loadFloat(id43, maxScroll)` / `loadFloat(id44, content)`
  feed the TE's own clamp — clean id-based coupling, exactly upstream.
- **(b) ANSWERED — raw coords suffice for MVP.** The TE expression is `-TOUCH_POS_Y` with `stopMode=0`
  (delta mode: `value = valueAtDown + (raw_now − raw_down)`). A constant coord offset cancels in the delta,
  so our global raw doc-space dispatch (S2b) drives the scroll correctly for the corpus doc. The upstream
  `touchDrag(y + mScrollY)` content-space adjustment only matters when `mScrollY` changes *during* the drag
  (continuous/over-scroll precision) → **refinement, not an MVP blocker.**

**Net:** S3b dev-1 work shrinks to: `ScrollModifier.scrollOffset(ctx) = -clamp(getFloat(idFromNan(position)),
getFloat(idFromNan(max)))` per direction, + layout `loadFloat(max, maxScroll)`/`loadFloat(notchMax, content)`
fed by dev-2's content dimension. dev-2 applies the offset (translate+clip). Only open item → (c) content-dim hook.
