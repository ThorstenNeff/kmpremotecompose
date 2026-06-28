#!/usr/bin/env bash
# web_sweep_driver.sh — REM-79/A2 → REM-80/C7: treibt den 173-Doc Web-Render-Sweep (wasmJs/chromium)
# und verdiktet ihn mit DEMSELBEN Stack wie mobil (screenshots/parity/parity_sweep.py). test-2.
#
# 🔴 ARMT ERST, wenn die Web-Shell sweepbar ist (test-2 verifiziert 2026-06-28 — alle drei offen):
#   W3  webApp/main.kt ruft RemoteComposeApp (statt CMP-Template App()) — sonst rendert kein Doc.
#   C5  Korpus-`.rc` async im Browser ladbar (dev-1 WIP) — aktuell 0 .rc im web-dist.
#   W1  ?rc=<doc> URL-Router (window.location → RcRouter) — sonst nur DEFAULT_DOC.
# W2 (DOM-data-rc-Marker) IST gemergt (REM-83) → docs/flows/web_render_sweep.yaml gated darauf.
#
# Bis dahin: Scaffold + Vor-Verdrahtung. Liveness zuerst via web_default_smoke.yaml (braucht nur W3+C5).
#
# Usage: web_sweep_driver.sh [BASE_URL] [BASELINE_DIR]
#   BASE_URL      default http://localhost:8080  (./gradlew :webApp:wasmJsBrowserDistribution dann
#                 python3 -m http.server 8080 -d webApp/build/dist/wasmJs/productionExecutable)
#   BASELINE_DIR  default screenshots/reference/android  (die bewiesene Mobile-FULL/PARTIAL/BLANK-Baseline)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="${1:-http://localhost:8080}"
BASELINE="${2:-$ROOT/screenshots/reference/android}"
CORPUS="$ROOT/shared/src/commonTest/resources/rc-corpus/corpus"
FLOW="$ROOT/docs/flows/web_render_sweep.yaml"
OUT="$ROOT/screenshots/web"
mkdir -p "$OUT"

# Image-Doc-Verdacht-Bucket ZUERST + separat (REM-80-Jira-Survey): Divergenz HIER = erwartete
# wasm-Decode-Klasse; Divergenz in den übrigen ~159 (reine Geo/Text) = echter Bug, höhere Severity.
IMAGE_BUCKET=(
  c_image demo_bitmap_drawing_bit_draw1 demo_bitmap_drawing_bit_draw2 hostile_actor1
  hostile_actor1_c impulse_demo_confetti_demo particle stock texture_demo_basic_texture
  texture_demo_texture_clock texture_demo_texture_clock_test wake_demo_wake_clock
  digital_clock1 shader_calendar
)

# Kanonischer Frame-Pin für die Uhr/Animations-Docs (REM-62, 10:10:30). Leer = static t=0.
declare -A PIN
for d in clock clock_demo1 clock_demo2_jancy jclock2 fancy_clock2 fancy_clocks_1 \
         fancy_clocks_2 fancy_clocks_3 experimental_fancy_clock simple_clock_fast \
         simple_clock_slow digital_clock1; do PIN[$d]=36630; done

run_one() {  # $1 = docName
  local rc="$1" t="${PIN[$1]:-}"
  echo "WEBSWEEP capture rc=$rc t=${t:-0}"
  # MCP-Variante: maestro run device_id=chromium files=[$FLOW] env={RC,BASE,T}. CLI-Form:
  maestro test --device chromium \
    --env RC="$rc" --env BASE="$BASE" --env T="$t" \
    "$FLOW" || echo "WEBSWEEP FAIL-CLOSED rc=$rc (kein honest-render / doc-mismatch / error)"
  # Maestro legt den Screenshot unter seinem Run-Dir ab → nach $OUT/$rc.png einsammeln (Maestro-Version-abh.).
}

names() {  # Bucket zuerst, dann der Rest alphabetisch — beide aus dem realen Korpus gefiltert.
  local all; all="$(cd "$CORPUS" && ls *.rc 2>/dev/null | sed 's/\.rc$//')"
  for d in "${IMAGE_BUCKET[@]}"; do grep -qx "$d" <<<"$all" && echo "$d"; done
  comm -23 <(sort <<<"$all") <(printf '%s\n' "${IMAGE_BUCKET[@]}" | sort)
}

echo "WEBSWEEP-BEGIN corpus=$(ls "$CORPUS"/*.rc | wc -l | tr -d ' ') base=$BASE baseline=$BASELINE"
while read -r d; do [ -n "$d" ] && run_one "$d"; done < <(names)

# ── Crop-Kalibrierung (test-2 empirisch verifiziert 2026-06-28, chromium gg. realen Render):
#    Web rendert das Doc bei SCALE=1.0, top-left-anchored (das Doc-Pixelmaß == Mobile-Golden-Maß).
#    Der ComposeViewport-<canvas>-Capture ist der ganze Browser-Viewport (z.B. 1200x780) mit dem Doc
#    oben-links + Debug-testTags unten-links. → je Capture auf (0,0, golden_w, golden_h) croppen (das
#    schneidet Doc-nativ aus + schließt die Debug-Text-Band-Pixel aus). Validiert: simple1 (600x600)
#    + simple2 (300x300) → PASS vs Android+iOS. KEIN Resize nötig (Web-Doc-nativ == Golden-Maß).
# Browser-Viewport-Größe des Maestro-chromium-Captures (empirisch 1200x780). Docs, deren Golden
# GRÖSSER ist, werden vom Viewport GECLIPPT → Crop wäre ein schwarz-gepaddetes Artefakt, KEIN
# Render-Defekt. Solche Docs werden ausgesondert (→ $OUT/_oversized/) + geloggt, NICHT false-FAILt.
# (test-2-Befund 2026-06-28: digital_clock1 500x1500, shader_calendar 1000x2400 > 780h-Viewport.)
VIEWPORT_W=1200; VIEWPORT_H=780
echo "WEBSWEEP-CROP auf Doc-nativ (0,0,golden_w,golden_h); oversize→_oversized ──────"
mkdir -p "$OUT/_oversized"
python3 - "$OUT" "$BASELINE" "$VIEWPORT_W" "$VIEWPORT_H" <<'PY'
import sys, os, glob, shutil
from PIL import Image
out, baseline, vw, vh = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
for p in glob.glob(os.path.join(out, "*.png")):
    name = os.path.basename(p)
    ref = os.path.join(baseline, name)
    if not os.path.exists(ref):
        continue  # kein Golden → parity_sweep überspringt es ohnehin
    gw, gh = Image.open(ref).size
    if gw > vw or gh > vh:  # Doc größer als Capture-Viewport → Clip-Artefakt, aussondern
        shutil.move(p, os.path.join(out, "_oversized", name))
        print(f"  OVERSIZE {name}: golden ({gw},{gh}) > viewport ({vw},{vh}) → ausgesondert (braucht höheren Viewport)")
        continue
    im = Image.open(p).convert("RGB")
    if im.size != (gw, gh):
        im.crop((0, 0, gw, gh)).save(p)
        print(f"  crop {name}: {im.size} -> ({gw},{gh})")
PY

echo "WEBSWEEP-VERDICT (Web vs Mobile-Baseline) ──────────────"
python3 "$ROOT/screenshots/parity/parity_sweep.py" "$OUT" "$BASELINE" \
  --diff-out "$ROOT/screenshots/web_diff"
echo "WEBSWEEP-END"
