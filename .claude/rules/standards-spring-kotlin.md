---
paths:
  - "**/*.kt"
---

# Spring Boot Kotlin Standards (lexicon.server)

These rules extend the global Kotlin standards specifically for this Spring Boot project.

## Layering (STRICT)

```
Controller → Service → Repository
```

- Controllers handle HTTP binding, validation, and call **one** service method
- Services contain all business logic — they call repositories, never other controllers
- Repositories are pure data-access — complex queries in JPQL/native SQL, not in services
- No `@Repository` or `@Entity` imports in service layer
- No business logic in controllers

## Response Format

Every endpoint returns `ResponseEntity<ApiResponse<T>>` — no raw types.

```kotlin
// Correct
@GetMapping("/{id}")
fun getWord(@PathVariable id: Long): ResponseEntity<ApiResponse<WordDto>> =
    ResponseEntity.ok(ApiResponse.success(service.getWord(id)))

// Wrong — never return raw data
@GetMapping("/{id}")
fun getWord(@PathVariable id: Long): WordDto = service.getWord(id)
```

## Injection Style

```kotlin
// Correct — constructor injection
@Service
class WordService(
    private val wordRepository: WordRepository,
    private val userRepository: UserRepository,
)

// Wrong — never field injection
@Autowired
private lateinit var wordRepository: WordRepository
```

## Exception Handling

Use existing exception types that `GlobalExceptionHandler` already maps:
- `IllegalArgumentException` → 400
- `NoSuchElementException` → 404
- `AccessDeniedException` → 403

Only add a new `@ExceptionHandler` for a genuinely new exception type.

## Kotlin Style in Spring Context

- **`require()` / `check()`** over `if (x == null) throw ...`
- **Backtick test names**: `` fun `should return 404 when word not found`() ``
- **Extension functions** at file level for entity-to-DTO mapping: `private fun Word.toDto(): WordDto`
- **Package-level logger**: `private val logger = KotlinLogging.logger {}`
- **Lambda logger syntax**: `logger.error(e) { "Failed: $variable" }` — never string concatenation
- **`data class`** for all DTOs

## Security Rules

- Never log sensitive data: no passwords, tokens, emails, JWTs in log lines
- Ownership checks in service layer: `require(entity.userId == user.id) { "Forbidden" }`
- Public endpoints must be explicitly listed in `SecurityConfig.kt` `permitAll()` block
- Never manually parse JWT tokens in controllers or services — the filter handles it

## Database / Flyway

- New schema changes go in a new migration: `db/migration/V{N+1}__description.sql`
- Never modify an existing migration file that has been applied
- Test migrations work against H2 (CI default) before merging

## Verification

Before marking a task done:
1. `./gradlew test -q` — all tests pass
2. `./gradlew ktlintCheck` (if configured) or review for style
3. No new `@Autowired` field injections introduced
4. No business logic added to controllers
5. No repository calls added directly to controllers
