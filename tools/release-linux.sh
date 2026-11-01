#!/bin/bash
# From the Mac: copies the current source to hostinger, builds the Linux packages there and
# fetches them back into dist/linux.
#
#   ./tools/release-linux.sh
set -euo pipefail
cd "$(dirname "$0")/.."

HOST="${HOST:-hostinger}"
REMOTE="build/MetalRat"

rsync -az --delete --exclude .git --exclude build --exclude '*/build' --exclude .gradle --exclude .kotlin \
  --exclude local.properties --exclude dist ./ "$HOST:$REMOTE/"
ssh "$HOST" "cd $REMOTE && ./tools/build-linux.sh 2>&1 | tee ~/build/metalrat-linux.log | tail -n 20"
mkdir -p dist/linux
rsync -az --delete "$HOST:$REMOTE/dist/linux/" dist/linux/
ls -la dist/linux
