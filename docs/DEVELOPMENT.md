# Development

See [CONTRIBUTING.md](../CONTRIBUTING.md) for contribution guidelines and [AGENTS.md](../AGENTS.md) for automation-specific instructions.

## Requirements

- Android Studio with Android SDK Platform 37 and Build Tools 36.0.0 installed.
- JDK 17 or later compatible with the Gradle wrapper; Android Studio's bundled runtime is suitable. CI uses JDK 21.
- Git and the checked-in Gradle 9.4.1 wrapper. A separate Gradle installation is not required.

The build versions are defined in `app/build.gradle.kts`, `build.gradle.kts`, and `gradle/wrapper/gradle-wrapper.properties`. If Android Studio uses a custom SDK location, configure it locally; do not commit `local.properties`.

## Build from source

Clone the repository, open it in Android Studio, and let Gradle sync. Use Android Studio's bundled JDK as the Gradle JDK.

For a Windows PowerShell terminal with the default Android Studio and SDK locations:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) '.android-user-home'
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux, configure the JDK and Android SDK for your environment and use `bash ./gradlew :app:assembleDebug`. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

The first build needs network access to resolve dependencies. Once they are cached, add `--offline` for local builds. The PowerShell installation and release helpers use offline mode and the default Windows toolchain locations.

## Install on a device

Enable Android developer options and USB or wireless debugging, connect an authorized phone, then run:

```powershell
.\scripts\install-debug-to-device.ps1
```

The helper selects the sole authorized physical device. With multiple phones, pass `-Serial <device-serial>`. It builds the debug APK and installs it as **PAD Solver (Dev)**. Preserve `.android-user-home/debug.keystore` between installs so updates keep the same signing identity and app data.

For wireless debugging, inspect `adb devices -l` and `adb mdns services`. Connect a paired device with `adb connect <host:debugging-port>`. If pairing is needed, use the pairing endpoint and code shown in the phone's Wireless debugging settings; the pairing and connection ports can differ. Use `adb` from the SDK's `platform-tools` directory if it is not on `PATH`.

## Tests and checks

Run Gradle from the repository root with the same JDK and SDK configuration:

| Task | Purpose |
| --- | --- |
| `:app:testDebugUnitTest` | JVM tests in `app/src/test` |
| `:app:lintDebug` | Android lint for the debug variant |
| `:app:connectedDebugAndroidTest` | Device tests in `app/src/androidTest` |
| `:app:assembleDebug` | Build the debug APK |

For example, `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` runs the local checks. Device tests require an authorized, unlocked phone or emulator. Keep the device awake during testing and restore its prior wake setting and preferences afterward.

When testing on a phone with app data to preserve, pass `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. Otherwise test cleanup can uninstall the app and reset its data and permissions. Reinstall the debug app with the installation helper after tests.

Recognition fixtures live in `app/src/androidTest/assets`. Validate route readability over `pad-color-variance-reference.jpg`, including dense revisits across all six ordinary orb colors. For solver performance, use `BoardSolverPerformanceTest` and the `SolverParallelTiming` log tag. Keep warm-ups, repeated samples, and result equality checks; do not use timing thresholds as correctness assertions.

## Signed APK releases

Release installations use `io.github.styoung88.padsolver` and the name **PAD Solver**. Debug installations retain `com.example.phoneapp`, allowing both variants on one phone.

1. Create a private release signing keystore with Android Studio or `keytool`. Store it outside the repository and securely back up the keystore and credentials. Future updates must preserve the application and signing identity.
2. Copy [`apk-release.properties.example`](../apk-release.properties.example) to `release.properties`. Fill in the keystore path, passwords, and alias. Use forward slashes for Windows paths. Real settings and signing files are ignored by Git.
3. Set `versionName` to the displayed version and increment `versionCode` for each update.
4. Run `.\scripts\build-apk-release.ps1`. It validates signing, runs unit tests and release lint, builds the APK, and verifies its signature.

The helper writes `app/build/outputs/distribution/PAD-Solver-<versionName>.apk` and an adjacent `.sha256` checksum. Release APK and bundle packaging reject incomplete signing configuration; debug builds do not require release credentials.

For a fresh dependency cache, first run `.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` with network access, then use the offline helper to verify and package the distribution files.

Environment variables can replace the local properties: `PAD_RELEASE_APPLICATION_ID`, `PAD_RELEASE_VERSION_CODE`, `PAD_RELEASE_VERSION_NAME`, `PAD_RELEASE_STORE_FILE`, `PAD_RELEASE_STORE_PASSWORD`, `PAD_RELEASE_KEY_ALIAS`, and `PAD_RELEASE_KEY_PASSWORD`. Never print secret values in build logs.

Publish from an exact, tested commit on `main`: build the APK from that commit, tag that same commit, and attach the APK and checksum to the GitHub Release. APKs and credentials do not belong in source commits. For Google Play, `:app:bundleRelease` produces an app bundle; check the current [Android release requirements](https://developer.android.com/studio/publish) and complete the store listing and privacy contact before submission.

## CI and branch protection

The workflow in [`.github/workflows/android.yml`](../.github/workflows/android.yml) runs unit tests, lint, and APK builds, followed by device tests on API 26 and API 36. Test and lint reports are uploaded as workflow artifacts.

When branch protection is available, require **Unit tests, lint and build**, **Device tests (API 26)**, and **Device tests (API 36)** on `main`, require branches to be up to date, and enforce the rules for administrators.

CI builds APKs before starting an emulator and uses Gradle caching. Keep the full test suite, disk cleanup, and a single value for each AVD partition/navigation-key setting. Software navigation avoids Android 8 SystemUI crashes on permission dialogs. Diagnose failures using the reports; do not skip failing tests to make checks pass.

## Regression guidance

- App launch requests no access. The photo picker needs no photo-library permission; showing the toolbar or route needs overlay access; only Capture starts screen-sharing consent. Notification access must not block either service.
- The overlay has its own foreground service; stopping sharing keeps it usable. Android backup and transfer retain solve preferences only.
- Assert legal routes and independently checked results rather than one exact path or a global optimum; the solver uses bounded search.
- Capture must use a fresh reader, including on static boards. Do not compare image timestamps with system clocks; their time bases can differ.
- For capture changes, deny and retry consent, capture a static board twice, stop while a capture is pending, check that the toolbar remains usable and stale routes stay cleared, then restart sharing. Check timeout and write-failure behavior on a device when changing those paths.

If Kotlin reports a daemon `AccessDeniedException` but its in-process fallback ends with `BUILD SUCCESSFUL`, the build completed. For a hang or failed build, stop Gradle with `.\gradlew.bat --stop` before retrying.
