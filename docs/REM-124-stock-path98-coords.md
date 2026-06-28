# REM-124 — Expected `stock` sparkline (path-98) coordinates (test-3 data-driven oracle)

**Owner:** dev-2 · **Fix commit:** `822fe75` (branch `bugfix/REM-124-stock`) · **Generated:** data-driven from `corpus/stock.rc` via the fixed render pipeline (doc-space, surface 256x256, density 1.0, static t=0).

## Why
REM-124 root cause: `ClipRect` (CLIP_RECT draw op) was not `VariableSupport`, so its computed bounds reached `clipRect()` as raw NaN -> a degenerate clip that hid the loop-built sparkline `DrawPath id=98`. Fix resolves the bounds (mirrors DrawRect). stock's data-oracle failed identically pre/post REM-121 because REM-121 fixed path-98's *accumulation* while the NaN clip still hid it.

## Scroll caveat (important for the oracle)
The sparkline canvas (128x260) sits inside a `ScrollModifier` container; at static scroll=0 it is **off-screen** in a full-doc 256x256 render, so a full-doc pixel golden cannot show it. Verify path-98 against these **doc/canvas-space** coordinates (scroll-independent), or render the canvas in isolation. `pts` are per-command endpoints (the polyline vertices), rounded to 2 decimals; `distinct == count` proves non-degenerate (the bug gave distinct=1).

## Expected (post-fix `822fe75`)

```
canvasDims: w(id91)=128.0 h(id92)=260.0
clipRect(resolved): (24.2,24.2,103.8,235.8)
path98: 92 pts, distinct=92, x[19.2..108.8] y[24.828934..240.8]
path98 pts=(19.2,240.8) (19.2,84.82) (20.2,75.29) (21.2,78.57) (22.2,66.47) (23.2,75.0) (24.2,87.85) (25.2,89.23) (26.2,79.58) (27.2,95.8) (28.2,66.77) (29.2,88.54) (30.2,109.79) (31.2,113.55) (32.2,110.67) (33.2,105.41) (34.2,102.07) (35.2,103.17) (36.2,113.34) (37.2,124.82) (38.2,124.96) (39.2,113.08) (40.2,107.14) (41.2,106.88) (42.2,97.6) (43.2,106.69) (44.2,98.75) (45.2,90.29) (46.2,75.36) (47.2,53.15) (48.2,78.08) (49.2,87.22) (50.2,98.58) (51.2,116.06) (52.2,119.01) (53.2,121.28) (54.2,126.21) (55.2,123.67) (56.2,130.66) (57.2,130.82) (58.2,132.73) (59.2,132.7) (60.2,121.6) (61.2,134.76) (62.2,123.1) (63.2,121.47) (64.19,116.89) (65.19,108.79) (66.19,113.21) (67.19,105.5) (68.19,102.52) (69.19,103.87) (70.19,99.11) (71.19,99.67) (72.19,104.61) (73.19,95.07) (74.19,92.12) (75.19,68.85) (76.19,32.01) (77.19,35.84) (78.19,24.82) (79.19,47.28) (80.19,68.61) (81.19,74.77) (82.2,70.43) (83.2,74.84) (84.2,73.83) (85.2,66.38) (86.2,64.08) (87.2,77.88) (88.2,64.64) (89.2,79.19) (90.2,67.67) (91.2,66.05) (92.2,64.16) (93.2,54.37) (94.2,54.09) (95.2,41.0) (96.2,41.61) (97.2,61.96) (98.2,61.33) (99.2,55.26) (100.2,95.57) (101.2,110.2) (102.2,117.21) (103.2,111.79) (104.2,104.42) (105.2,114.94) (106.2,115.18) (107.2,123.17) (108.2,125.19) (108.8,240.8)
```
