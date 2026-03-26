# Code Coverage — Lexicon Server

**Threshold:** 70% line coverage (enforced in CI)

## Local

Generate coverage report locally:

```bash
./gradlew test
./gradlew jacocoTestReport
```

Report location: `build/reports/jacoco/test/index.html`

Open in browser:
```bash
open build/reports/jacoco/test/index.html
```

## CI/CD

Coverage is measured and reported on every push and PR:

- ✅ Coverage >= 70% — PR checks pass
- ⚠️ Coverage < 70% — PR checks fail (must fix before merge)
- 📊 Coverage report uploaded to GitHub Actions artifacts
- 💬 Coverage percentage commented on PR

## What's Excluded

The following are excluded from coverage calculations (too noisy, low value):

- Generated code (entity classes, DTOs, configs)
- Spring Boot application class
- Test fixtures and utilities

## Critical Paths (Must Have Tests)

- `authentication/*` — OAuth flows, token generation
- `users/*` — User CRUD, profile endpoints
- `words/*` — Word CRUD, batch operations
- `leaderboard/*` — Ranking calculations
- `features/*` — Feature flag logic

## Coverage Metrics by Package

After running tests, check `build/reports/jacoco/test/index.html`:

| Package | Target | Status |
|---------|--------|--------|
| `com.alirezaiyan.vokab.server.controller` | 80%+ | ? |
| `com.alirezaiyan.vokab.server.service` | 80%+ | ? |
| `com.alirezaiyan.vokab.server.repository` | 70%+ | ? |
| `com.alirezaiyan.vokab.server.security` | 90%+ | ? |

---

**Last updated:** 2026-03-26  
**Created by:** Claw
