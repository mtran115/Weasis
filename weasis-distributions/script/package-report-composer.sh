#!/bin/bash
# Packages Weasis Report Composer as its own macOS app, so it installs beside a regular Weasis:
# its own name, bundle identifier, and shell port, and no weasis:// link handler. Its data folder
# (~/.weasis-report-composer) is set in etc/config/base.json.
#
# Usage, from the unzipped weasis-native distribution:
#   build/script/package-report-composer.sh --jdk <jdk-25> --input bin-dist --output <dir> --no-installer
set -euo pipefail

[ "$(uname -s)" = "Darwin" ] || { echo "package-report-composer.sh builds the macOS app only." >&2; exit 1; }

curPath=$(dirname "$(readlink -f "$0")")
APP_NAME="Weasis Report Composer"
resources=$(mktemp -d)
trap 'rm -rf "$resources"' EXIT

cp -R "$curPath/resources/macosx/." "$resources/"
# jpackage looks for the icon by the app name.
cp "$resources/Weasis.icns" "$resources/$APP_NAME.icns"
/usr/libexec/PlistBuddy \
  -c "Set :CFBundleIdentifier org.weasis.reportcomposer" \
  -c "Delete :CFBundleURLTypes" \
  "$resources/Info.plist"
sed -i '' 's/-Dgosh.port=17179/-Dgosh.port=17181/' "$resources/dicomizer-launcher.properties"

WEASIS_APP_NAME="$APP_NAME" \
WEASIS_APP_IDENTIFIER="org.weasis.reportcomposer" \
WEASIS_RESOURCE_DIR="$resources" \
WEASIS_GOSH_PORT="17181" \
  "$curPath/package-weasis.sh" "$@"
