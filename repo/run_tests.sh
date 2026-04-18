#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker is required to run tests" >&2
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

echo ">>> Building test image"
$COMPOSE build tests

echo ">>> Running tests (mvn verify with JaCoCo 100% gate)"
$COMPOSE run --rm tests

echo ">>> Tests complete."
echo ">>> To view the JaCoCo HTML coverage report, copy it out of the container:"
echo ">>>   docker compose cp tests:/app/target/site/jacoco ./jacoco-report"
