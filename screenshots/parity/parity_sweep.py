#!/usr/bin/env python3
"""parity_sweep — parity_compare über alle Docs in BEIDEN reference-Dirs (android|desktop vs ios|...).
Buckets: PASS (clean) / TEXT (diffuse Font/AA/thin-line) / BLANK (beide blank, nicht gerendert) /
FAIL (struktureller Defekt) / ERROR (size). Render-Parität = (PASS+TEXT)/(comparable-BLANK).

--no-resize (REM-78): keine Density-Resize-Heuristik (LANCZOS) — fuer den Desktop-Sweep, der bereits
density=1.0 doc-native-px ist und gegen iOS-Goldens vergleicht, die ebenfalls native-px sind. ≤2px
dim-Diff = beidseitig auf min-Dim croppen; >2px = ERROR.

Usage: parity_sweep.py <a_dir> <b_dir> [--diff-out <dir>] [--no-resize]
"""
import os, sys, glob
from parity_compare import compare

def main():
    a_dir, i_dir = sys.argv[1].rstrip("/"), sys.argv[2].rstrip("/")
    diff = sys.argv[sys.argv.index("--diff-out") + 1] if "--diff-out" in sys.argv else None
    no_resize = "--no-resize" in sys.argv
    a = {os.path.basename(p) for p in glob.glob(f"{a_dir}/*.png")}
    i = {os.path.basename(p) for p in glob.glob(f"{i_dir}/*.png")}
    both = sorted(a & i); only_a, only_i = sorted(a - i), sorted(i - a)
    label_a = os.path.basename(a_dir); label_b = os.path.basename(i_dir)
    print(f"PARITY-SWEEP-BEGIN {label_a}={len(a)} {label_b}={len(i)} comparable={len(both)} "
          f"{label_b}-only={len(only_i)}" + (f" (no-resize)" if no_resize else ""))
    res = {"PASS": [], "TEXT": [], "BLANK": [], "FAIL": [], "ERROR": []}
    for name in both:
        res[compare(f"{a_dir}/{name}", f"{i_dir}/{name}", name[:-4], diff, no_resize=no_resize)].append(name[:-4])
    comp = len(both)
    rendered = comp - len(res["BLANK"])
    ok = len(res["PASS"]) + len(res["TEXT"])
    pct = (100.0 * ok / rendered) if rendered else 0.0
    print(f"PARITY-SWEEP-RESULT comparable={comp} PASS={len(res['PASS'])} TEXT={len(res['TEXT'])} "
          f"BLANK={len(res['BLANK'])} FAIL={len(res['FAIL'])} ERROR={len(res['ERROR'])} | "
          f"render-paritaet={ok}/{rendered} ({pct:.1f}%, BLANK ausgeschlossen)")
    if res["BLANK"]: print(f"BLANK-BOTH (beide blank, NICHT gerendert — kein Render-Beweis) [{len(res['BLANK'])}]:", res["BLANK"])
    if res["TEXT"]:  print(f"TEXT (diffus, Font/AA/thin-line — Heatmap-Beleg) [{len(res['TEXT'])}]:", res["TEXT"])
    if res["FAIL"]:  print(f"STRUKTURELL (solider Block — echter Render-Defekt) [{len(res['FAIL'])}]:", res["FAIL"])
    if res["ERROR"]: print(f"SIZE-MISMATCH (>2.25x) [{len(res['ERROR'])}]:", res["ERROR"])
    if only_i:       print(f"NUR-iOS [{len(only_i)}]:", only_i[:20])
    print("PARITY-SWEEP-END")

if __name__ == "__main__":
    main()
