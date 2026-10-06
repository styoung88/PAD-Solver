# Development

Follow `AGENTS.md` for branches, commits, merges, and authorized phone updates.

## Build and device checks

- Install with `scripts/install-debug-to-device.ps1`; it selects the sole authorized physical phone. Use `-Serial` only when needed.
- For direct Gradle commands, use Android Studio's bundled JDK, `%LOCALAPPDATA%/Android/Sdk`, `%USERPROFILE%/.gradle`, and the checkout's `.android-user-home` for `ANDROID_USER_HOME`. Prefer `--offline` when dependencies are cached.
- Gradle tasks: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:connectedDebugAndroidTest` (`.\gradlew.bat` on Windows).
- JVM tests: `app/src/test`; device tests: `app/src/androidTest`; committed image fixtures: `app/src/androidTest/assets`.
- UI tests require an unlocked phone. Keep it awake during testing and restore its prior wake setting and preferences afterward.
- On the phone, pass `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` to retain app data and permissions. Otherwise cleanup can uninstall the app and reset them. Reinstall the debug app with the install script after tests.

## Known failure handling

- Preserve `.android-user-home/debug.keystore` between installs so updates retain their signing identity. Keep generated artifacts and caches untracked.
- A Kotlin daemon `AccessDeniedException` is harmless if in-process fallback ends with `BUILD SUCCESSFUL`. Use `.\gradlew.bat --stop` and retry only for a hang or failed build.
- CI compiles APKs before emulator startup and caches build outputs. Preserve full test execution, runner disk cleanup, and the replacement of the AVD partition setting; duplicate configuration keys can retain the oversized default.
- Required checks are deferred: GitHub returns HTTP 403 for protection on this private repository's current plan. Do not retry or change visibility unless the user changes that decision. When available, require all three workflow checks on `main`, up-to-date branches, and administrator enforcement.

## Regression decisions

- Photo library access is optional: only the screenshot shortcut requires full access. Recheck grants before watching; selected-only or denied access must leave direct capture and the picker usable. Backup/transfer allowlists retain only solve preferences, excluding captures and phone-specific settings.
- Keep selected photo access in Android's request flow; it may offer reselection instead of full access. Explain Settings only when photo access is denied and Android no longer prompts. Hide permission controls once full access is granted.
- Test legal routes and independently checked results, not one exact path or a global optimum; the solver uses bounded search.
- Capture must use a fresh reader to discard overlay frames, including on static boards. Do not compare image timestamps with system clocks; their time bases can differ.
- Real projection consent/revocation, hardware acquisition/timeouts, and disk-full writes still need phone checks. For capture changes: deny and retry, capture a static board twice, stop while pending, confirm overlays close and stale routes stay cleared, then restart sharing.
- CI job names and configuration live in `.github/workflows/android.yml`; use its downloadable artifacts to diagnose failures. Never skip failing tests to make checks pass.
