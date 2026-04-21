#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

# Gradle wrapper 9.4+ chạy được với JDK 17–26 (khớp ./gradlew trên máy dùng JDK 25 mặc định).
# Nếu cần ép JVM: export JAVA_HOME=/path/to/jdk trước khi chạy script.

if ! command -v adb >/dev/null 2>&1; then
  echo "adb không có trong PATH. Cài Android SDK platform-tools hoặc thêm vào PATH." >&2
  exit 1
fi

if ! adb devices | awk 'BEGIN{c=0} /\tdevice$/ {c++} END{exit c==0 ? 1 : 0}'; then
  echo "Không thấy thiết bị adb ở trạng thái \"device\". Bật USB debugging và cắm cáp." >&2
  exit 1
fi

echo "JAVA_HOME=${JAVA_HOME:-<PATH — JVM chạy Gradle>}"

GRADLE_ARGS=(clean installDebug --no-build-cache --rerun-tasks)
# Tải lại metadata/artifact dependency (chậm). Bật khi cần: REFRESH_DEPS=1 ./build-deploy-no-cache.sh
if [[ "${REFRESH_DEPS:-}" == "1" ]]; then
  GRADLE_ARGS+=(--refresh-dependencies)
fi

# clean: xóa output cũ; --no-build-cache: không dùng Gradle build cache;
# --rerun-tasks: chạy lại task dù Gradle coi là up-to-date.
./gradlew "${GRADLE_ARGS[@]}"

adb shell am start -n com.ttsaistory.app/.MainActivity

echo "Xong: đã build (không cache) và cài debug lên thiết bị."
