# Project notes

## Initial choices

- Native Android app written in Kotlin.
- Jetpack Compose for the user interface.
- One `app` module for a personal phone app; additional modules can be introduced if the app grows.
- `minSdk` 26 as an initial modern-device baseline; revisit it if the app needs to support older phones.
- `compileSdk` and `targetSdk` 37 to use the newest installed Android platform. Google Play currently requires at least API 36 for new apps and updates.

## Compatibility consideration

Keep `minSdk` at 26 for now. This does not restrict features on the Pixel 9. If a future feature or library requires a newer Android API, either guard that feature so it only runs on supported Android versions or raise `minSdk` if the app no longer needs to support API 26 devices. Check library minimum SDK requirements when adding dependencies.

## To decide

- App purpose, name, and permanent application ID.
- Minimum Android version based on the actual device and intended audience.
- Whether the project needs local persistence, network access, or additional modules.
- Review Android 17 (API 37) behavior changes as features and permissions are added.
