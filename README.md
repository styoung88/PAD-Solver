# Android-solver

A native Android app that captures and solves 5×4, 6×5, and 7×6 Puzzles & Dragons boards. It uses Kotlin and Jetpack Compose and targets the newest installed Android API.

## Requirements

- Android Studio
- Android SDK Platform 37 and Android SDK Build Tools 36.0.0
- JDK 17 (Android Studio's bundled runtime is suitable)
- Git

Install SDK Platform 37 and Android SDK Build Tools 36.0.0 from Android Studio's SDK Manager if they are not already installed. Open this folder in Android Studio and let it sync the Gradle project. Connect your phone with USB debugging enabled to run on your device, or create an emulator.

## First-version flow

1. Tap **Capture board** and approve the screen-capture and notification prompts.
2. Switch to Puzzles & Dragons, open the notification shade, and tap **Capture board** in the ongoing capture notification.
3. Review the detected board, then solve it. Board size is detected automatically.
4. Tap **Show route in PAD**. If asked, grant draw-over-apps access, then tap **Show route in PAD** again to display the numbered, touch-through route overlay. No game input is automated.

Each screen-capture session requires system approval. Notifications are needed for the capture action on Android 13 and later. Captured images are kept in the app's private storage and are excluded from Android backup. They are not sent over the network.

The floating Capture button clears the route and hides the toolbar before reading a fresh screen frame. Use **Choose existing screenshot** to review and solve a saved board with the system photo picker; no photo-library permission is needed.

Choose **Game: PAD / PAD 0** in solve settings. The saved choice applies to captures, screenshots, and game-opening actions; changing it clears the previous board and route and ends screen sharing. Existing installations default to PAD. PAD 0 supports the supplied 6×5 layout, ordinary orbs, jammers, and combo-bonus badges. Each matched bonus orb adds one combo, including in cascades. Combo targets and results include these bonuses. Orb lock badges are ignored in both games.

PAD 0 offers eight saved **Team skill** presets, with **None** as the default. Team ATK multipliers affect relative damage ranking. Multicolor uses a checklist of the team's main and sub-attributes. Expert Multicolor and 5 Color Erasure require all five attack colors and have no attribute checklist. Hearts and hazards do not count as attacking attributes. **Damage reduction** optionally requires activation for skills with a separate reduction condition. Results show the team ATK multiplier, active reduction, and fixed bonus attack separately. Move-time bonuses, HP and recovery are excluded; the Orb movement count controls route length. Combo Orb appearance chance does not add speculative combos. This remains a relative estimate without creature stats, awakenings or enemy modifiers.

The recognizer supports the five attack colors, hearts, jammers, poison, lethal poison, and bombs, plus enhanced (+) and weakened (−) markers. Ordinary orbs use color sampling, hazards use artwork references, and modifiers use separate visual checks; additional device captures and orb skins still need calibration. Uncertain captures ask for a new capture. Hazards match and contribute combos, but poison damage and bomb explosions are not modeled. The board-only color-damage estimate includes the 6% per remaining enhanced orb bonus and weakened-orb penalties after enhancement cancellation; it excludes team awakenings and other team or enemy modifiers. See [orb references and supported rules](docs/ORB_REFERENCES.md).

The solver uses a bounded beam search with deterministic gravity cascades and no random skyfall. Combo minimums, attacking-color pattern requirements and enabled damage reduction must be met first. Qualifying routes maximize the selected color's relative damage, or total combos when no color or team skill is selected. With a PAD 0 team skill and no color focus, attacking colors have equal damage weight. A pattern requirement means at least one match, rather than maximizing pattern count. Score ties prefer fewer moves, then fewer turns. Pattern progress guides the search frontier but does not rank final solutions. Requests proven impossible by orb inventory or board space return immediately for adjustment.

Color-focused solves run two bounded searches concurrently, independently of the selected combo minimum, then share their candidates. An additional search targets the requested requirements if needed. Equal results remain deterministic across workers. Search limits are 30 moves for 5×4, 40 for 6×5, and 50 for 7×6. Row patterns span the full board width. The solver does not use the in-game timer and does not guarantee a global optimum.

## Project structure

```text
app/                  Android application module
  src/main/java/      Kotlin source
  src/main/res/       Android resources
  src/main/AndroidManifest.xml
  src/androidTest/    Instrumentation tests and test-only screenshot fixtures
docs/                 Project notes and decisions
.vscode/              Optional editor recommendations
```

See [Development workflow](docs/DEVELOPMENT.md) for build, test, and device-install instructions.

Puzzle & Dragons 0 support and remaining calibration are tracked in the [PAD 0 support checklist](docs/PAD_ZERO_SUPPORT.md).

## Signed APK releases

The release app is **PAD Solver**, with application ID `io.github.styoung88.padsolver`. Debug builds use the existing `com.example.phoneapp` ID and the launcher name **PAD Solver (Dev)** so both can be installed together.

1. Create a private release signing keystore with Android Studio or `keytool`, and keep it outside the repository. Securely back up the keystore and credentials; reuse the same key for future updates.
2. Copy `apk-release.properties.example` to `release.properties` and fill in the keystore path, passwords, and key alias. Use forward slashes in Windows paths. The local file is ignored by Git; `PAD_RELEASE_*` environment variables can also supply these settings.
3. Run `.\scripts\build-apk-release.ps1`. It checks the configuration, runs unit tests and release lint, builds the APK, and verifies its signature.
4. Attach `app/build/outputs/distribution/PAD-Solver-1.0.0.apk` and its `.sha256` checksum to a GitHub Release. Keep APKs and signing credentials out of source commits.

Increment `versionCode` for each update and set the displayed `versionName` in `release.properties`. The output filename uses `versionName`. Release APK and bundle packaging require a valid signing configuration; debug builds do not.

## Before publishing

- Keep the release application ID and signing identity consistent across updates. The temporary `com.example.phoneapp` ID is used only for debug builds.
- Review app name, icon, privacy disclosures, permissions, and store listing.
- Keep signing keys and passwords out of Git. Create and securely back up a release signing key before publishing.
- This project compiles and targets API 37 (Android 17). Google Play currently requires new apps and updates to target API 36 or higher; targeting 37 opts into Android 17's target-specific behavior changes. Recheck Play's requirement before each release.

## Git

Start each feature by creating a dedicated branch from an up-to-date `main`, using the `codex/` prefix unless another name is requested. Use Conventional Commit messages. When a feature is complete, commit it and push and merge it before starting the next feature. A request to merge includes cleanup: after pushing the merge to `main`, delete the fully merged feature branch locally and on the remote, then finish on `main`. Preserve uncommitted work. Do not commit `local.properties`, build outputs, or signing credentials.
