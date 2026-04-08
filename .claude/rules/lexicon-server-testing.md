---
name: Testing Conventions
description: MockK, JUnit 5, test factories, and controller/integration test patterns for Vokab Server
type: convention
paths:
  - "src/test/**"
---

# Testing Conventions

## Framework

- **JUnit 5** (`org.junit.jupiter.api.*`) + **MockK 1.13.8** — never Mockito.
- Use `io.mockk.mockk()`, `every { } returns ...`, `verify { }`, `slot<>()`, `coEvery { }` for coroutines.
- Prefer `MockKAnnotations.init(this)` only if you need `@MockK` fields — otherwise plain `mockk()` is simpler.

## Test Class Structure

```kotlin
class WordServiceTest {
    private val wordRepository: WordRepository = mockk()
    private val eventService: EventService = mockk(relaxed = true)
    private lateinit var service: WordService

    @BeforeEach
    fun setUp() {
        service = WordService(wordRepository, eventService)
    }

    @Test
    fun `should add word when user has capacity`() {
        // given
        val user = testUser()
        every { wordRepository.save(any()) } returns testWord(user.id)

        // when
        val result = service.addWord(user.id, createRequest())

        // then
        assertEquals("hello", result.term)
        verify { wordRepository.save(any()) }
    }

    // --- factories ---
    private fun testUser(id: Long = 1L) = User(id = id, email = "t@test", ...)
    private fun testWord(userId: Long) = Word(id = 1L, userId = userId, term = "hello", ...)
}
```

## Naming

- **Backticks** for test names — full sentences describing the behavior:

```kotlin
@Test
fun `should return 404 when word not found`() { ... }

@Test
fun `should reject duplicate term for same user`() { ... }
```

- One **behavior** per test. Don't assert 5 unrelated things in one method.

## Test Data Factories

- **Private factory functions inside the test class** — not inline construction scattered everywhere, not a shared test fixture module (unless genuinely reused across many tests).

```kotlin
private fun testUser(
    id: Long = 1L,
    email: String = "user@test.com",
    streak: Int = 0,
) = User(id = id, email = email, currentStreak = streak, ...)
```

Override only the fields the test cares about via default arguments.

## Mocking Rules

- **`relaxed = true`** for fire-and-forget collaborators (`EventService`, `PushNotificationService`) — they shouldn't force every test to stub their return values.
- **Strict mocks** (`mockk()`) for the repository / collaborators whose calls are the point of the test.
- **Never mock** the class under test.
- **Don't mock data classes** — construct them with factory functions.

## Controller Tests

Use `@WebMvcTest` + `ControllerTestSecurityConfig` to bypass the full JWT filter chain:

```kotlin
@WebMvcTest(WordController::class)
@Import(ControllerTestSecurityConfig::class)
class WordControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockkBean lateinit var wordService: WordService
    ...
}
```

`TestUserHelper` creates authenticated users for MockMvc requests — use it rather than hand-rolling `SecurityContext` stubs.

## Integration Tests

- Annotated with `@SpringBootTest` — boot the full context only when you need it.
- Use the **H2 profile** by default. `H2CompatFunctions` shims PG-specific functions (`gen_random_uuid`, JSONB ops) so migrations run cleanly.
- Gate PG-only tests with a profile (`@ActiveProfiles("integration-pg")`) and skip when not available.
- Clean up state in `@AfterEach` — don't rely on transaction rollback alone for tests that commit.

## Coverage & Gates

- Target **≥ 80%** line coverage on services. Controllers and DTOs are lower priority.
- `./gradlew jacocoTestReport` produces the report under `build/reports/jacoco/`.
- New business logic must ship with tests — don't defer "I'll add tests later".

## Anti-Patterns

- ❌ **Mockito** — project uses MockK. Don't mix.
- ❌ **Inline object construction** for entities repeated in every test.
- ❌ **Multiple behaviors per test** — split them.
- ❌ **Testing implementation** (asserting internal mock call counts when the observable output is what matters).
- ❌ **Shared mutable state between tests** — each test sets up its own fixtures in `@BeforeEach`.
- ❌ **`@Transactional` on tests** as a substitute for cleanup — fine for JPA tests, but don't hide side effects.
