# TECHSPEC — REM-127: PathExpression render-apply (FINAL)

> **Status:** FINAL (assist-finalized from dev-2 scoping draft `TECHSPEC-REM-127-pathexpression-render-DRAFT.md`,
> commit `cdb2973`). Supersedes the DRAFT. Data-grounded vs the 12 corpus docs + upstream
> `./androidx` (behavior-reference, not paste — PROJECT_CONTEXT §5).
>
> **§2-safe by construction: render-only.** `PathExpression.write`/`read`/`equals`/`hashCode` and all
> wire layouts stay UNCHANGED → 173 byte-conformance intact. A binary-format change is out of scope.
>
> **De-risk verified against the tree (not assumed):** the consumer side already exists and the
> evaluator is one primitive short of complete. So REM-127 builds only the *producer*.

## 1. Problem
`PathExpression` (`PATH_EXPRESSION`) is `: Operation` only — a byte-faithful carrier
(`id, flags, min, max, count, expressionX[], expressionY[]`) with **zero render-apply** (census HIGH gap,
confirmed). The doc round-trips byte-exact but the path it describes is never evaluated / never loaded
→ the paired `DrawPath#id` finds no path-data → the curve/arc/clock-hand doesn't render. **12 docs**
(chart/plot/clock — the path is usually the *primary* content).

## 2. The leverage — build the PRODUCER only (verified)
Upstream `PathExpression implements VariableSupport` (a producer, same family as our `FloatExpression` /
`PathData` / `PathAppend` post-REM-121). Two in-tree facts make this cheap, both verified:

1. **Consumer already exists.** `DrawPath` + `PathDataResolver`/`FloatsToPath` render a `MOVE/CUBIC`
   marker path-data array from the store (REM-36/121). PathGenerator's output IS that marker format. So
   once `apply` loads the path-data under `id`, the existing draw path renders it — **no walk/adapter
   change.**
2. **Phase-A auto-runs producers.** `RemoteComposePlayer:132-134` (and :238 in loops) already does
   `if (op is VariableSupport) { op.updateVariables(ctx); op.apply(ctx) }`. So converting
   `PathExpression` to `VariableSupport` makes it run automatically — **no `RemoteComposePlayer`
   change** (cleaner than S3b). Producer-in, consumer-already-there.

**Evaluator is one primitive short (verified):** `RpnFloatEvaluator.eval(exp, len, context)` has **no
`t` parameter and no op#70 handling** → `VAR1` (op#70) is the single genuinely-new primitive. Every
other operator the 12 docs use is already present (§5 Q2).

## 3. Upstream model (behavior only — `PathExpression.java` + `PathGenerator.java`)
- **`updateVariables(ctx)`**: resolve `min`/`max`/`count` NaN-refs; resolve the **data-var NaN-refs
  inside** `expressionX[]`/`expressionY[]` (skip math-operator NaNs AND the `VAR1` operator) into
  resolved out-arrays. (Mirror our REM-36 resolve pattern.)
- **`apply(ctx)`**: sample `t = min + i·step`, `i in 0..count-1` (`step=(max−min)/(count−1)`, or `/count`
  when `LOOP`). Per sample eval `X(t)`, `Y(t)` via the RPN evaluator **with `t` injected as `VAR1`**;
  feed `(x[],y[])` into `PathGenerator` (SPLINE / LINEAR / POLAR) → a `MOVE/CUBIC` marker path;
  `putPathData(id, out)` + `putPathWinding(id, winding)`.
- **Flags:** `LOOP=0x1` · mode `=(flags & 0x6)` (0=SPLINE, 2=MONOTONIC, 4=LINEAR) · `POLAR=0x8` ·
  `winding=(flags & 0x3000000) >> 24`.

## 4. Data-grounded scope (decoded across 12 docs / ~39 instances — dev-2, accepted)
- **`VAR1` (op#70)** in every expressionX/Y → it IS sample-`t` → the one new evaluator primitive.
- **Modes:** SPLINE + LINEAR + POLAR present; **MONOTONIC corpus-ABSENT** → loud reach-guard, don't build.
- **Array-deref** (op#32 A_DEREF / op#38 A_SPLINE) in **0** path expressions → `ca == null` path only.
- **winding = 0 in all** → `putPathWinding(id, 0)`.
- **count** 20–300, literal (a few NaN-ref → getFloat).
- **operators used:** 1,2,3,4,6,9,10,18,19,26,45,70 — **all present except 70** (§5 Q2 verified).

## 5. Open-question decisions (verified in-tree)

**Q1 — VAR2+ (op#71…)? → MVP single-`t`, VAR2+ loud-guarded for free.** The corpus uses only VAR1. Add
`eval(exp, len, context, t: Float)` mapping `op#70 → t`. Do **not** build a general `vararg` var form
(YAGNI). VAR2+ (op#71+) is unhandled → falls through to the evaluator's existing
`else -> throw IllegalArgumentException` → **loud, not silent** (the reach-guard is free). If a future
doc needs VAR2, that throw is the signal to generalize.

**Q2 — operator-coverage (18/19/26/45)? → ALL present; no sub-add.** Verified in `RpnFloatEvaluator`:
op 18=`OP_SIN`, 19=`OP_COS`, **26=`OP_IFELSE`/ternary (NOT CBRT — the draft's CBRT label is wrong for
our op-numbering)**, 45=`OP_SQUARE`; plus 6=MIN, 9=SQRT, 10=ABS, 1-4=ADD/SUB/MUL/DIV. The full corpus
op-set is covered. **S1 still keeps a loud reach-guard** (the existing else-throw) so any operator in a
doc not decoded here surfaces loudly, never silently mis-evaluates.

**Q3 — PathGenerator output exactness? → HARD GATE: match the consumer's slot table, verify by
round-trip.** The contract is `PathDataResolver.commandLength` (the REM-121 single source):
`MOVE=10` → 3 slots (marker + x,y), `CUBIC=14` → 9 slots (marker + 8 floats). **PathGenerator MUST emit
markers + slot-counts that this exact table consumes** — same marker values AND same per-command float
layout. ⚠️ `CUBIC=9` means **8** floats follow the marker (not the bare 6 of a control-point-only
cubic) → the producer must emit whatever those 8 are. **Do not assume the layout — decode an existing
`DATA_PATH` cubic segment (e.g. from `procedure_text_path_effects`, which carries a real DATA_PATH) and
match `PathGenerator`'s output to it.** S2 gate: a producer→consumer round-trip test
(`PathGenerator` output → `PathDataResolver.resolvePathData` walks it with no leftover/misaligned slots)
**before** any render proof. This is the make-or-break de-risk: a slot-layout divergence renders garbage.

**Q4 — pathChanged / per-frame? → per-frame re-eval for MVP (mirrors REM-36 E1).** No dirty-tracking;
every VariableSupport re-evaluates each frame already. 🟡 **Perf flag (non-blocking):** up to 300
samples × 2 RPN evals × per-frame × 12 docs could cost frames; if a render-sweep shows frame-rate
impact, add upstream's `pathChanged` dirty-tracking (re-sample only when min/max/count or a referenced
var changes). MVP-acceptable; track as a forward perf optimization.

**Q5 — POLAR convention? → lock to upstream `getPolarPath` EXACTLY, decode-verify against a corpus
POLAR doc.** Do **not** guess the `(r,θ)` mapping. Derive it from upstream `getPolarPath` (behavior-ref)
and **verify** by generating the polar path for a corpus POLAR doc (`clock` ids 63/74, or a
`demo_path_expression_path_test`) and comparing sampled endpoints to test-3's data-oracle before the
render proof. POLAR center/units come from the doc, not a platform default.

## 6. Impl slices (for dev-2)
1. **S1 — `RpnFloatEvaluator` t-overload.** Add `eval(exp, len, context, t)` mapping `op#70(VAR1) → t`;
   the existing 3-arg `eval` stays **byte/behavior-untouched** (existing FloatExpression/TouchExpression
   callers must not shift — blast-radius lock §7). Keep the loud reach-guard. *Smallest, highest-leverage.*
2. **S2 — `PathGenerator` port** (`player/core/PathGenerator.kt`): `Spline` + `Linear` (`asPath(x,y,loop)`
   → MOVE/CUBIC marker array) + `getPolarPath` + `getReturnLength`. **MONOTONIC = loud-guarded stub.**
   **Q3 round-trip gate** (output ↔ `PathDataResolver`) is part of this slice.
3. **S3 — `PathExpression : VariableSupport`.** `updateVariables` (resolve min/max/count + data-var-refs
   in X/Y, skipping operator + VAR1 NaNs) + `apply` (sample → PathGenerator → `putPathData(id,out)` +
   `putPathWinding(id, winding)`). Decode flags (§3). `write`/`read`/`equals`/`hashCode` **untouched**.
4. **S4 — verify.** No player change. Render-proof the 12 docs (desktop, test-3) + full-sweep
   **0-collateral** + conformance 173/173 byte-exact.

## 7. §2 / §6 / blast-radius (review-enforced)
- **Render-only:** `PathExpression` wire (`write`/`read`/`equals`/`hashCode`) UNCHANGED → 173 intact by
  construction. commonMain diff to PathExpression must be **only** `: VariableSupport` +
  additive `updateVariables`/`apply`.
- **Evaluator overload is ADDITIVE:** the new 4-arg `eval(...,t)` must not alter the existing 3-arg
  `eval` — a regression there would shift *other* docs' FloatExpression/TouchExpression output. Verify
  via conformance + the eval being a separate overload only called by `PathExpression.apply`.
- **Blast-radius:** converting PathExpression to VariableSupport makes it run in Phase-A. Non-PathExpression
  docs are unaffected (no op). The 12 PathExpression docs flip blank/degenerate→curve. **Full-sweep MUST
  show only the ≤12 PathExpression docs change, 0 collateral** (heuristic #3 — prove the non-target path
  is unchanged, not just that the feature works).
- **Loud-not-silent guards** (D1/D5): MONOTONIC mode, VAR2+ (op#71+), array-deref (op#32/38) all reach a
  clear throw, never a silent approximation.

## 8. Verification (the "done" bar)
- Conformance 173/173 byte-exact (render-only proof).
- **Q3 round-trip green** (PathGenerator output ↔ PathDataResolver, exact slot match) — gates S2.
- **POLAR decode-verified** against a corpus polar doc (Q5) before render proof.
- Per-doc render proof (test-3 data-oracle, like REM-121/124/125): the ≤12 docs go
  blank/degenerate→curve; **full-sweep 0-collateral**. assist GO is necessary-not-sufficient — the
  visible-degenerate→curve proof per doc is the test-3 render-sweep.

## 9. Visual scope (test-3 data-oracle scopes FINAL)
All 12 are chart/plot/clock where the path is primary → high prior for visible-degenerate. Likely-most-
visible: `demo_graphs0`, `linear_regression`, `plot2/3/4`, `plot_wave`, `themed_plot1`,
`demo_path_expression_path_test1/2/3`, `paths_demos`; `clock` = polar decorative (ids 63/74). test-3
supplies expected sampled-path endpoints per doc.

## 10. assist verdict
**GO on the design.** Producer-only (consumer + Phase-A auto-run already exist, both verified in-tree)
makes this the cleanest render-backlog item — no player/walk change, §2 render-only. The de-risk is real:
the evaluator is one primitive (VAR1) short, every other corpus operator is present, and the marker
contract is the REM-121 single-source table. Open questions resolved: Q1 single-`t` + free VAR2-loud-guard,
Q2 coverage complete (the CBRT worry was a numbering misread), Q3 elevated to a **hard round-trip gate with
decode-verify** (the one place garbage could slip in), Q4 per-frame MVP + perf flag, Q5 lock-to-upstream +
corpus-decode-verify for POLAR. Loud-guards for MONOTONIC/VAR2/array-deref are correct. Blast-radius lock:
full-sweep 0-collateral + the eval-overload-additive guard. MVP is ungated — S1 can start.
