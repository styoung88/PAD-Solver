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

## Pull requests and automatic checks

Open a pull request from the feature branch into `main`. Related cleanup and its regression tests can share a branch and PR. Use the PR template to describe the final behavior, validation, and any remaining limitations. Merge only after reviewing the diff and check results.

`.github/workflows/android.yml` runs on pull requests into `main`, pushes to `main`, and manual workflow dispatches. It runs JVM tests, Android lint, and debug app/test APK builds, then the complete instrumentation suite on API 26 and API 36 emulators. API 26 protects the minimum supported Android version; API 36 adds a recent runtime. Continue checking the physical phone for its current Android version, real screen capture, permissions, and overlay behavior.

CI uses the committed Gradle wrapper and fixed action revisions with read-only repository permissions. Builds from this repository can populate Gradle caches for later runs; fork PRs only read caches. APK builds enable Gradle's task-output cache so unchanged compilation work can be reused across runs. Device jobs build the app and test APKs before starting the emulator, avoiding CPU contention during compilation. JVM tests and lint run separately without task-output caching, and both emulators still run the complete instrumentation suite. New pushes cancel outdated checks on the same PR. Test and lint reports are retained as downloadable workflow artifacts for 14 days, including failed runs. The jobs run their test tasks directly; tests are never skipped to produce a passing check.

After the first successful workflow run, repository branch protection can require `Unit tests, lint and build`, `Device tests (API 26)`, and `Device tests (API 36)` before merging. Adding a workflow alone does not configure branch protection.

GitHub currently rejects branch-protection and ruleset requests for this private repository with HTTP 403: "Upgrade to GitHub Pro or make this repository public to enable this feature." Keep the repository private unless its owner explicitly requests otherwise. Once the account supports private-repository protection, require all three checks on `main`, require the branch to be up to date, and apply the checks to administrators too.

The emulator jobs remove unused preinstalled Android NDKs from their disposable runners to leave room for Gradle caches and system images. The app does not compile native source. The AVD's data partition is explicitly set to 2 GB before boot; replacing the existing configuration entry avoids a duplicate key preserving the oversized default. Boot failures time out after five minutes and print available disk space in the logs.

## Regression coverage

Keep pure board and solver tests in `app/src/test` so they run without Android. These cover matched group boundaries, simultaneous clears, gravity and cascades, enhanced-orb accounting, pattern geometry/color filters, goal evaluation, search bounds, route replay, and cancellation. The screenshot-derived combo/pattern regression also runs locally. Check route legality and resulting matches rather than requiring a particular path when multiple routes are valid; the bounded search does not guarantee a global optimum.

Capture-request tests drive readiness, timeout, retry, stale callbacks, concurrent frame consumption, and session cancellation without clocks or actual screen sharing. Device tests exercise reader replacement, service shutdown, screenshot recognition/rejection, renderer alignment, settings persistence, recalculation, activity recreation, settings actions in short windows, busy/uncertain/unmet-goal screens, and replacement of an active solve by a new capture. UI tests keep the display awake and restore solve preferences after each test; unlock physical devices before running them.

Android's real projection consent/revocation and hardware acquisition still need a manual smoke check on the phone: deny permissions and retry; start sharing; capture a static board twice; stop sharing while capture/solve is pending; verify both overlays disappear; then start a new session. Verify no old route appears after a failed capture. The request-state tests cover timeout coordination, while the actual 350 ms surface switch and 5 second timeout remain Android integration checks. File decode failures are automated; disk-full/write failures require a controlled device test. Add a regression test with independently checked expected results whenever fixing a new bug. Keep performance benchmarks separate from correctness checks.

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
