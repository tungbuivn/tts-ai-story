#!/usr/bin/env bash
# Sau khi: ./gradlew :app:installDebug
# Chạy script này rồi thao tác app đến khi ANR — xem dòng BEGIN/END nào không có END,
# hoặc ActivityManager "ANR in com.ttsaistory.app".
set -euo pipefail
adb logcat -c
exec adb logcat -v time \
  TtsAnrDiag:E \
  StrictMode:D \
  ActivityManager:I \
  AndroidRuntime:E
