# Development workflow

## Feature branches and merge cleanup

Use Conventional Commits for all commit messages: `<type>[optional scope]: <description>`. Common types include `feat`, `fix`, `docs`, `refactor`, `test`, `build`, and `chore`. See `../CONTRIBUTING.md` for examples.

Before starting each new feature, update `main` and create a dedicated feature branch. Use a descriptive name with the `codex/` prefix unless the user specifies another name. Keep related changes on the existing feature branch.

A request to merge a feature branch also authorizes cleaning it up. Commit and push the completed changes, merge into `main`, and push `main`. After confirming the changes are safely merged and pushed, delete the feature branch locally and on the remote, finish on `main`, and report the result. Preserve uncommitted work throughout this process.

## Build and test source sets

- App code belongs in `app/src/main`. It is compiled into the installable app APK.
- Local JVM tests belong in `app/src/test`; Android instrumentation tests belong in `app/src/androidTest`.
- Android test screenshots and other fixtures belong in `app/src/androidTest/assets`.
- Instrumentation tests use `androidTestImplementation` dependencies. Gradle packages instrumentation tests and their assets in a separate test APK; a normal `assembleDebug` app APK does not contain them.
- Build the installable app with `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat :app:assembleDebug`). Run JVM tests with `./gradlew :app:testDebugUnitTest`; run device/emulator instrumentation tests with `./gradlew :app:connectedDebugAndroidTest`.
- After device instrumentation tests, run `scripts/install-debug-to-device.ps1` again and verify the app is installed. Test-runner cleanup can remove the app from the phone.

## Install a debug build on a connected Android device (Windows)

Always build and install updated debug builds on the user's connected phone after changes that affect the app. Routine device updates are authorized without another confirmation. Report installation success, or explain if the phone is disconnected or unauthorized. If multiple physical devices are connected, ask which phone to use.

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

## Automatic PAD overlay capture

Use Open PAD overlay from the home screen, or Show route in PAD from a solution, to enable the overlay session. Allow screen sharing and full photo access when prompted. Screen-sharing consent is required once per session; Android requires a foreground notification, which has only a Stop sharing action. While the overlay is enabled, its Capture button and newly saved screenshots whose filenames contain `screenshot` trigger a fresh screen capture, board recognition, and solving with the saved color, combo target, and match pattern. The previous route clears first; an uncertain board or unmet solve goal shows a retry status instead of a route.

The app temporarily hides its overlay windows and attaches a fresh image reader to the projection surface after 350 ms to obtain a clean frame, including when PAD is static. The previous reader is closed to discard queued frames; image timestamps are not compared with system clocks because their time bases can differ. A phone screenshot acts as a trigger: the gallery image can contain the overlay, while the solver uses the subsequent clean frame. Keep PAD on the same board until capture completes. Full photo access is needed to observe screenshots from other apps; the overlay Capture button works without it. Closing the overlay or Stop sharing ends screen sharing and closes the overlay.

When the overlay cannot satisfy the saved solve goal, tap Adjust settings to return to the app with that capture loaded. The overlay controls close during review while the sharing session remains active. Change the solve settings, apply and solve, then show the revised route in PAD. Loading a new capture cancels any previous board read or solve to keep old results from replacing it.
