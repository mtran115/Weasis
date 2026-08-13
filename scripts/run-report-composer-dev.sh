#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export WEASIS_USER_HOME="${WEASIS_REPORT_COMPOSER_HOME:-$HOME/.weasis-report-composer-dev}"
export WEASIS_GOSH_PORT="${WEASIS_REPORT_COMPOSER_GOSH_PORT:-17180}"
export WEASIS_PROFILE="${WEASIS_REPORT_COMPOSER_PROFILE:-report-composer-v1}"
export WEASIS_NAME="${WEASIS_REPORT_COMPOSER_NAME:-Weasis Report Composer}"

# JDK 26 fixes JDK-8372757, a native macOS accessibility crash in Swing popup menus.
REPORT_COMPOSER_JAVA_HOME="${WEASIS_REPORT_COMPOSER_JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
if [[ ! -x "$REPORT_COMPOSER_JAVA_HOME/bin/java" ]]; then
  echo "Weasis Report Composer requires the Homebrew OpenJDK 26 runtime."
  echo "Install it with: brew install openjdk"
  exit 1
fi
export JAVA_HOME="$REPORT_COMPOSER_JAVA_HOME"

exec "$ROOT/scripts/run-weasis-dev.sh" "$@"
