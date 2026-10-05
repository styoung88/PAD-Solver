# Android-solver

A native Android app that captures and solves a standard Puzzles & Dragons board. It uses Kotlin and Jetpack Compose and targets the newest installed Android API.

## Requirements

- Android Studio
- Android SDK Platform 37 and Android SDK Build Tools 36.0.0
- JDK 17 (Android Studio's bundled runtime is suitable)
- Git

Install SDK Platform 37 and Android SDK Build Tools 36.0.0 from Android Studio's SDK Manager if they are not already installed. Open this folder in Android Studio and let it sync the Gradle project. Connect your phone with USB debugging enabled to run on your device, or create an emulator.

## First-version flow

1. Tap **Capture board** and approve the screen-capture and notification prompts.
2. Switch to Puzzles & Dragons, open the notification shade, and tap **Capture board** in the ongoing capture notification.
3. Review the detected 6×5 board, then solve it.
4. Tap **Show route in PAD**. If asked, grant draw-over-apps access, then tap **Show route in PAD** again to display the numbered, touch-through route overlay. No game input is automated.

Each screen-capture session requires system approval. Notifications are needed for the capture action on Android 13 and later. Captured images are kept in the app's private storage and are excluded from Android backup. They are not sent over the network.

The recognizer supports the six ordinary orb colors and hearts, and detects enhanced (+) orbs using the marker near each orb's lower-right edge. The marker detector is a visual heuristic and should be checked against more device captures. Hazards and locks are not supported yet; uncertain captures ask for a new capture. The board-only color-damage estimate includes the 6% per enhanced orb match bonus, but excludes team awakenings and other team or enemy modifiers.

The solver uses a bounded beam search to find high-combo routes and prefers shorter routes when combo counts tie. It does not use the in-game timer. Like other bounded searches, it is not guaranteed to find the global optimum; the search objective and move bound are isolated so future goal types and complexity settings can be added.

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

Start each feature from an up-to-date `main` branch. Use Conventional Commit messages. When a feature is complete, commit it and push and merge it before starting the next feature. Do not commit `local.properties`, build outputs, or signing credentials.
