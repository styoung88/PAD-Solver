# Project notes

## Initial choices

- Native Android app written in Kotlin.
- Jetpack Compose for the user interface.
- One `app` module for a personal phone app; additional modules can be introduced if the app grows.
- `minSdk` 26 as an initial modern-device baseline; revisit it if the app needs to support older phones.
- `compileSdk` and `targetSdk` 37 to use the newest installed Android platform. Google Play currently requires at least API 36 for new apps and updates.
- First board model is a standard 6×5 grid with the six ordinary colors and hearts. Orb modifiers such as locks and enhancements are represented separately from color.
- The first solve objective maximizes match groups, then prefers the shorter drag path. `SolveGoal` and the move bound are separate so later conditions and complexity options can be added.
- Screen capture uses a user-approved `MediaProjection` session and a capture action in its ongoing notification. Draw-over-apps access is used by the separate touch-through route overlay.
- An uncertain or unsupported board is rejected for recapture. The first-pass image recognizer still needs calibration against real PAD captures.

## Compatibility consideration

Keep `minSdk` at 26 for now. This does not restrict features on the Pixel 9. If a future feature or library requires a newer Android API, either guard that feature so it only runs on supported Android versions or raise `minSdk` if the app no longer needs to support API 26 devices. Check library minimum SDK requirements when adding dependencies.

## Later decisions

- Add screenshot selection from the phone.
- Recognize hazards, locks, and enhanced-orb markers.
- Define the complexity slider's path-cost measure and solve-goal options.
- Review Android 17 (API 37) behavior changes as features and permissions are added.
- Replace the placeholder application ID before publishing.
