# Vokab Server — Claude Code Context

Spring Boot (Kotlin) backend for the Lexicon vocabulary learning app. Spaced-repetition word management, Google/Apple auth, streaks, leaderboards, AI vocabulary extraction, smart push notifications, email system, tags, Word Rush game, and subscription management.

## Rules

Project rules live in `.claude/rules/` as modular, path-scoped files — loaded only when editing the matching files to keep context lean.

| Rule                                                                      | Loaded when editing                                                  |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| [lexicon-server-project.md](.claude/rules/lexicon-server-project.md)      | Every session — tech stack, layout, commands, sub-agents             |
| [lexicon-server-kotlin.md](.claude/rules/lexicon-server-kotlin.md)        | `src/main/kotlin/**/*.kt` — layering, DI, logging, transactions      |
| [lexicon-server-api.md](.claude/rules/lexicon-server-api.md)              | `presentation/**`, `exception/**` — REST conventions, ApiResponse     |
| [lexicon-server-flyway.md](.claude/rules/lexicon-server-flyway.md)        | `db/migration/**`, `domain/entity/**` — migration rules, schema      |
| [lexicon-server-security.md](.claude/rules/lexicon-server-security.md)    | `security/**`, `config/**`, auth/webhook controllers                 |
| [lexicon-server-testing.md](.claude/rules/lexicon-server-testing.md)      | `src/test/**` — MockK, factories, controller tests                   |

## Detailed References

- [Architecture & Domain Model](.claude/architecture.md)
- [API Endpoints](.claude/api.md)
- [Tech Stack & Dependencies](.claude/tech-stack.md)
- [Configuration & Environment](.claude/configuration.md)

## Sub-agents

| Agent              | Trigger                                                        |
| ------------------ | -------------------------------------------------------------- |
| `kotlin-reviewer`  | Review Kotlin/Spring code for correctness and conventions      |
| `migration-writer` | Write a new Flyway SQL migration                               |
| `test-writer`      | Write JUnit 5 + MockK tests for a service or controller       |
| `api-designer`     | Design a new REST endpoint following project conventions      |
| `e2e`              | Feature or bug fix spanning backend + KMP client (`~/projects/Lexicon`) |
