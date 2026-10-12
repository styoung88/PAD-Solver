# Orb recognition references

The PAD detector uses color-ring sampling for ordinary orbs and central-symbol luminance templates for jammers, poison, lethal poison, and bombs. Only hazards have artwork templates. Templates use a 13×13 grid at 0.052 cell-width spacing, centered in a 50×50 cell; luminance is quantized to 16 levels and normalized before comparison. Hazard matches require sufficient correlation and a margin over competing hazard types.

After fitting the grid, hazard matching also searches offsets up to 0.08 cell widths and scales from 0.9 to 1.1, with a stronger jammer confidence requirement for this wider search.

Grid candidates are ranked by combined color and spacing evidence, including partially recognized grids. Unresolved cells are retried at nearby sample positions after geometry is fitted. A stronger grid with unreadable cells requires another capture instead of falling back to a weaker region of battle artwork.

Reference artwork and test images:

- `drop_01.png` through `drop_10.png`: [PAD orb artwork](https://github.com/Mapaler/PADDashFormation/tree/master/images/project%20file/orbs) used as test fixtures. Only `drop_07.png` through `drop_10.png` supply hazard samples in `PadHazardTemplates.kt`.
- `J*.png`: independent jammer cell samples from [OrbAI's image dataset](https://github.com/Eclipseop/OrbAI/tree/master/data).
- `locked.jpg` and `bomb.jpg`: game-art examples from [AppMedia's board mechanics guide](https://appmedia.jp/pazudora/789324).
- `weakened.png`: game-board crop from [GameWith's weakened-orb guide](https://xn--0ck4aw2h.gamewith.jp/article/show/361312).
- `jammer-capture.jpg` and `poison-route-capture.jpg`: user-supplied game screenshots, including a route overlay over poison orbs.
- `seven-by-six-route-capture.jpg`: user-supplied framed 7×6 board with a route overlay and battle artwork above it.

PAD 0 uses the user-supplied fixtures in `app/src/androidTest/assets/pad-zero`. `annotations.json` records independently labeled colors, bonus badges, bounds, and cell centers. Ordinary orbs use color-ring sampling without artwork comparisons. `PadZeroOrbReferences.kt` contains 13×13 luminance descriptors after the shared 2×2 image averaging. Jammer comparisons use samples from `1000013017.jpg` at 0.052 cell-width spacing, excluding upper-corner badge artwork. Bonus markers use separate descriptors at 0.035 spacing near offset `(0.30, -0.26)`. Only the diagonal badge and its rim are compared, excluding surrounding orb pixels so the underlying color does not suppress the marker. `1000013023.jpg` covers badges on heart, fire, and dark orbs. Hazard references currently cover PAD 0 jammers; other hazards need their own captures. Orb lock badges are ignored in both games and have no stored modifier or solver state.

Puzzle & Dragons artwork belongs to its respective owners. Reference images are used for symbol identification and recognition tests; application board graphics are drawn separately.

Hazards use normal matching and deterministic gravity in this detection-focused implementation. Poison damage, bomb explosions, and other special effects are deferred. Weakened matches adjust the existing board-only attack estimate: each enhanced orb cancels one weakened orb, one remaining weakened orb halves that match's damage, and two or more reduce it to zero. The match still counts as a combo. See [the mechanic discussion](https://www.reddit.com/r/PuzzleAndDragons/comments/1bv95b7).
