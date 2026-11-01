#!/bin/bash
# Builds the Linux packages (.deb + portable .tar.gz) into dist/linux with SHA256 checksums.
# jpackage builds for the system it runs on, so this only works on Linux (dpkg-deb + fakeroot).
# Runs on hostinger, see tools/release-linux.sh.
#
#   ./tools/build-linux.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[ "$(uname -s)" = Linux ] || { echo "Dieses Skript baut nur unter Linux." >&2; exit 1; }

VERSION="$(sed -n 's/^app = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
ARCH="$(dpkg --print-architecture 2>/dev/null || uname -m)"
OUT_DIR="dist/linux"
APP_DIR="desktopApp/build/compose/binaries/main/app"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "==> Tests, DEB und portables Paket ($VERSION)..."
./gradlew --console=plain :shared:desktopTest :desktopApp:packageDeb :desktopApp:createDistributable

DEB="$(ls desktopApp/build/compose/binaries/main/deb/*.deb | head -n 1)"
mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*
cp "$DEB" "$OUT_DIR/MetalRat_${VERSION}_linux-${ARCH}.deb"
tar -C "$APP_DIR" -czf "$OUT_DIR/MetalRat_${VERSION}_linux-${ARCH}.tar.gz" MetalRat
(cd browser-extension && python3 -m zipfile -c "../$OUT_DIR/MetalRat_${VERSION}_browser-extension.zip" manifest.json background.js icons)
(cd "$OUT_DIR" && sha256sum * > SHA256SUMS.txt)
echo "==> Fertig:"
ls -la "$OUT_DIR"
