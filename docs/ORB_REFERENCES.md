# Orb recognition references

The detector combines ordinary color-ring sampling with central-symbol luminance templates for jammers, poison, lethal poison, and bombs. Templates use a 13×13 grid at 0.052 cell-width spacing, centered in a 50×50 cell; luminance is quantized to 16 levels and normalized before comparison. Both ordinary and hazard symbols compete so similar colors alone do not establish a hazard.

Reference artwork and test images:

- `drop_01.png` through `drop_10.png`: [PAD orb artwork](https://github.com/Mapaler/PADDashFormation/tree/master/images/project%20file/orbs), also the source of the symbol samples in `OrbSymbolTemplates.kt`.
- `J*.png`: independent jammer cell samples from [OrbAI's image dataset](https://github.com/Eclipseop/OrbAI/tree/master/data).
- `locked.jpg` and `bomb.jpg`: game-art examples from [AppMedia's board mechanics guide](https://appmedia.jp/pazudora/789324).
- `weakened.png`: game-board crop from [GameWith's weakened-orb guide](https://xn--0ck4aw2h.gamewith.jp/article/show/361312).

Puzzle & Dragons artwork belongs to its respective owners. Reference images are used for symbol identification and recognition tests; application board graphics are drawn separately.

Hazards use normal matching and deterministic gravity in this detection-focused implementation. Poison damage, bomb explosions, and other special effects are deferred. Weakened matches adjust the existing board-only attack estimate: each enhanced orb cancels one weakened orb, one remaining weakened orb halves that match's damage, and two or more reduce it to zero. The match still counts as a combo. See [the mechanic discussion](https://www.reddit.com/r/PuzzleAndDragons/comments/1bv95b7).
