#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker is required to build the Windows .exe" >&2
    exit 1
fi

if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
else
    echo "ERROR: docker compose (v2) or docker-compose (v1) is required" >&2
    exit 1
fi

mkdir -p dist

echo ">>> Building cross-build image (first run pulls Windows JDK + JavaFX jmods + launch4j)"
$COMPOSE build builder

echo ">>> Cross-building the Windows .exe inside the container"
$COMPOSE run --rm builder

echo ""
echo ">>> Artifact(s) in ./dist/:"
ls -lh dist/
