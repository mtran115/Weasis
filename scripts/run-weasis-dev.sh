#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PARENT_POM="$ROOT/weasis-parent/pom.xml"

property() {
  sed -n "s:.*<$1>\\(.*\\)</$1>.*:\\1:p" "$PARENT_POM" | head -n 1 | xargs
}

REVISION="${WEASIS_REVISION:-$(property revision)}"
CHANGELIST="${WEASIS_CHANGELIST:-$(property changelist)}"
APP_VERSION="${WEASIS_APP_VERSION:-${REVISION}${CHANGELIST}}"
FELIX_VERSION="${FELIX_FRAMEWORK_VERSION:-$(property felix.framework.version)}"

LAUNCHER_JAR="$ROOT/weasis-launcher/target/weasis-launcher-${APP_VERSION}.jar"
FELIX_JAR="$HOME/.m2/repository/org/apache/felix/org.apache.felix.framework/${FELIX_VERSION}/org.apache.felix.framework-${FELIX_VERSION}.jar"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="$(command -v java)"
fi

# Keep the native macOS renderer by default; set false only as a crash workaround.
JAVA2D_METAL="${WEASIS_JAVA2D_METAL:-true}"
JAVA_USER_HOME="${WEASIS_USER_HOME:-$HOME}"
GOSH_PORT="${WEASIS_GOSH_PORT:-17179}"

WEASIS_JAVA_PROPERTIES=()
if [[ -n "${WEASIS_PROFILE:-}" ]]; then
  WEASIS_JAVA_PROPERTIES+=("-Dweasis.profile=$WEASIS_PROFILE")
fi
if [[ -n "${WEASIS_NAME:-}" ]]; then
  WEASIS_JAVA_PROPERTIES+=("-Dweasis.name=$WEASIS_NAME")
fi

if [[ ! -f "$LAUNCHER_JAR" || ! -f "$FELIX_JAR" ]]; then
  echo "Weasis is not built yet, or Felix is missing."
  echo "Run this first:"
  echo "  cd \"$ROOT\""
  echo "  mvn -Dweasis.arch=macosx-aarch64 clean install"
  exit 1
fi

cd "$ROOT/weasis-launcher"

exec "$JAVA_CMD" \
  -Xms64m \
  -Xmx768m \
  "-Duser.home=$JAVA_USER_HOME" \
  "-Dgosh.port=$GOSH_PORT" \
  "${WEASIS_JAVA_PROPERTIES[@]}" \
  --enable-native-access=ALL-UNNAMED \
  "-Dsun.java2d.metal=$JAVA2D_METAL" \
  -Dapple.laf.useScreenMenuBar=true \
  -Dapple.awt.application.appearance=NSAppearanceNameDarkAqua \
  -Djavax.accessibility.assistive_technologies=org.weasis.launcher.EmptyAccessibilityProvider \
  -Djavax.accessibility.screen_magnifier_present=false \
  -Dmaven.localRepository="$HOME/.m2/repository" \
  -cp "$LAUNCHER_JAR:$FELIX_JAR" \
  org.weasis.launcher.AppLauncher \
  "$@"
