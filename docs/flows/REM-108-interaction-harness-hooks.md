# REM-108 Interaction Harness — Required App-Hooks (test-1)

> **Status:** ✅ **CONFIRMED IN CONTRACT.** Both hooks are now first-class deliverables in the assist
> TechSpec `docs/TECHSPEC-REM-108-interaction-api.md` @d7ed33d §5 ("nicht beobachtbar = nicht im
> Lieferstandard"), wired NoOp-until-fed in S0 (dev-1). This note (test-1, 2026-06-30, Epic REM-154) is now
> the harness-side companion: exact testTag/format the staged flows assert against.
>
> **Precedent:** identical pattern to `rc-touch-echo` / `rc-frame-count` (REM-143 S3) — small testTag-
> surfaced render-only app-hooks (§2 null-write/read) so Maestro causally gates without pixel-diff (W9-safe).
>
> **🧪 Empirical probe (test-1, pre-S2 build @3c2ff54):** a `tapOn` does NOT move `rc-touch-echo` (TouchState
> is fed only by the wired `detectDragGestures`; the tap detector is unbuilt S2 work). ⇒ the click path has
> NO today-co-signal — `rc-action-echo` is the ONLY observation path, confirming why it's contract-§5
> first-class. The drag path's `rc-touch-echo` co-signal DOES work today (verified green).

---

## Why hooks are needed (the observability gap)

Both interaction axes mutate **non-rendered state** — there is no on-screen text bound to the changed
value, so a Maestro flow cannot see "it fired" without a surfaced hook:

| Axis | What changes on-device | Why it's not visible |
|---|---|---|
| **Click → Callback** (S4) | `VALUE_INTEGER_CHANGE_ACTION` mutates `DATA_INT(id=42)` (down→2, up→3, cancel→4) | The rendered text is the static label ("Press Down"); the int is never drawn. |
| **Drag → Scroll-Offset** (S3b) | `ScrollModifier` offset advances | Only the clipped content translates; offset value itself isn't surfaced. The visual delta needs pixel-diff (out-of-Maestro, W9-fragile). |

---

## Hook 1 — `rc-action-echo`  (for Click → Callback gate)

- **testTag:** `rc-action-echo` (surfaced via `Modifier.testTag` + `testTagsAsResourceId`, like rc-touch-echo).
- **Content:** ✅ **PO-LOCKED `"<valueId>=<value>"`** (Contract §4-Q2, 2026-06-30) of the most-recently-
  mutated DATA_INT, e.g. `"42=0"` at rest → `"42=2"` after a TOUCH_DOWN action fires. (Self-describing,
  robust for docs with multiple action-ints. Relayed to dev-1 for S0/S2 emission.)
- **Sentinel:** the int's init value (`"42=0"` for the corpus fixtures) before any action dispatches.
- **live-gating (REM-108 §0 linchpin):** in `&live=0` no action is dispatched → the hook MUST stay at the
  sentinel. This is what lets the harness assert the determinism pin (Goldens/173-conformance immune).
- **Updates when:** a touch-event modifier's `VALUE_INTEGER_CHANGE_ACTION` writes the float/int store.

## Hook 2 — `rc-scroll-offset`  (for Drag → Scroll gate)

- **testTag:** `rc-scroll-offset`.
- **Content (suggested):** the live `ScrollModifier` offset as a string, e.g. `"0.0"` at rest →
  `"-142.0"` after an upward drag. (Sign/units per the impl; the harness only asserts `!= "0.0"` + changed.)
- **Sentinel:** `"0.0"` at rest.
- **live-gating:** `&live=0` → MUST stay `"0.0"` (S3b already proved the render is live-gated, mad=0.000;
  this surfaces the same fact as a value the harness can assert without pixel-diff).
- **Updates when:** the ScrollModifier offset changes (drag / fling settle).

---

## What the harness already does WITHOUT the hooks (today)

`interaction_click_callback.yaml` + `interaction_drag_scroll.yaml` are staged and ALREADY assert, today,
via the existing `rc-touch-echo`:
- live render (rc-rendered + rc-doc + rc-draw-count>0, not blank),
- the gesture is **accepted on-device + reaches the canvas** (rc-touch-echo moves off `"0,0"`),
- **no crash** (rc-error absent, rc-rendered persists).

What stays **PENDING on the hooks** = the *causal value gate* (the action reached the expected int / the
drag advanced the offset) + the *static determinism pin* on the value. Those are the lines marked
`PENDING` (commented) in each flow — uncomment + drop the `staged` tag once the hook + dev-1's S4 impl land.

---

## Open contract question for assist (verify-before-route)

**Dispatch path for press events:** does S4 touch-event dispatch (DOWN/UP) reuse the `detectDragGestures`
node (which requires crossing touch-slop — a pure `tapOn` would NOT register, per the S2b root-cause), or a
separate press/tap detector? The click flow uses `tapOn` as primary with a short-swipe fallback noted; the
contract should pin which gesture a "click" is, so the gate uses the right Maestro primitive.
