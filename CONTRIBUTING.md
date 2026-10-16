# Contributing

## Report an issue

Use [GitHub Issues](https://github.com/styoung88/PAD-Solver/issues) for bugs and feature requests. For a bug, include the app version, Android version, selected game, board size, steps to reproduce, and what you expected to happen. For recognition problems, include a screenshot when possible and remove any personal information before sharing it.

## Make a change

Start a focused branch from an up-to-date `main`; use the `codex/` prefix for agent-created branches. Open a pull request against `main` and describe the changed files and overall purpose concisely.

Follow the [development guide](docs/DEVELOPMENT.md) for setup and checks. Changes to recognition, scoring, or capture behavior should include regression coverage for both games where applicable. For app changes, also check an updated debug build on a connected device.

Keep generated APKs, local SDK configuration, temporary captures, and signing credentials out of commits. Intentional regression images belong in `app/src/androidTest/assets`, with provenance documented in the fixture notes. The release configuration template can be committed; real `release.properties` files and keystores must stay private.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/) for commit messages:

```text
<type>[optional scope]: <short description>
```

Common types for this project:

- `feat`: add user-visible functionality
- `fix`: correct a bug
- `docs`: documentation-only changes
- `refactor`: restructure code without changing behavior
- `test`: add or update tests
- `build`: build configuration and dependencies
- `chore`: maintenance and project setup

Examples: `feat(settings): add theme selection`, `fix: preserve form values after rotation`, `build: update Android Gradle plugin`.

Use an optional `!` after the type or scope for a breaking change, and include a `BREAKING CHANGE:` footer when more explanation is needed.
