#!/bin/sh
# Regenerates every icon from tools/make_icons.py (macOS: needs sips + iconutil).
set -e
cd "$(dirname "$0")/.."
python3 tools/make_icons.py
SRC=tools/build/icon-512.png
SET=tools/build/icon.iconset
rm -rf "$SET" && mkdir -p "$SET"
for s in 16 32 128 256; do
  sips -z $s $s "$SRC" --out "$SET/icon_${s}x${s}.png" >/dev/null
  d=$((s * 2)); sips -z $d $d "$SRC" --out "$SET/icon_${s}x${s}@2x.png" >/dev/null
done
cp "$SRC" "$SET/icon_512x512.png"
iconutil -c icns "$SET" -o desktopApp/icon.icns
for s in 16 32 48 128; do sips -z $s $s "$SRC" --out "browser-extension/icons/$s.png" >/dev/null; done
echo "icons ok"
