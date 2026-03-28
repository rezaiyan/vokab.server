# Changelog

All notable changes to Vokab Server are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

---

## [1.0.0] - 2026-03-28

### Added
- Google and Apple Sign-In authentication with JWT token issuance
- Spaced-repetition word management (CRUD, batch delete, tagging)
- Word sync endpoint for offline-first mobile clients
- Streak tracking with daily activity detection
- Leaderboard across all users
- AI vocabulary extraction via OpenRouter (AiController)
- Word Rush mini-game with session sync, analytics, and daily insights
- Push notifications via Firebase Cloud Messaging (FCM)
- RevenueCat webhook integration for subscription management
- Apple App Store server-to-server notifications
- Account deletion with full cascade cleanup (GDPR)
- Flyway-managed schema migrations (V1–V28)
- JaCoCo code coverage reporting with 80% threshold enforcement
- Rate limiting on auth and AI endpoints via Bucket4j
- Health check endpoint (`/actuator/health`)

[Unreleased]: https://github.com/alirezaiyan/lexicon.server/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/alirezaiyan/lexicon.server/releases/tag/v1.0.0
