# PAD Solver

A native Android helper for Puzzle & Dragons (PAD) and Puzzle & Dragons 0 (PAD 0). Capture a board or choose a saved screenshot, find a suggested route, and display it over the game while you move the orb yourself.

## Install

Requires **Android 8.0 or later**.

1. Open [Releases](https://github.com/styoung88/PAD-Solver/releases) and download the `PAD-Solver-<version>.apk` attachment from a published release.
2. Open the APK on your phone. If Android asks, allow the browser or file manager you used to install unknown apps, then tap **Install**.
3. Open **PAD Solver**. Overlay and screen-sharing access are requested when you use those features.

If no release is published, you can [build a debug APK from source](docs/DEVELOPMENT.md#build-from-source). For Android's installation settings, see [installing apps from other sources](https://developer.android.com/distribute/marketing-tools/alternative-distribution).

## Use the floating toolbar

1. Open solve settings with the gear button and choose **Game: PAD / PAD 0**, your combo target, color or match-pattern preferences, and orb movement count. PAD 0 also offers team-skill presets.
2. Tap **Open PAD overlay** or **Open PAD 0 overlay**. If prompted, allow PAD Solver to display over other apps. The floating toolbar opens over the selected game.
3. With a clear board visible, tap the toolbar's camera button. Approve Android's screen-sharing prompt when starting a capture session.
4. The app reads the board, solves it, and displays the route automatically. Follow it manually, starting at green and ending at red. Capture again whenever the board changes.

The toolbar hides while reading a fresh frame so it is not included in the capture. Use the eye button to hide or show a route, the gear button to adjust settings, and **×** to close the overlay. Stopping screen sharing through Android's controls leaves the toolbar available; the next capture asks for consent again.

## Solve a saved screenshot

1. Tap **Choose existing screenshot** and select a clear battle screenshot with the whole board visible.
2. Review the detected orbs, open **Solve settings**, and tap **Apply and solve**.
3. When the requirements are met, tap **Show route in PAD** or **Show route in PAD 0** to display the route over the game.

Changing games clears the current board and route and ends screen sharing. Each game keeps its own solve preferences.

## Supported boards and limits

- PAD: 5×4, 6×5, and 7×6 boards; five attack colors, hearts, hazards, and enhanced/weakened markers.
- PAD 0: calibrated 6×5 boards, ordinary orbs, hearts, jammers, combo-bonus badges, and eight team-skill presets. See [PAD 0 support and remaining checks](docs/PAD_ZERO_SUPPORT.md).
- Recognition depends on board layout, artwork, and image quality. Unclear boards ask for another capture; additional layouts and orb skins may need calibration.
- The solver models deterministic cascades with no random skyfall and uses a bounded search. It does not guarantee the best possible route or model the in-game timer.
- Damage is a relative board estimate. Creature stats, awakenings, enemy effects, healing, poison damage, and bomb explosions are not modeled. Lock badges are ignored.

The overlay displays instructions without automating game input. Solver rules and implementation details are described in [Project architecture and solver behavior](docs/PROJECT_NOTES.md).

## Privacy

Board images are processed on the device. The app has no accounts, advertising, analytics, or network uploads. The screenshot picker only reads the image you select and needs no photo-library permission. Captures stay in private app storage and are excluded from backup; solve preferences can be backed up by Android.

Notification permission is not required to use the toolbar or capture a board. On Android 13 and later, allowing notifications makes the ongoing service notifications visible in the notification drawer. See the [included privacy policy](app/src/main/res/raw/privacy_policy.txt) for storage, backup, and deletion details.

## Development and feedback

- [Build, test, install, and create signed APKs](docs/DEVELOPMENT.md)
- [Contribution and commit guidelines](CONTRIBUTING.md)
- [Report a bug or request a feature](https://github.com/styoung88/PAD-Solver/issues)
- [Orb reference sources and recognition rules](docs/ORB_REFERENCES.md)

The app is written in Kotlin with Jetpack Compose. Release installations are named **PAD Solver**; debug installations are named **PAD Solver (Dev)** and use a separate application ID so both can be installed together.

Puzzle & Dragons artwork belongs to its respective owners. Reference-image provenance is documented in the linked recognition notes and [test fixture notes](app/src/androidTest/assets/README.md).
