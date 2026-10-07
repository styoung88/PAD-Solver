# Development instructions

Use Conventional Commits for all commit messages: `<type>[optional scope]: <description>`, such as `feat: add a feature`, `fix: correct a bug`, or `docs: update development instructions`. See `CONTRIBUTING.md` for details.

Keep PR descriptions and comments concise: cover only the files changed and the overall purpose of the change. Omit validation results and methods, publishing checklists, future work, conversation history, progress logs, and troubleshooting unless the user explicitly requests them.

Keep agent-only Markdown concise: include only actionable instructions or necessary context useful across tasks. Keep one-time findings and validation results out of these files; report them in chat when relevant.

Create a dedicated feature branch from an up-to-date `main` before starting each new feature. Use the `codex/` branch prefix unless the user specifies another name. Continue related work on its existing feature branch.

When the user requests merging a branch, include branch cleanup as part of the request. Commit and push the completed changes, merge into `main`, push `main`, then delete the fully merged feature branch both locally and on the remote. Finish on `main` and report the merge and cleanup status. Preserve uncommitted work and do not delete a branch before its changes are safely merged and pushed.

After changes that affect the app, always build and install the updated debug app on the user's connected phone using `scripts/install-debug-to-device.ps1`. The user has authorized these routine device updates; do not ask for confirmation each time. Report whether installation succeeded. If the phone is disconnected or unauthorized, report the blocker. If multiple physical devices are connected, ask which phone to use.

See `docs/DEVELOPMENT.md` for build and installation details.
