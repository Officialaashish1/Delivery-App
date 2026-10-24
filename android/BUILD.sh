#!/usr/bin/env bash
# Builds LocalRush Android apps inside Docker (no JDK / Android SDK needed on the host).
# Usage: ./BUILD.sh [gradle tasks...]   (default: assembleDebug for all apps + core unit tests)
set -euo pipefail
cd "$(dirname "$0")"

IMAGE="${LOCALRUSH_ANDROID_IMAGE:-ghcr.io/cirruslabs/android-sdk:36}"
GRADLE_VOLUME="${LOCALRUSH_GRADLE_VOLUME:-localrush-gradle}"
TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
  TASKS=(:app-customer:assembleDebug :app-rider:assembleDebug :app-partner:assembleDebug
         :core:common:testDebugUnitTest :core:network:testDebugUnitTest)
fi

# Run as the host user so build outputs are not root-owned. Gradle cache lives in a named volume.
# First-time setup of the cache volume (idempotent): make it writable by the host uid.
docker run --rm -v "$GRADLE_VOLUME":/vol "$IMAGE" bash -c "chown -R $(id -u):$(id -g) /vol" >/dev/null 2>&1 || true

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp/home \
  -e GRADLE_USER_HOME=/tmp/home/.gradle \
  -e ANDROID_HOME=/opt/android-sdk-linux \
  -e LOCALRUSH_API_BASE_URL -e LOCALRUSH_WS_BASE_URL \
  -e LOCALRUSH_KEYSTORE_FILE -e LOCALRUSH_KEYSTORE_PASSWORD -e LOCALRUSH_KEY_ALIAS -e LOCALRUSH_KEY_PASSWORD \
  -v "$PWD":/project \
  -v "$GRADLE_VOLUME":/tmp/home/.gradle \
  -w /project \
  "$IMAGE" \
  bash -c 'mkdir -p /tmp/home && ./gradlew --no-daemon --stacktrace "$@"' -- "${TASKS[@]}"
