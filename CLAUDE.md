# Vokab Server — Claude Code Context

Vokab Server is a **Spring Boot (Kotlin)** backend for a vocabulary learning mobile app. It provides spaced-repetition word management, user authentication (Google/Apple), streaks, leaderboards, AI vocabulary extraction, push notifications, and subscription management.

See `.claude/` for detailed references:
- [Architecture & Domain Model](.claude/architecture.md)
- [API Endpoints](.claude/api.md)
- [Tech Stack & Dependencies](.claude/tech-stack.md)
- [Configuration & Environment](.claude/configuration.md)

---

## Project Layout

```
src/main/kotlin/com/alirezaiyan/vokab/server/
├── config/          # Spring config beans (JWT, Firebase, CORS, rate limiting)
├── domain/
│   ├── entity/      # JPA entities (13 total)
│   └── repository/  # Spring Data JPA repositories + Projections.kt
├── presentation/
│   ├── controller/  # REST controllers (17 total)
│   └── dto/         # Request/response DTOs
├── service/         # Business logic (27 services)
├── security/        # JWT filter, SecurityConfig, token providers
├── scheduler/       # Scheduled jobs (streak reminders, insight generation)
├── task/            # Async jobs
└── exception/       # Global exception handling

src/main/resources/
├── application.yml
└── db/migration/    # Flyway V1–V14
```

---

## Rules

### Architecture

- **Strict layering.** Controllers call services only. Services call repositories only. No repository calls from controllers, no HTTP logic in services.
- **No business logic in controllers.** Controllers handle HTTP binding/validation, call one service method, and return `ResponseEntity<ApiResponse<T>>`.
- **No business logic in repositories.** Repositories are pure data-access. Complex queries belong in JPQL/native SQL on the repository, not in the service.

### Kotlin Style

- Use **constructor injection** with `private val` — never `@Autowired` on fields.
- Use **`data class`** for all DTOs and value objects.
- Use **extension functions** at file level (not inside classes) for entity-to-DTO mapping, e.g., `private fun Word.toDto(): WordDto`.
- Use **backtick test names** for readability: `` fun `should return 404 when word not found`() ``.
- Prefer **`require()` / `check()`** over `if (x) throw ...` for preconditions.
- Logger is always a **package-level `private val`**: `private val logger = KotlinLogging.logger {}`.
- Use lambda logger syntax: `logger.error(e) { "Failed: ${variable}" }` — never string interpolation outside the lambda.

### API Design

- **All responses** use `ApiResponse<T>` wrapper.
- **HTTP status codes:** 200 OK, 201 Created, 204 No Content (no response body), 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 500 Internal Server Error.
- **Validation** with `@field:NotBlank`, `@field:Email`, etc. on request DTOs. Use `@Valid @RequestBody` in controllers.
- **Ownership** is always checked in the service layer (`require(entity.userId == user.id) { "Forbidden" }`). Never trust client-supplied IDs without verification.
- **Pageable** for list endpoints that may grow large. Accept `page` and `size` query params.
- Never expose internal error messages to clients (`include-message: never` in config). Log the full exception server-side.

### Error Handling

- Throw **standard exception types** that `GlobalExceptionHandler` already handles:
  - `IllegalArgumentException` → 400
  - `NoSuchElementException` → 404
  - `AuthenticationException` / `BadCredentialsException` → 401
- Add a new `@ExceptionHandler` only when a new exception type doesn't map to an existing handler.
- Controllers use **try-catch only for auth endpoints** where specific error messages are safe to return. All other controllers let exceptions propagate to `GlobalExceptionHandler`.

### Security

- **Never log sensitive data**: no passwords, tokens, full JWTs, email addresses in log lines.
- **Rate limiting** via Bucket4j is applied at the controller level for sensitive endpoints (auth, AI).
- JWT filter sets `SecurityContext` — never manually parse JWT tokens in controllers or services.
- Public endpoints must be explicitly listed in `SecurityConfig.kt`'s `permitAll()` block.
- Apple/RevenueCat webhook signatures must be validated before processing payloads.

### Database & Migrations

- **Flyway manages all schema changes.** `ddl-auto` is `validate` — never `create`, `update`, or `create-drop`.
- New file for every schema change: `V{N}__short_description.sql`. Never edit an existing migration.
- Use `BIGSERIAL PRIMARY KEY` for IDs. Use `TIMESTAMP` for time columns with `DEFAULT CURRENT_TIMESTAMP`.
- Add `NOT NULL` unless null is explicitly meaningful. Add `UNIQUE` constraints for business keys.
- Add **indexes** on columns used in `WHERE` and `JOIN` clauses that are frequently queried.
- `user_id` in `app_events` and `audit_log` has **no FK constraint** intentionally — analytics/audit must survive account deletion (GDPR).
- Use `UNIQUE(user_id, client_session_id)` pattern to prevent duplicate syncs.

### Transactions

- `@Transactional` on all write service methods.
- `@Transactional(readOnly = true)` on all read service methods.
- Avoid long transactions — don't call external APIs (OpenRouter, Firebase) inside a `@Transactional` block.

### Analytics & Events

- `EventService.track(...)` must **never throw** — wrap in try/catch internally.
- Fire-and-forget pattern: analytics failures must not fail the primary operation.
- `AppEvent.properties` is a JSON string — use Jackson to serialize maps before persisting.

### Testing

- Use **MockK** (`mockk()`, `every { } returns`) — never Mockito.
- Create test entities via **private factory functions** inside the test class, not inline construction everywhere.
- Use `@BeforeEach` to instantiate services with mocked dependencies (not Spring context unless integration test).
- Test names use backticks describing the behavior: `` `should calculate streak correctly when activity gaps exist` ``.
- Test one behavior per test. Avoid multiple unrelated assertions in one test.

### Push Notifications

- Always check `PushToken.active` before sending. Never send to inactive tokens.
- Token send failures must be caught and logged — never propagate to the caller.
- Deactivate tokens that return `NOT_REGISTERED` or equivalent error from FCM/APNs.

---

## Common Tasks

### Add a new endpoint
1. Add request/response DTOs in `presentation/dto/`
2. Implement business logic in the relevant `service/`
3. Add controller method with `@Valid @RequestBody` and `@AuthenticationPrincipal User`
4. If new table needed: write `V{N}__description.sql` in `db/migration/`

### Add a new entity
1. Create entity in `domain/entity/` with `@Entity`, `@Table`, `@Id`, `@GeneratedValue`
2. Create repository in `domain/repository/` extending `JpaRepository<Entity, Long>`
3. Write Flyway migration SQL (`V{N}__create_{table}.sql`)

### Add a new analytics event
1. Define event name constant (e.g., `"word_added"`) in `EventService` or a constants file
2. Call `eventService.track(user.id, eventName, properties, platform, appVersion)` from the relevant service
3. Wrap in try/catch — never let it fail the primary operation

### Run locally
```bash
./gradlew bootRun   # Uses H2 in-memory DB by default
```

### Run tests
```bash
./gradlew test
```

---

## Sub-agents

Use these specialized agents for focused tasks:

| Agent | Trigger |
|---|---|
| `kotlin-reviewer` | Review Kotlin/Spring code for correctness, security, and conventions |
| `migration-writer` | Write a new Flyway SQL migration |
| `test-writer` | Write JUnit 5 + MockK tests for a service or controller |
| `api-designer` | Design a new REST endpoint following project conventions |
