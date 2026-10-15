# PAD 0 support checklist

Add support in stages: game selection, shared recognition classes, board geometry and orb artwork, combo-bonus orbs, then PAD 0 team skills. Keep the existing PAD behavior covered throughout.

The reported differences are board dimensions or placement, hazard artwork (especially jammers), orb modifiers that add combos when matched, and a small set of team skills. Confirm the exact geometry and rules from PAD 0 captures before implementing them.

## 1. Select the game

- [x] Add a saved **Game: PAD / PAD 0** choice, defaulting existing installations to PAD.
- [x] Use that selection to choose the recognition flow; keep grid size detection independent of game selection.
- [x] Apply the same selection to the screenshot picker, live capture, and route display. Snapshot it for each operation; changing games must cancel pending work and clear incompatible board/route results.
- [x] Make the selected game visible in settings and board review. Game-specific text and any game-opening action must follow the saved choice.

The player's choice is authoritative. Both choices remain available without installed-game discovery, foreground-app inference, or image-based game identification.

Done when every capture/solve flow consistently uses the manually selected game.

Both game choices support capture, solving, and route display for their calibrated layouts.

## 2. Extract shared recognition classes

- [x] Extract a `BaseBoardRecognizer` with the common recognition flow: image preparation, candidate evaluation, grid fitting, cell sampling, uncertainty handling, and construction of `BoardRecognition`.
- [x] Keep PAD-specific geometry in `PadBoardRecognizer`; add `PadZeroBoardRecognizer` for PAD 0. Children provide supported sizes, layout/search parameters, and narrow overrides where the geometry algorithm actually differs.
- [x] Extract a `BaseOrbRecognizer` for shared ordinary color sampling, hazard comparison, alignment retries, and confidence evaluation. `PadOrbRecognizer` and `PadZeroOrbRecognizer` supply their palettes, hazard references, thresholds, and modifier detection.
- [x] Give each board recognizer its corresponding orb recognizer. Use a small factory keyed by the saved game choice so callers share one recognition API.
- [x] Keep `Board`, `Orb`, `BoardRecognition`, matching/gravity, and route geometry shared. Gameplay and damage rules belong in their own evaluators, separately from image recognition.
- [x] Refactor the existing PAD implementation first and verify its existing image fixtures and route alignment before adding PAD 0-specific behavior.

Recognition structure:

```text
BaseBoardRecognizer
  PadBoardRecognizer     uses PadOrbRecognizer
  PadZeroBoardRecognizer uses PadZeroOrbRecognizer

BaseOrbRecognizer
  PadOrbRecognizer
  PadZeroOrbRecognizer
```

Keep both hierarchies shallow. Shared algorithms stay in the base classes; simple variant differences use parameters or reference data, and specialized behavior uses focused overrides.

`BoardGeometry` supplies layout/search calibration, and `OrbSamplingParameters` supplies sampling positions and confidence requirements. `HazardSymbolMatcher` uses variant references in `PadHazardTemplates` and `PadZeroOrbReferences`. Each child supplies its own geometry, palette, hazards, and markers. Ordinary orb recognition uses color sampling exclusively.

Done when existing PAD recognition uses the shared bases without changing its results, and callers can select a variant through the common API.

## 3. Recognize the board geometry

- [x] Annotate the supplied full battle screenshots with grid size, outer bounds, and cell centers.
- [x] Measure the layout differences; support the confirmed 6×5 size.
- [x] Configure PAD 0 geometry, including inset edges and bright grid lines. Fit row and column pitch independently.
- [x] Verify saved screenshot recognition and rendered route corners at multiple capture/display scales on the phone.
- [ ] Verify alignment over a live PAD 0 battle through screen sharing. Collect additional layouts or board-size effects when observed.

The supplied 574×1280 captures have approximate board bounds `(10, 810)–(563, 1270)`, horizontal pitch 92.2 px and vertical pitch 92 px. Fixture annotations are in `app/src/androidTest/assets/pad-zero/annotations.json`.

Done when ordinary PAD 0 boards are recognized with accurate cell centers and the displayed route stays aligned.

## 4. Recognize orb artwork and hazards

- [x] Label ordinary orbs, hearts, jammers, and combo-bonus badges from the supplied captures.
- [x] Add PAD 0 artwork references, thresholds, and marker detection using the shared recognition flow.
- [x] Recognize combo bonuses independently of base color. Ignore lock badges because they do not affect matching or damage.
- [x] Display hazards/modifiers in board review and reject unclear color/grid evidence; cover both games with image fixtures. Ordinary orbs use color sampling; artwork comparisons distinguish hazards and modifier badges.

Other PAD 0 hazards and modifiers need their own reference captures before support is added.

Done when the supported PAD 0 orb types and markers are distinguishable, with uncertain images rejected for recapture.

## 5. Model combo-bonus orbs

- [x] Confirm +1 combo per marked orb included in a match, as specified by the user. Leader-specific caps and conditions belong to phase 6.
- [x] Preserve bonus flags through swaps, gravity, and solver cache keys.
- [x] Track physical matches and bonus combos separately; count each cleared marker once and leave unmatched markers inactive.
- [x] Include bonuses in combo objectives, minimums, inventory bounds, settings, results, overlays, and the existing relative damage estimate. Keep pattern checks tied to physical matches.
- [x] Cover multiple marked orbs, separate matches, cascades, and targets reachable only through bonuses.

Done when routes that consume bonus orbs are scored correctly and valid combo targets are not rejected by the old physical-match limit.

## 6. Include PAD 0 team skills in route scoring

- [x] Record the eight supplied team skills from the team-screen descriptions.
- [x] Add a saved PAD 0 **Team skill** preset selector with **None** as the default. Keep team attacking attributes separate from matched orb colors.
- [x] Separate game-specific damage rules from match analysis. Use effective combos for damage and combo-based skill activation; preserve PAD's existing board-only estimate.
- [x] Evaluate conditional team attack multipliers during route ranking. Carry the same configuration into manual and automatic overlay solves.
- [x] Add an optional toggle labeled **Damage reduction** where its activation condition is separate from attack activation. When enabled, require the route to satisfy that condition; show whether it activates in the result.
- [x] Display relative damage and team-skill activation clearly.
- [ ] Compare representative activations against live game results, including attributes across cascades and linked heart/hazard groups.

Exclude time-remaining bonuses from route scoring; use the existing **Orb movement** control to suit player speed. Defer healing/recovery and actual creature-stat damage calculations. Fixed bonus attacks remain separate from combo bonuses, and Combo Orb appearance chance does not grant speculative combos to the current solve.

`DamageEvaluator` shares the board contribution; `PadDamageEvaluator` retains PAD's estimate, and `PadZeroDamageEvaluator` applies the selected team skill. Tiered multipliers use the highest qualifying tier; independent clauses multiply. With a skill selected and no color focus, sum attacking-color contributions with equal weights. Multicolor restricts these colors to the configured team attributes.

Rules modeled from the supplied descriptions:

| Preset | ATK condition (excluding time) | Damage reduction | Fixed bonus attack |
| --- | --- | --- | --- |
| Starting team | 3/4/5/6+ combos: ×5/8/11/14 | None | None |
| Combo | 7/8+ combos: ×12/15 | None | None |
| Linked Orb Erasure | 6/7+ linked orbs: ×10/15 | 45% at 5+ combos | None |
| Multicolor | 4/5 attacking attributes: ×14/16 | 45% at 5+ combos | None |
| 5 Color Erasure | 5 attacking attributes: ×25 | 50% at 5 attributes | 10 at 5 attributes |
| Link & Combo | 4+ linked orbs: ×9; 6+ combos: another ×2 | 50% at 6+ combos | None |
| Expert Combo | 6+ combos: ×10 | 50% at 6+ combos | None |
| Expert Multicolor | 5 attacking attributes: ×10 | 50% at 6+ combos | 10 at 5 attributes |

Count matched attacking attributes across the resolved turn. Multicolor intersects these with configured team coverage; Expert Multicolor and 5 Color Erasure always require all five attack colors and ignore saved attribute selections. Hearts/hazards never add an attacking attribute. Linked-orb conditions use the largest single cleared group of any orb type, rather than combining separate groups. Cascade and linked-group interpretations still need live-game comparison. Bonus combos affect combo conditions only.

Done when the selected team skill and optional damage reduction requirement affect route selection consistently across capture flows.

## Existing code entry points

| Area | Files |
| --- | --- |
| Selection, persistence, launch | `MainActivity.kt`, `SolveSettings.kt`, `SolveSettingsStore.kt`, `ui/BoardScreens.kt` |
| Shared capture/solve flow | `BoardViewModel.kt`, `capture/SolutionOverlayService.kt` |
| Shared recognition | `capture/BaseBoardRecognizer.kt`, `capture/BaseOrbRecognizer.kt`, `capture/BoardRecognizer.kt` |
| Variant geometry and artwork | `capture/PadBoardRecognizer.kt`, `capture/PadZeroBoardRecognizer.kt`, `capture/PadOrbRecognizer.kt`, `capture/PadZeroOrbRecognizer.kt`, `capture/PadHazardTemplates.kt` |
| Orb state, clears, cascades | `board/Board.kt`, `board/BoardAnalyzer.kt`, `solver/BoardSolver.kt` |
| Combo limits, objectives, damage | `solver/SolveGoal.kt`, `solver/DamageEvaluator.kt`, `solver/PadZeroTeamSkill.kt`, `solver/SolveSettings.kt`, `ui/BoardScreens.kt` |

Each app stage needs focused regression checks for both games and a debug build installed with `scripts/install-debug-to-device.ps1`, following [the development workflow](DEVELOPMENT.md).
