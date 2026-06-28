# TECHSPEC — REM-127: PathExpression render-apply (SCOPING DRAFT, dev-2)

> **Status:** DRAFT (dev-2 scoping, data-grounded vs the 12 corpus docs + upstream `./androidx`
> behavior — PROJECT_CONTEXT §5, behavior-not-paste). → assist finalizes TechSpec → impl slices.
> **§2-safe by construction: render-only, no wire/encoding change — the `.rc` is invariant.**

## 1. Problem
`PathExpression` (`PATH_EXPRESSION`) is `: Operation` only — a byte-faithful carrier (`id`, `flags`,
`min`, `max`, `count`, `expressionX[]`, `expressionY[]`) with **ZERO render-apply path** (census-confirmed
HIGH gap). The doc decodes + round-trips byte-exact (§2 intact) but the path it describes is **never
evaluated and never loaded into the store** → the paired `DrawPath#id` finds no path-data → the
path-driven curve/arc/hand **does not render**. 12 docs affected (all chart/plot/clock — the curve is
typically the *primary* content).

## 2. Upstream model (`PathExpression.java` + `PathGenerator.java`, behavior only)
`PathExpression implements VariableSupport` — a **producer** (same family as our `FloatExpression`,
`PathData`/`PathAppend` post-REM-121):
- **`updateVariables(ctx)`**: resolve `min`/`max`/`count` NaN-refs; resolve the NaN **var-refs inside**
  `expressionX[]`/`expressionY[]` (skip math-operator NaNs) into resolved out-arrays. Mark `pathChanged`.
- **`apply(ctx)`** (only if `pathChanged`): sample `t = min + i·step` for `i in 0..count-1`
  (`step = (max−min)/(count−1)`, or `/count` when LOOP). For each sample eval `X(t)`, `Y(t)` via the RPN
  evaluator with **`t` injected as the `VAR1` operator**; feed the sampled `(x[],y[])` into a
  `PathGenerator` (SPLINE / LINEAR / MONOTONIC; or polar→cartesian for POLAR) → an output path of
  `MOVE/CUBIC` NaN-markers; `loadPathData(id, winding, outputPath)`.
- `DrawPath#id` (already working post-REM-121) then renders the loaded path-data.

**Flags (`PathExpression.java`):** `LOOP=0x1` · mode `=(flags & 0x6)` (0=SPLINE, 2=MONOTONIC, 4=LINEAR)
· `POLAR=0x8` · `winding=(flags & 0x3000000) >> 24`.

## 3. The key leverage (our architecture)
We **already** have the consumer side: `DrawPath` + `PathDataResolver`/`FloatsToPath` render a
`MOVE/CUBIC`-marker path-data array from the store (REM-36/121). PathGenerator's output is exactly that
marker format. **So REM-127 = build the PRODUCER only.** Once `PathExpression.apply` loads the path-data
under `id`, the existing draw path renders it — no walk/adapter change.

`PathExpression` becomes a `VariableSupport`, so the player's existing **Phase-A loop
(`updateVariables` + `apply` on every `VariableSupport`)** runs it automatically — **no
`RemoteComposePlayer` change** (unlike S3b). Producer-in, consumer-already-there.

## 4. Data-grounded scope (decoded across the 12 docs — ~39 PathExpression instances)
| Fact | Finding → scope impact |
|---|---|
| **`VAR1` (op#70)** | in **every** expressionX/Y → it IS the sample-`t`. **The one essential new evaluator primitive.** |
| **Modes** | SPLINE (most) + LINEAR (demo_graphs0, linear_regression, plot_wave, some tests) + POLAR (clock, path tests, paths_demos, plot4). **MONOTONIC = corpus-ABSENT** → defer behind a loud reach-guard (D1/D5 discipline), don't build now. |
| **Array-deref** | op#32 (A_DEREF) / op#38 (A_SPLINE) appear in **0** path expressions → **CollectionsAccess not needed** → port the `ca == null` path only (big simplification). |
| **winding** | **0 in all** → `putPathWinding(id, 0)` (trivial; no winding-mode work). |
| **count** | 20–300 samples; literal (a few NaN-ref) → resolve via getFloat. |
| **operators used** | 1,2,3,4 (ADD/SUB/MUL/DIV), 6,9,10,18,19,26,45,70 → mostly MVP (REM-104/109). **Coverage-check needed** for 18/19/26/45 (26≈CBRT was flagged non-MVP) — any gap = a small evaluator add, surfaced loud not silent. |

## 5. Impl slices (proposed)
1. **S1 — RpnFloatEvaluator: `VAR1` + t-parameterized eval.** Add an `eval(exp, len, ctx, t)` overload
   that substitutes `VAR1`(op#70) with `t` on the stack. Audit the op-set the 12 docs use (§4) and add
   any genuinely-missing operator (loud reach-guard, no silent fail). *Smallest, highest-leverage slice.*
2. **S2 — `PathGenerator` port** (new `player/core/PathGenerator.kt`): `Spline` + `Linear` 2D path
   builders (`asPath(x[],y[],loop)` → MOVE/CUBIC marker array) + `getPolarPath` (polar sample→cartesian)
   + `getReturnLength`. **MONOTONIC = loud-guarded stub** (corpus-absent). Output = our DrawPath marker
   format (cross-check vs `PathDataResolver`/`FloatsToPath` markers MOVE=10/CUBIC=14).
3. **S3 — `PathExpression` → `VariableSupport`.** `updateVariables` (resolve min/max/count + var-refs in
   X/Y arrays, skipping operator/VAR1 NaNs — mirror upstream + our REM-36 pattern) + `apply` (sample →
   PathGenerator → `putPathData(id, out)` + `putPathWinding(id, winding)`). Decode flags (§2). `write`/
   `read` **untouched**.
4. **S4 — verify.** No player change needed (Phase-A runs the new VariableSupport). Render-proof the 12
   docs (desktop) + full-sweep diff (expect: only the 12 flip BLANK/degenerate→curve, 0 collateral) +
   conformance 173/173 byte-exact.

## 6. §2 / §6 safety
Render-only. `PathExpression.write`/`read` and all wire layouts unchanged → 173-byte-conformance intact.
A binary-format change is explicitly **out of scope**. Gate per §6: conformance byte-exact + per-doc
render proof (not dispatch-count) + full-sweep 0-collateral.

## 7. Visual scope (necessary-not-sufficient — test-3 data-oracle scopes FINAL)
All 12 are chart/plot/clock where the path is primary content → **high prior for visible-degenerate**
(blank or missing curve), not merely dispatch-affected. Likely-most-visible (curve = the content):
`demo_graphs0`, `linear_regression`, `plot2`, `plot3`, `plot4`, `plot_wave`, `themed_plot1`,
`demo_path_expression_path_test1/2/3`, `paths_demos`. `clock` = polar decorative paths (id 63/74).
**test-3 per-doc data-oracle (like REM-121/124/125) confirms which are visibly degenerate** before/after
— I supply expected sampled-path endpoints per doc on request.

## 8. Open questions for assist (TechSpec finalization)
- **VAR2+ (op#71…)?** Corpus uses only VAR1 — confirm the eval overload needs only `var[0]`, or build the
  general `float... var` form for forward-safety.
- **Operator-coverage audit** (18/19/26/45): are all in our `RpnFloatEvaluator`? Any gap = an S1 sub-add.
- **PathGenerator output exactness:** confirm Spline/Linear/polar emit the *same* MOVE/CUBIC byte-marker
  structure our `DrawPath` consumes (the REM-121 single-source `commandLength` table is the contract).
- **`pathChanged`/per-frame:** MVP can re-evaluate every frame (no dirty tracking, mirrors our E1 MVP);
  confirm acceptable vs caching.
- **POLAR center/units:** confirm polar `(r=X(t), θ=Y(t)?)` convention vs upstream `getPolarPath` exactly.
