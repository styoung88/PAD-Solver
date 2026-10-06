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

status=0
bash ./gradlew --no-daemon --build-cache :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.nativeUiDiagnostics=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true || status=$?
if [ "$status" -ne 0 ]; then
  mkdir -p app/build/reports/emulator-diagnostics
  adb pull /sdcard/Android/data/com.example.phoneapp/files/native-ui-failures app/build/reports/emulator-diagnostics/ || true
  adb shell dumpsys window > app/build/reports/emulator-diagnostics/windows.txt || true
  adb shell dumpsys accessibility > app/build/reports/emulator-diagnostics/accessibility.txt || true
  adb shell dumpsys power > app/build/reports/emulator-diagnostics/power.txt || true
fi
# Diagnostics must not conceal a failing Gradle test result.
exit "$status"
