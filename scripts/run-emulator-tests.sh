#!/usr/bin/env bash
set -eu

# Only configure the disposable CI emulator, never a connected physical phone.
case "${ANDROID_SERIAL:-}" in
  emulator-*) ;;
  *) echo 'ANDROID_SERIAL must identify the CI emulator.' >&2; exit 1 ;;
esac
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

diagnostics_directory=app/build/reports/emulator-diagnostics
mkdir -p "$diagnostics_directory"
# Keep system logs between tests, where an emulator ANR may first occur.
adb logcat -b all -v threadtime > "$diagnostics_directory/logcat.txt" 2>&1 &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true' EXIT

status=0
bash ./gradlew --no-daemon --build-cache :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.nativeUiDiagnostics=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true || status=$?
if [ "$status" -ne 0 ]; then
  adb pull /sdcard/Android/data/com.example.phoneapp/files/native-ui-failures "$diagnostics_directory/" || true
  adb shell dumpsys window > "$diagnostics_directory/windows.txt" || true
  adb shell dumpsys accessibility > "$diagnostics_directory/accessibility.txt" || true
  adb shell dumpsys power > "$diagnostics_directory/power.txt" || true
  adb shell dumpsys activity lastanr > "$diagnostics_directory/last-anr.txt" || true
fi
# Diagnostics must not conceal a failing Gradle test result.
exit "$status"
