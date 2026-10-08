# Development

Follow `AGENTS.md` for branches, commits, merges, and authorized phone updates.

## Build and device checks

- Install with `scripts/install-debug-to-device.ps1`; it selects the sole authorized physical phone. Use `-Serial` only when needed.
- The user's phone uses wireless debugging. Before reporting no connected phone, check `adb devices -l` and `adb mdns services` for a paired wireless device; connect its advertised debugging endpoint with `adb connect`, then retry the install script. If pairing is required, ask for the phone's pairing endpoint and code.
- For direct Gradle commands, use Android Studio's bundled JDK, `%LOCALAPPDATA%/Android/Sdk`, `%USERPROFILE%/.gradle`, and the checkout's `.android-user-home` for `ANDROID_USER_HOME`. Prefer `--offline` when dependencies are cached.
- Gradle tasks: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:connectedDebugAndroidTest` (`.\gradlew.bat` on Windows).
- JVM tests: `app/src/test`; device tests: `app/src/androidTest`; committed image fixtures: `app/src/androidTest/assets`.
- Validate route appearance over `pad-color-variance-reference.jpg`, with dense revisits crossing all six orb colors.
- UI tests require an unlocked phone. Keep it awake during testing and restore its prior wake setting and preferences afterward.
- Measure solver performance on the phone with `BoardSolverPerformanceTest`; read medians from `SolverParallelTiming`. Keep warm-ups, repeated samples, and result equality checks; do not assert timing thresholds.
- On the phone, pass `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` to retain app data and permissions. Otherwise cleanup can uninstall the app and reset them. Reinstall the debug app with the install script after tests.

## Known failure handling

- Preserve `.android-user-home/debug.keystore` between installs so updates retain their signing identity. Keep generated artifacts and caches untracked.
- A Kotlin daemon `AccessDeniedException` is harmless if in-process fallback ends with `BUILD SUCCESSFUL`. Use `.\gradlew.bat --stop` and retry only for a hang or failed build.
- CI builds APKs before emulator startup and caches outputs. Keep full test execution, disk cleanup, and single AVD partition/navigation-key entries. Software navigation avoids Android 8 SystemUI crashes on permission dialogs.
- Required checks are deferred: GitHub returns HTTP 403 for protection on this private repository's current plan. Do not retry or change visibility unless the user changes that decision. When available, require all three workflow checks on `main`, up-to-date branches, and administrator enforcement.

## Regression decisions

- Request no access on launch. The picker needs no broad photo access; showing the toolbar/route needs overlay access; only Capture starts projection consent. Notifications must not block either service. New installs explicitly enable screenshots; full-access legacy users retain their choice.
- Automatic screenshots read the saved image, independently of projection, only while the overlay is open. Recheck grants before watching; limited/denied access leaves Capture and the picker usable. The overlay has its own foreground service; stopping sharing keeps it usable. Backup/transfer retains solve preferences only.
- Selected photo access can reopen reselection without an Allow all choice. Explain Settings for selected access or blocked denial; otherwise request permission. Use App info with a best-effort Permissions highlight; direct permission pages require system privileges. Hide permission controls with full access.
- Test legal routes and independently checked results, not one exact path or a global optimum; the solver uses bounded search.
- Capture must use a fresh reader to discard overlay frames, including on static boards. Do not compare image timestamps with system clocks; their time bases can differ.
- Capture changes need phone checks: deny and retry, capture a static board twice, stop while pending, confirm the toolbar remains usable and stale routes stay cleared, then restart sharing. Hardware timeouts and disk-full writes still need device checks.
- CI job names and configuration live in `.github/workflows/android.yml`; use its downloadable artifacts to diagnose failures. Never skip failing tests to make checks pass.
- After pushing, do not wait for CI to finish; the user will report its result.
