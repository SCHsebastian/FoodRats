#!/usr/bin/env bash
#
# Computes the version name + version code for a release build and prints them
# as KEY=VALUE lines (intended to be appended to "$GITHUB_ENV").
#
#   beta  → versionName = latest SemVer tag (the version in the oven),
#           versionCode = BASE_BETA + run number.
#   prod  → versionName = the pushed tag (vX.Y.Z → X.Y.Z),
#           versionCode = BASE_PROD + run number.
#
# Separate BASE offsets guarantee a prod build number always exceeds any beta
# build number, so the stores never reject a release for a non-increasing code.
#
# Usage: scripts/ci/compute_version.sh <beta|prod>
set -euo pipefail

MODE="${1:?usage: compute_version.sh <beta|prod>}"
RUN_NUMBER="${GITHUB_RUN_NUMBER:?GITHUB_RUN_NUMBER is required}"

BASE_BETA=10000
BASE_PROD=500000

case "$MODE" in
  beta)
    base=$BASE_BETA
    latest_tag="$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.1.0")"
    version_name="${latest_tag#v}"
    ;;
  prod)
    base=$BASE_PROD
    tag="${GITHUB_REF_NAME:?GITHUB_REF_NAME is required for prod}"
    version_name="${tag#v}"
    ;;
  *)
    echo "unknown mode: $MODE (expected beta|prod)" >&2
    exit 1
    ;;
esac

version_code=$((base + RUN_NUMBER))

echo "VERSION_NAME=${version_name}"
echo "VERSION_CODE=${version_code}"
