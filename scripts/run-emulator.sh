#!/usr/bin/env bash
# Chạy Android Emulator (Linux).
# Dùng: ./scripts/run-emulator.sh [tên_AVD] [-- tham_số_thêm_cho_emulator]
# Mặc định AVD: Medium_Phone_API_36.1
# Biến môi trường: ANDROID_HOME, RUN_EMULATOR_AVD, RUN_EMULATOR_EXTRA_ARGS (chuỗi)
# adb -s emulator-5554 emu kill
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_HOME
EMULATOR_BIN="$ANDROID_HOME/emulator/emulator"
ADB_BIN="${ANDROID_HOME}/platform-tools/adb"

# Chỉ tắt emulator cũ nếu nó đang online — tránh "could not connect to TCP port 5554: Connection refused"
if [[ -x "$ADB_BIN" ]] && "$ADB_BIN" devices 2>/dev/null | grep -qE '^emulator-[0-9]+[[:space:]]+device'; then
  serial="$("$ADB_BIN" devices 2>/dev/null | awk '/^emulator-/ && $2=="device" { print $1; exit }')"
  if [[ -n "${serial:-}" ]]; then
    echo "Đang tắt emulator đang chạy: $serial"
    "$ADB_BIN" -s "$serial" emu kill 2>/dev/null || true
    sleep 1
  fi
fi

if [[ ! -x "$EMULATOR_BIN" ]]; then
  echo "Lỗi: không tìm thấy emulator tại: $EMULATOR_BIN" >&2
  echo "Đặt ANDROID_HOME (ví dụ export ANDROID_HOME=\$HOME/Android/Sdk)" >&2
  exit 1
fi

DEFAULT_AVD="Medium_Phone_API_36.1"
AVD="${RUN_EMULATOR_AVD:-$DEFAULT_AVD}"
EXTRA=()

if [[ $# -gt 0 && "$1" != --* ]]; then
  AVD="$1"
  shift
fi

if [[ $# -gt 0 && "$1" == -- ]]; then
  shift
  EXTRA=("$@")
elif [[ -n "${RUN_EMULATOR_EXTRA_ARGS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA=($RUN_EMULATOR_EXTRA_ARGS)
fi

# -gpu swiftshader_indirect: ổn định trên máy không có GPU / headless
# Bỏ dòng default nếu muốn dùng GPU máy: thêm -- sau tên AVD rồi truyền -gpu host
DEFAULT_FLAGS=(-gpu swiftshader_indirect)

echo "ANDROID_HOME=$ANDROID_HOME"
echo "AVD=$AVD"
exec "$EMULATOR_BIN" -avd "$AVD" "${DEFAULT_FLAGS[@]}" "${EXTRA[@]}"
