#!/usr/bin/env python3
"""
parity_compare — cross-platform render parity (Part B of the §6 render gate).

Maestro's assertScreenshot compares ONE run vs ONE golden per platform (render_parity.yaml,
Part A). This harness is Part B: a direct Android<->iOS pixel diff of the rc-canvas-cropped
renders, within a tolerance band (Skia on both, but Android-Canvas vs CMP-iOS-Skiko diverge at
sub-pixel/AA edges, so exact equality is not expected — a tolerance band is).

Usage:
    python3 parity_compare.py <imgA.png> <imgB.png> [--pixel-tol 16] [--match-pct 99.5] [--mask-top 0]

Exit 0 if match% >= --match-pct, else 1. Prints match% and first-divergence stats.
Owner: test-1 (QA). Goldens live under screenshots/reference/<platform>/<rc>.png.

--mask-top <px> (REM-166): exclude the top N rows of BOTH images from the comparison. For **tall docs whose
canvas starts at screen y=0** the OS **status bar** (clock/icons; iOS dynamic island) is composited over the
top of the rendered canvas — a non-deterministic, platform-divergent strip that is NOT part of the doc render.
shader_calendar (the only such corpus doc) is compared with `--mask-top 160` so the bar is excluded; the top
calendar row it occludes is validated on Desktop (no OS chrome). The render itself is left untouched (no
crop/shift) — only the comparison skips the strip.
"""
import sys
from PIL import Image

def parity(a_path, b_path, pixel_tol=16, match_pct=99.5, mask_top=0):
    a = Image.open(a_path).convert("RGB")
    b = Image.open(b_path).convert("RGB")
    if a.size != b.size:
        b = b.resize(a.size)  # normalize (different device densities) before comparing
    ap, bp = a.load(), b.load()
    W, H = a.size
    y0 = max(0, min(mask_top, H))  # rows [0, y0) excluded = the OS status-bar strip (REM-166)
    match = 0
    for y in range(y0, H):
        for x in range(W):
            if all(abs(ap[x, y][k] - bp[x, y][k]) <= pixel_tol for k in range(3)):
                match += 1
    denom = W * (H - y0)
    pct = 100.0 * match / denom if denom else 100.0
    ok = pct >= match_pct
    masked = f", mask_top={y0}" if y0 else ""
    print(f"parity {a_path} <-> {b_path}: {pct:.3f}% match "
          f"(pixel_tol={pixel_tol}, gate>={match_pct}%{masked}) -> {'PASS' if ok else 'FAIL'}")
    return ok

if __name__ == "__main__":
    args = [x for x in sys.argv[1:] if not x.startswith("--")]
    opts = {sys.argv[i].lstrip("-").replace("-", "_"): sys.argv[i + 1]
            for i in range(len(sys.argv)) if sys.argv[i].startswith("--")}
    ptol = int(opts.get("pixel_tol", 16))
    mpct = float(opts.get("match_pct", 99.5))
    mtop = int(opts.get("mask_top", 0))
    sys.exit(0 if parity(args[0], args[1], ptol, mpct, mtop) else 1)
