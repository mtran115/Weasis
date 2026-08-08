#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AI_HOME="${WEASIS_AI_HOME:-$HOME/.weasis-ai}"
AI_MAVEN_REPO="${WEASIS_AI_MAVEN_REPO:-$AI_HOME/m2/repository}"
WEASIS_ARCH="${WEASIS_ARCH:-macosx-aarch64}"

cd "$ROOT"

exec mvn \
  "-Dmaven.repo.local=$AI_MAVEN_REPO" \
  "-Dweasis.arch=$WEASIS_ARCH" \
  "$@" \
  install
