---
name: REST API Conventions
description: Controller patterns, ApiResponse wrapper, validation, and exception mapping
type: convention
paths:
  - "src/main/kotlin/com/alirezaiyan/vokab/server/presentation/**"
  - "src/main/kotlin/com/alirezaiyan/vokab/server/exception/**"
---

# REST API Conventions

## Response Envelope

**Every** endpoint returns `ResponseEntity<ApiResponse<T>>`. No raw DTOs, no `Map<String, Any>`.

```kotlin
@PostMapping
fun create(
    @Valid @RequestBody request: CreateWordRequest,
    @AuthenticationPrincipal user: User,
): ResponseEntity<ApiResponse<WordDto>> {
    val word = wordService.create(user.id, request)
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(word))
}
```

## HTTP Status Codes

| Code | When                                                  |
| ---- | ----------------------------------------------------- |
| 200  | Successful GET / PUT / PATCH with body                |
| 201  | Successful POST that creates a resource               |
| 204  | Successful DELETE or mutation with no response body   |
| 400  | Validation failure, `IllegalArgumentException`        |
| 401  | Missing / invalid auth (`AuthenticationException`)    |
| 403  | Authenticated but not authorized (ownership mismatch) |
| 404  | `NoSuchElementException` — resource not found         |
| 409  | Unique constraint / conflict                          |
| 500  | Unhandled — logged, never leaked                      |

`application.yml` sets `server.error.include-message: never` — **never expose internal error messages to clients.** Log the full exception server-side.

## Controller Rules

- **No business logic.** Bind, validate, call one service method, return.
- `@Valid @RequestBody` on all write requests.
- `@AuthenticationPrincipal User` for authenticated routes — never parse JWT in the controller.
- `@PathVariable` / `@RequestParam` with explicit names.
- **Pageable** for list endpoints that can grow — accept `page`, `size`, optional `sort`.
- **try/catch only in auth controllers** where specific messages are safe. Everywhere else, let exceptions propagate to `GlobalExceptionHandler`.

## Validation

Use Jakarta Bean Validation on request DTOs:

```kotlin
data class CreateWordRequest(
    @field:NotBlank val term: String,
    @field:Size(max = 500) val translation: String?,
    @field:Email val contactEmail: String?,
)
```

Add `@Valid` on the controller parameter. Custom validators live alongside the DTO.

## Ownership

**Always checked in the service**, not the controller:

```kotlin
fun update(userId: Long, wordId: Long, req: UpdateWordRequest): WordDto {
    val word = wordRepository.findById(wordId).orElseThrow()
    require(word.userId == userId) { "Forbidden" }   // → 400 by default
    ...
}
```

Never trust a client-supplied user ID — always use the `@AuthenticationPrincipal`.

## Exception Mapping

`GlobalExceptionHandler` already maps these — **throw the standard type, don't add a new handler:**

| Throw                                             | HTTP Response |
| ------------------------------------------------- | ------------- |
| `IllegalArgumentException`                        | 400           |
| `NoSuchElementException`                          | 404           |
| `AuthenticationException` / `BadCredentialsException` | 401       |
| `AccessDeniedException`                           | 403           |

Only add a new `@ExceptionHandler` when a new exception type doesn't fit any existing bucket.

## Public Endpoints

Any route that should be anonymous MUST be listed in `SecurityConfig.kt` under `permitAll()`. The JWT filter runs otherwise and rejects the request.

## DTO Location

- Request/response DTOs live in `presentation/dto/`.
- Never return JPA entities directly — always map to a DTO via extension function.
