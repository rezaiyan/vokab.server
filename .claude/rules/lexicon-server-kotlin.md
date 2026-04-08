---
name: Kotlin & Spring Conventions
description: Kotlin style, layering, DI, logging, and transactional rules for Vokab Server
type: convention
paths:
  - "src/main/kotlin/**/*.kt"
---

# Kotlin & Spring Conventions

## Layering — Strict

```
Controller  →  Service  →  Repository
```

- Controllers call services only. Never inject a repository into a controller.
- Services call repositories and other services. Never issue HTTP or persist via controllers.
- Repositories are pure data access — JPQL/native SQL for complex queries, no business logic.

## Dependency Injection

- **Constructor injection** with `private val`. Never `@Autowired` on fields.
- `@Service`, `@RestController`, `@Repository`, `@Component` — one annotation per bean.

```kotlin
@Service
class WordService(
    private val wordRepository: WordRepository,
    private val eventService: EventService,
)
```

## Data Classes & Mapping

- `data class` for all DTOs, request/response bodies, value objects.
- Entity ↔ DTO mapping via **file-level extension functions** (not inside classes):

```kotlin
private fun Word.toDto(): WordDto = WordDto(id = id, term = term, ...)
```

## Preconditions

- Prefer `require(cond) { "msg" }` (→ `IllegalArgumentException` → 400) and `check(cond) { "msg" }` (→ `IllegalStateException`) over manual `if (x) throw ...`.
- Ownership checks go in the service layer:

```kotlin
require(word.userId == user.id) { "Forbidden" }
```

## Logging

- **Package-level** `private val logger` — never inside classes, never static.
- **Always** use the lambda form so strings aren't built when disabled:

```kotlin
private val logger = KotlinLogging.logger {}

logger.error(e) { "Failed to send push to user=${user.id}" }
logger.info { "Streak updated: ${user.id} -> ${newStreak}" }
```

- **Never log sensitive data:** passwords, JWTs, refresh tokens, emails, Apple/Google IDs.

## Transactions

- `@Transactional` on **every write** service method.
- `@Transactional(readOnly = true)` on **every read** service method.
- **No external I/O inside a transaction.** Never call OpenRouter, Firebase, RevenueCat, Apple, or SMTP inside a `@Transactional` block — the DB connection stays pinned to the thread for the whole call.

```kotlin
@Transactional
fun addWord(...) { /* DB only */ }

@Transactional(readOnly = true)
fun listWords(...): List<WordDto> { ... }
```

## Coroutines / Reactive

- Use `kotlinx-coroutines-reactor` bridges when calling `WebClient`; don't mix `.block()` into request threads.
- Scheduler/async work lives in `scheduler/` or `task/` — not in request-scoped services.

## Fire-and-Forget Patterns

Several side effects must never break the primary operation:

| Side effect          | Rule                                                                 |
| -------------------- | -------------------------------------------------------------------- |
| `EventService.track` | Must never throw — wrap internally in try/catch                      |
| Push notification    | Catch send failures, log, deactivate invalid tokens                  |
| Email send           | Catch SMTP failures, log — don't fail the triggering request         |

Call sites write as if the call cannot fail; the implementation absorbs failures.

## File Size

- Production files target **under 800 lines**. Over 1000 is a signal to split — only when it's the task at hand, not a drive-by refactor.
