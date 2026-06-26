#!/usr/bin/env python3
"""parity_sweep — parity_compare über alle Docs in BEIDEN reference/{android,ios}/-Dirs.
Aggregiert PASS / TEXT (diffuse Font/AA, akzeptabel-pending-Review) / FAIL (strukturell) / ERROR (size).
Parität-Zahl = (PASS+TEXT) / comparable; TEXT + FAIL separat gelistet.
Usage: parity_sweep.py <android_dir> <ios_dir> [--diff-out <dir>]
"""
import os, sys, glob
from parity_compare import compare

def main():
    a_dir, i_dir = sys.argv[1].rstrip("/"), sys.argv[2].rstrip("/")
    diff = sys.argv[sys.argv.index("--diff-out") + 1] if "--diff-out" in sys.argv else None
    a = {os.path.basename(p) for p in glob.glob(f"{a_dir}/*.png")}
    i = {os.path.basename(p) for p in glob.glob(f"{i_dir}/*.png")}
    both = sorted(a & i); only_a, only_i = sorted(a - i), sorted(i - a)
    print(f"PARITY-SWEEP-BEGIN android={len(a)} ios={len(i)} comparable={len(both)} android-only={len(only_a)} ios-only={len(only_i)}")
    res = {"PASS": [], "TEXT": [], "FAIL": [], "ERROR": []}
    for name in both:
        v = compare(f"{a_dir}/{name}", f"{i_dir}/{name}", name[:-4], diff)
        res.setdefault(v, []).append(name[:-4])
    comp = len(both); ok = len(res["PASS"]) + len(res["TEXT"])
    pct = (100.0 * ok / comp) if comp else 0.0
    print(f"PARITY-SWEEP-RESULT comparable={comp} PASS={len(res['PASS'])} TEXT={len(res['TEXT'])} "
          f"FAIL={len(res['FAIL'])} ERROR={len(res['ERROR'])} | paritaet={ok}/{comp} ({pct:.1f}%)")
    if res["TEXT"]:  print("TEXT-DIVERGENZ (diffus, Font/AA erwartet — Heatmap zur Bestätigung):", res["TEXT"])
    if res["FAIL"]:  print("STRUKTURELL (konzentriert — echter Render-Unterschied, Heatmap prüfen):", res["FAIL"])
    if res["ERROR"]: print("SIZE-MISMATCH (>2.25x, Layout/Density-Bug):", res["ERROR"])
    if only_i:       print(f"NUR-iOS ({len(only_i)}):", only_i[:20])
    if only_a:       print(f"NUR-Android (kein iOS-Render): {len(only_a)}")
    print("PARITY-SWEEP-END")

if __name__ == "__main__":
    main()
