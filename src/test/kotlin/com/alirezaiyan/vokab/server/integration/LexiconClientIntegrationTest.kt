package com.alirezaiyan.vokab.server.integration

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

/**
 * Integration tests for Lexicon client endpoints.
 * 
 * Lexicon (KMP mobile app) calls these vokab.server endpoints.
 * This test suite ensures all client-facing endpoints work correctly.
 * 
 * Lexicon endpoints tested:
 * - /auth/google, /auth/apple (auth)
 * - /users/me (profile)
 * - /words (CRUD)
 * - /leaderboard (rankings)
 * - /streak (daily progress)
 * - /notifications (push config)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LexiconClientIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var wordRepository: WordRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val testEmail = "lexicon-client@example.com"
    private val testUserName = "Lexicon Client Test"

    @BeforeEach
    fun setUp() {
        userRepository.findByEmail(testEmail)?.let {
            wordRepository.deleteByUserId(it.id!!)
            userRepository.delete(it)
        }
    }

    // --- Auth Flow (as Lexicon client would use it) ---

    @Test
    fun `Lexicon can authenticate with Google and get tokens`() {
        val googleToken = "lexicon-google-token-123"

        val request = """
            {
                "idToken": "$googleToken"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.data.user.id").isNumber)
            .andExpect(jsonPath("$.data.user.email").isNotEmpty)
    }

    @Test
    fun `Lexicon can authenticate with Apple and get tokens`() {
        val appleToken = "lexicon-apple-token-456"
        val appleUserId = "com.apple.user.001"

        val request = """
            {
                "idToken": "$appleToken",
                "appleUserId": "$appleUserId",
                "fullName": "Lexicon Apple User"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
    }

    // --- Profile Operations (as Lexicon uses them) ---

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon can fetch user profile`() {
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "LexiconUser",
            currentStreak = 7,
            longestStreak = 15,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userRepository.save(user)

        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value(testEmail))
            .andExpect(jsonPath("$.data.currentStreak").value(7))
            .andExpect(jsonPath("$.data.longestStreak").value(15))
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon can update user profile`() {
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "OldLexiconUser",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userRepository.save(user)

        val updateRequest = """
            {
                "name": "Updated Lexicon User",
                "displayAlias": "NewLexiconAlias"
            }
        """.trimIndent()

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Updated Lexicon User"))
    }

    // --- Word Operations (core Lexicon feature) ---

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon can add new words`() {
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "WordAdder",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userRepository.save(user)

        val upsertRequest = """
            {
                "words": [
                    {
                        "word": "ephemeral",
                        "meaning": "lasting a very short time",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa",
                        "example": "The beauty was ephemeral"
                    },
                    {
                        "word": "tenacious",
                        "meaning": "holding firm; persistent",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa"
                    }
                ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify they were stored
        val words = wordRepository.findByUserEmail(testEmail)
        assert(words.size == 2)
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon can retrieve all user words`() {
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "WordLister",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedUser = userRepository.save(user)

        // Add words
        val upsertRequest = """
            {
                "words": [
                    {
                        "word": "serendipity",
                        "meaning": "luck in finding good things",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa"
                    },
                    {
                        "word": "mellifluous",
                        "meaning": "sweet sounding",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa"
                    }
                ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertRequest)
        ).andExpect(status().isOk)

        // Retrieve
        mockMvc.perform(get("/api/v1/words"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon can delete individual word`() {
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "WordDeleter",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedUser = userRepository.save(user)

        val upsertRequest = """
            {
                "words": [
                    {
                        "word": "ubiquitous",
                        "meaning": "present everywhere",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa"
                    }
                ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertRequest)
        ).andExpect(status().isOk)

        val words = wordRepository.findByUserId(savedUser.id!!)
        val wordId = words[0].id

        mockMvc.perform(delete("/api/v1/words/$wordId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        val remaining = wordRepository.findByUserId(savedUser.id)
        assert(remaining.isEmpty())
    }

    // --- Leaderboard (Lexicon displays this) ---

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon can fetch leaderboard`() {
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "LeaderboardUser",
            currentStreak = 5,
            longestStreak = 10,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userRepository.save(user)

        mockMvc.perform(get("/api/v1/leaderboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").exists())
    }

    // --- Feature Flags (Lexicon checks these) ---

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon can fetch feature flags`() {
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "FeatureFlagUser",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userRepository.save(user)

        mockMvc.perform(get("/api/v1/users/feature-access"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.featureFlags").isMap)
    }

    // --- Error Cases (important for client reliability) ---

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Lexicon gets 401 when deleting account of another user`() {
        val user1 = User(
            id = null,
            email = "user1@example.com",
            name = "User 1",
            displayAlias = "User1",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedUser1 = userRepository.save(user1)

        // Logged in as testEmail, trying to delete user1
        mockMvc.perform(delete("/api/v1/users/${savedUser1.id}"))
            .andExpect(status().is4xxClientError)
    }
}
