# Android-solver

A native Android app project using Kotlin and Jetpack Compose. It is set up for local development with a Play Store compatible target SDK baseline.

## Requirements

- Android Studio
- Android SDK Platform 37 and Android SDK Build Tools 36.0.0
- JDK 17 (Android Studio's bundled runtime is suitable)
- Git

Install SDK Platform 37 and Android SDK Build Tools 36.0.0 from Android Studio's SDK Manager if they are not already installed. Open this folder in Android Studio and let it sync the Gradle project. Connect your phone with USB debugging enabled to run on your device, or create an emulator.

## Project structure

```text
app/                  Android application module
  src/main/java/      Kotlin source
  src/main/res/       Android resources
  src/main/AndroidManifest.xml
docs/                 Project notes and decisions
.vscode/              Optional editor recommendations
```

## Before publishing

- Replace the temporary application ID `com.example.phoneapp` with a unique reverse-domain ID you control. Application IDs become part of the app's identity on Google Play.
- Review app name, icon, privacy disclosures, permissions, and store listing.
- Keep signing keys and passwords out of Git. Create and securely back up a release signing key before publishing.
- This project compiles and targets API 37 (Android 17). Google Play currently requires new apps and updates to target API 36 or higher; targeting 37 opts into Android 17's target-specific behavior changes. Recheck Play's requirement before each release.

## Git

The local `main` branch tracks `origin/main` on GitHub. The app starter files are currently uncommitted. To add and push them:

```powershell
git add .
git commit -m "Add Android app starter"
git push
```

Do not commit `local.properties`, build outputs, or signing credentials.
