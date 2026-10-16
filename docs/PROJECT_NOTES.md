# Project architecture and solver behavior

PAD Solver is a Kotlin and Jetpack Compose Android app with one `app` module. It supports Android 8.0 and later (`minSdk` 26), and compiles and targets API 37. The Gradle wrapper, plugins, and dependencies are pinned in the repository.

## Code layout

| Area | Location under `app/src/main/java/com/example/phoneapp/` |
| --- | --- |
| App lifecycle and board state | `MainActivity.kt`, `BoardViewModel.kt` |
| Compose screens and solve settings | `ui/BoardScreens.kt`, `solver/SolveSettings.kt` |
| Capture, image recognition, and floating controls | `capture/` |
| Board model, match analysis, and route drawing | `board/` |
| Route search and relative damage evaluation | `solver/` |
| Game selection | `game/GameVariant.kt` |

JVM tests live in `app/src/test`; device tests and image fixtures live in `app/src/androidTest`. See the [development guide](DEVELOPMENT.md) for build and test commands.

## Capture and recognition

The floating toolbar and route display use `SolutionOverlayService`. A separate `ScreenCaptureService` owns the user-approved `MediaProjection` session. Stopping sharing leaves the toolbar available. Each capture clears the old route, hides the toolbar, and uses a fresh image reader to avoid reading a stale or overlay-contaminated frame.

The system photo picker supplies saved images without photo-library permission. Both capture paths use the manually selected game. Changing games cancels pending work, clears the board and route, and ends the previous screen-sharing session.

`BaseBoardRecognizer` and `BaseOrbRecognizer` share grid fitting, sampling, and uncertainty handling. PAD and PAD 0 subclasses provide geometry, palettes, and reference descriptors. PAD supports 5×4, 6×5, and 7×6 boards; PAD 0 is calibrated for the reference 6×5 layout. Unreadable boards are rejected for another capture.

Ordinary orbs use color sampling. Hazards use central-symbol references, and enhanced, weakened, and combo-bonus markers use separate checks. Lock badges are ignored and have no stored modifier state. See [orb references](ORB_REFERENCES.md) for image provenance and recognition parameters.

## Route search

The solver uses bounded beam search with deterministic gravity cascades and no random skyfall. Combo minimums, attacking-color pattern requirements, and enabled damage reduction conditions must be met first. Qualifying routes maximize the selected color's relative damage, or total combos when no color or team skill is selected.

A pattern requirement means at least one qualifying match, rather than maximizing pattern count. Score ties prefer fewer moves, then fewer turns. Pattern progress guides the search frontier but does not rank final solutions. Requests proven impossible by orb inventory or board space return immediately for adjustment.

Color-focused solves run two bounded searches concurrently, independently of the selected combo minimum, then share their candidates. An additional search targets the requested requirements if needed. Equal results remain deterministic across workers. Search limits are 30 moves for 5×4, 40 for 6×5, and 50 for 7×6. Row patterns span the full board width.

The search does not use the in-game timer or guarantee a global optimum. The orb movement count controls path length.

## Relative damage and PAD 0 skills

The board-only color estimate includes the 6% per remaining enhanced orb bonus and weakened-orb penalties after enhancement cancellation. Each enhanced orb cancels one weakened orb; one remaining weakened orb halves that match's damage, and two or more reduce it to zero. The match still counts as a combo.

PAD 0 adds one combo for each matched combo-bonus orb, including during cascades. These bonuses affect combo targets and results; pattern requirements still depend on physical matches.

PAD 0 offers eight team-skill presets, with **None** as the default. Team ATK multipliers affect relative damage ranking. Multicolor uses the configured team's main and sub-attributes; Expert Multicolor and 5 Color Erasure require all five attack colors. Hearts and hazards do not count as attacking attributes.

With a PAD 0 skill selected and no color focus, attacking colors have equal damage weight. **Damage reduction** can require activation for skills whose reduction condition is separate from attack activation. Results show the team ATK multiplier, active reduction, and fixed bonus attack separately. See [PAD 0 support](PAD_ZERO_SUPPORT.md) for the modeled skill conditions and remaining live-game checks.

Creature stats, awakenings, enemy modifiers, time bonuses, HP, recovery, poison damage, and bomb explosions are not modeled. Combo Orb appearance chance does not add speculative combos to the current board.
