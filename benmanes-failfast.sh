#!/usr/bin/env bash
set -euo pipefail

# Run Ben Manes Versions plugin as a fail-fast pre-check
./gradlew dependencyUpdates

if [ $? -ne 0 ]; then
  echo "[benmanes-failfast] Dependency update check failed! Failing fast."
  exit 1
fi

echo "[benmanes-failfast] Dependency update check passed or no updates found." 