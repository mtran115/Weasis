#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export WEASIS_USER_HOME="${WEASIS_REPORT_COMPOSER_HOME:-$HOME/.weasis-report-composer-dev}"
export WEASIS_GOSH_PORT="${WEASIS_REPORT_COMPOSER_GOSH_PORT:-17180}"
export WEASIS_PROFILE="${WEASIS_REPORT_COMPOSER_PROFILE:-report-composer-v1}"
export WEASIS_NAME="${WEASIS_REPORT_COMPOSER_NAME:-Weasis Report Composer}"

exec "$ROOT/scripts/run-weasis-dev.sh" "$@"
