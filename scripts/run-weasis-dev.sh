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

# Keep the experimental build's runtime state separate from the daily reader.
AI_HOME="${WEASIS_AI_HOME:-$HOME/.weasis-ai}"
AI_PROFILE="${WEASIS_AI_PROFILE:-ai-training}"
AI_NAME="${WEASIS_AI_NAME:-Weasis AI}"
AI_GOSH_PORT="${WEASIS_AI_GOSH_PORT:-17180}"
AI_TMP_DIR="$AI_HOME/tmp"
AI_MAVEN_REPO="${WEASIS_AI_MAVEN_REPO:-$AI_HOME/m2/repository}"

LAUNCHER_JAR="$ROOT/weasis-launcher/target/weasis-launcher-${APP_VERSION}.jar"
FELIX_JAR="$AI_MAVEN_REPO/org/apache/felix/org.apache.felix.framework/${FELIX_VERSION}/org.apache.felix.framework-${FELIX_VERSION}.jar"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="$(command -v java)"
fi

# Keep the native macOS renderer by default; set false only as a crash workaround.
JAVA2D_METAL="${WEASIS_JAVA2D_METAL:-true}"

mkdir -p "$AI_TMP_DIR"

if [[ ! -f "$LAUNCHER_JAR" || ! -f "$FELIX_JAR" ]]; then
  echo "Weasis is not built yet, or Felix is missing."
  echo "Run this first:"
  echo "  \"$ROOT/scripts/build-weasis-ai.sh\" clean"
  exit 1
fi

cd "$ROOT/weasis-launcher"

exec "$JAVA_CMD" \
  -Xms64m \
  -Xmx768m \
  "-Dgosh.port=$AI_GOSH_PORT" \
  "-Dweasis.path=$AI_HOME" \
  "-Dweasis.profile=$AI_PROFILE" \
  "-Dweasis.name=$AI_NAME" \
  "-Djava.io.tmpdir=$AI_TMP_DIR" \
  -Dfelix.extended.config.properties=file:conf/ai-training.json \
  --enable-native-access=ALL-UNNAMED \
  "-Dsun.java2d.metal=$JAVA2D_METAL" \
  -Dapple.laf.useScreenMenuBar=true \
  -Dapple.awt.application.appearance=NSAppearanceNameDarkAqua \
  -Djavax.accessibility.assistive_technologies=org.weasis.launcher.EmptyAccessibilityProvider \
  -Djavax.accessibility.screen_magnifier_present=false \
  "-Dmaven.localRepository=$AI_MAVEN_REPO" \
  -cp "$LAUNCHER_JAR:$FELIX_JAR" \
  org.weasis.launcher.AppLauncher \
  "$@"
