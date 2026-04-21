#!/usr/bin/env bash
set -euo pipefail

# Chụp màn hình thiết bị Android qua adb.
# Dùng: ./capture-android-screen.sh [đường_dẫn_file.png]
# Mặc định: lưu vào screenshots/android-YYYYMMDD-HHMMSS.png
# Nhiều máy: ANDROID_SERIAL=<serial> ./capture-android-screen.sh

if ! command -v adb >/dev/null 2>&1; then
  echo "adb không có trong PATH (cài Android SDK platform-tools)." >&2
  exit 1
fi

if ! adb devices | awk 'BEGIN{c=0} /\tdevice$/ {c++} END{exit c==0 ? 1 : 0}'; then
  echo "Không có thiết bị adb trạng thái \"device\". Bật USB debugging và cắm cáp." >&2
  exit 1
fi

DEFAULT_DIR="${SCREENSHOT_DIR:-./screenshots}"
OUT="${1:-}"

if [[ -z "$OUT" ]]; then
  mkdir -p "$DEFAULT_DIR"
  OUT="$DEFAULT_DIR/android-$(date +%Y%m%d-%H%M%S).png"
elif [[ -d "$OUT" ]]; then
  OUT="${OUT%/}/android-$(date +%Y%m%d-%H%M%S).png"
fi

mkdir -p "$(dirname "$OUT")"

# -p: PNG; exec-out: xuất nhị phân ra stdout (ổn định hơn shell screencap | pull trên nhiều máy)
adb exec-out screencap -p >"$OUT"

echo "Đã lưu: $OUT"
