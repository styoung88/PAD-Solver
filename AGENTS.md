# Development instructions

After changes that affect the app, always build and install the updated debug app on the user's connected phone using `scripts/install-debug-to-device.ps1`. The user has authorized these routine device updates; do not ask for confirmation each time. Report whether installation succeeded. If the phone is disconnected or unauthorized, report the blocker. If multiple physical devices are connected, ask which phone to use.

See `docs/DEVELOPMENT.md` for build and installation details.
