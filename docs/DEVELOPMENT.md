# Development workflow

## Build and test source sets

- App code belongs in `app/src/main`. It is compiled into the installable app APK.
- Local JVM tests belong in `app/src/test`; Android instrumentation tests belong in `app/src/androidTest`.
- Android test screenshots and other fixtures belong in `app/src/androidTest/assets`.
- Instrumentation tests use `androidTestImplementation` dependencies. Gradle packages instrumentation tests and their assets in a separate test APK; a normal `assembleDebug` app APK does not contain them.
- Build the installable app with `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat :app:assembleDebug`). Run JVM tests with `./gradlew :app:testDebugUnitTest`; run device/emulator instrumentation tests with `./gradlew :app:connectedDebugAndroidTest`.

## Install a debug build on a connected Android device (Windows)

Use `scripts/install-debug-to-device.ps1`. For example:

```powershell
.\scripts\install-debug-to-device.ps1 -Serial <adb-device-serial>
```

The script locates the Android SDK and Android Studio's bundled JDK from their default Windows locations, builds `:app:assembleDebug` offline from the existing Gradle dependency cache, then installs the APK with `adb install -r`. Connect and authorize the device first. If exactly one physical Android device is connected, `-Serial` may be omitted; otherwise provide it explicitly.

The script sets `ANDROID_USER_HOME` to the ignored `.android-user-home` directory in the checkout. Keep its `debug.keystore` between installs: Android uses that signing key to allow updates over the existing debug app. Do not commit the keystore. The lock file in that directory is generated build residue and can be removed when no Gradle build is running; the script recreates the directory as needed.

## Kotlin compiler daemon fallback

On this Windows setup, Gradle has occasionally logged an `AccessDeniedException` while creating a Kotlin daemon marker under the user's local Kotlin daemon directory. Gradle then automatically falls back to in-process Kotlin compilation; recent debug builds completed successfully with that fallback. If a build actually hangs or repeatedly fails, stop stale Gradle daemons with `.\gradlew.bat --stop` and rerun the build or install script. The daemon warning by itself is not a failed build when Gradle reports `BUILD SUCCESSFUL`.

## Local artifacts

Keep generated previews, exported device-build archives, Gradle caches, and build outputs out of the repository. Committed screenshot fixtures used by tests belong only under `app/src/androidTest/assets`.
