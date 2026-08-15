#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if command -v node >/dev/null 2>&1; then
  node scripts/test-native-share-patch.mjs
  node scripts/test-native-ui-patch.mjs
fi

./gradlew testDebugUnitTest lintDebug assembleDebug

mkdir -p "$project_dir/dist"
cp "$project_dir/app/build/outputs/apk/debug/app-debug.apk" \
  "$project_dir/dist/CodexCompanion-debug.apk"
(
  cd "$project_dir/dist"
  sha256sum CodexCompanion-debug.apk > CodexCompanion-debug.apk.sha256
)

echo "APK: $project_dir/dist/CodexCompanion-debug.apk"
