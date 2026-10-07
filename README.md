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
3. Review the detected board, then solve it. Board size is detected automatically; choose a size in Solve settings to override detection and reread the screenshot.
4. Tap **Show route in PAD**. If asked, grant draw-over-apps access, then tap **Show route in PAD** again to display the numbered, touch-through route overlay. No game input is automated.

Each screen-capture session requires system approval. Notifications are needed for the capture action on Android 13 and later. Captured images are kept in the app's private storage and are excluded from Android backup. They are not sent over the network.

The recognizer supports the six ordinary orb colors and hearts, and detects enhanced (+) orbs using the marker near each orb's lower-right edge. The marker detector is a visual heuristic and should be checked against more device captures. Hazards and locks are not supported yet; uncertain captures ask for a new capture. The board-only color-damage estimate includes the 6% per enhanced orb match bonus, but excludes team awakenings and other team or enemy modifiers.

The solver uses a bounded beam search with deterministic gravity cascades and no random skyfall. Combo minimums and attacking-color pattern requirements must be met first; qualifying routes maximize the selected color's board-damage estimate, or total combos when no color is selected. A pattern requirement means at least one match, rather than maximizing pattern count. Score ties prefer fewer moves, then fewer turns. Pattern progress guides the search frontier but does not rank final solutions. Requests proven impossible by orb inventory or board space return immediately for adjustment.

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

## Before publishing

- Replace the temporary application ID `com.example.phoneapp` with a unique reverse-domain ID you control. Application IDs become part of the app's identity on Google Play.
- Review app name, icon, privacy disclosures, permissions, and store listing.
- Keep signing keys and passwords out of Git. Create and securely back up a release signing key before publishing.
- This project compiles and targets API 37 (Android 17). Google Play currently requires new apps and updates to target API 36 or higher; targeting 37 opts into Android 17's target-specific behavior changes. Recheck Play's requirement before each release.

## Git

Start each feature by creating a dedicated branch from an up-to-date `main`, using the `codex/` prefix unless another name is requested. Use Conventional Commit messages. When a feature is complete, commit it and push and merge it before starting the next feature. A request to merge includes cleanup: after pushing the merge to `main`, delete the fully merged feature branch locally and on the remote, then finish on `main`. Preserve uncommitted work. Do not commit `local.properties`, build outputs, or signing credentials.
