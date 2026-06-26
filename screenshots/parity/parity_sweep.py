#!/usr/bin/env python3
"""parity_sweep — fährt parity_compare über alle Docs, die in BEIDEN Verzeichnissen liegen.

Usage:  parity_sweep.py <android_dir> <ios_dir> [--diff-out <dir>]
Aggregiert PASS/FAIL/ERROR + listet die Divergenzen (FAIL/ERROR) als Routing-Liste an den PO.
Stufe-B Cross-Platform-Paritäts-Zahl = passed / comparable.
"""
import os
import sys
import glob
from parity_compare import compare


def main():
    a_dir, i_dir = sys.argv[1].rstrip("/"), sys.argv[2].rstrip("/")
    diff = sys.argv[sys.argv.index("--diff-out") + 1] if "--diff-out" in sys.argv else None
    a = {os.path.basename(p) for p in glob.glob(f"{a_dir}/*.png")}
    i = {os.path.basename(p) for p in glob.glob(f"{i_dir}/*.png")}
    both = sorted(a & i)
    only_a, only_i = sorted(a - i), sorted(i - a)
    print(f"PARITY-SWEEP-BEGIN  android={len(a)} ios={len(i)} comparable={len(both)} "
          f"android-only={len(only_a)} ios-only={len(only_i)}")
    res = {"PASS": [], "FAIL": [], "ERROR": []}
    for name in both:
        doc = name[:-4]
        code = compare(f"{a_dir}/{name}", f"{i_dir}/{name}", doc, diff)
        res["PASS" if code == 0 else "FAIL" if code == 1 else "ERROR"].append(doc)
    print(f"PARITY-SWEEP-RESULT comparable={len(both)} PASS={len(res['PASS'])} "
          f"FAIL={len(res['FAIL'])} ERROR={len(res['ERROR'])}")
    if res["FAIL"]:
        print("DIVERGENCES (breach/cluster über Toleranz — echte Render-Unterschiede prüfen):", res["FAIL"])
    if res["ERROR"]:
        print("SIZE-MISMATCH (Layout/Density-Bug — kein AA-Toleranzfall):", res["ERROR"])
    if only_a:
        print(f"NUR-Android ({len(only_a)}, kein iOS-Render):", only_a[:20])
    if only_i:
        print(f"NUR-iOS ({len(only_i)}):", only_i[:20])
    print("PARITY-SWEEP-END")


if __name__ == "__main__":
    main()
