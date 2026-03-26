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
 * Integration tests for word lifecycle operations.
 * Tests core vocabulary app functionality: create, read, update, delete words.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WordLifecycleIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var wordRepository: WordRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val testEmail = "word-test-user@example.com"
    private val testUserName = "Word Test User"

    @BeforeEach
    fun setUp() {
        // Clean up test data
        userRepository.findByEmail(testEmail)?.let {
            wordRepository.deleteByUserId(it.id!!)
            userRepository.delete(it)
        }
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Upsert word creates new word for user`() {
        // Arrange: Create test user
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "WordTester",
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
                        "word": "serendipity",
                        "meaning": "luck in finding good things by chance",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa",
                        "example": "Finding that book was pure serendipity"
                    }
                ]
            }
        """.trimIndent()

        // Act & Assert
        mockMvc.perform(
            post("/api/v1/words")
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify word was stored
        val words = wordRepository.findByUserEmail(testEmail)
        assert(words.isNotEmpty()) { "Word should be stored in database" }
        assert(words[0].word == "serendipity")
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `List words returns all user words`() {
        // Arrange
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

        val upsertRequest = """
            {
                "words": [
                    {
                        "word": "ephemeral",
                        "meaning": "lasting for a very short time",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa"
                    },
                    {
                        "word": "mellifluous",
                        "meaning": "sweet or musical",
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

        // Act & Assert
        mockMvc.perform(get("/api/v1/words"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Delete word removes word from user's collection`() {
        // Arrange
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

        // Get the word ID
        val words = wordRepository.findByUserId(savedUser.id!!)
        assert(words.isNotEmpty())
        val wordId = words[0].id

        // Act: Delete the word
        mockMvc.perform(delete("/api/v1/words/$wordId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Assert: Verify deletion
        val remainingWords = wordRepository.findByUserId(savedUser.id)
        assert(remainingWords.isEmpty()) { "Word should be deleted" }
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Batch delete removes multiple words`() {
        // Arrange
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "BatchDeleter",
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
                        "word": "amalgamate",
                        "meaning": "combine into one",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa"
                    },
                    {
                        "word": "tenacious",
                        "meaning": "holding firmly",
                        "sourceLanguage": "en",
                        "targetLanguage": "fa"
                    },
                    {
                        "word": "perspicacious",
                        "meaning": "having keen insight",
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
        val ids = words.take(2).map { it.id }.joinToString(",")

        val deleteRequest = """
            {
                "ids": [${words[0].id}, ${words[1].id}]
            }
        """.trimIndent()

        // Act
        mockMvc.perform(
            post("/api/v1/words/batch-delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.deletedCount").value(2))

        // Assert
        val remaining = wordRepository.findByUserId(savedUser.id)
        assert(remaining.size == 1) { "Should have 1 word left after deleting 2" }
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Update word modifies existing word`() {
        // Arrange
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "WordUpdater",
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
                        "word": "indefatigable",
                        "meaning": "never tiring",
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

        val updateRequest = """
            {
                "word": "indefatigable",
                "meaning": "tireless; showing no sign of fatigue",
                "example": "She was indefatigable in her pursuit of excellence"
            }
        """.trimIndent()

        // Act
        mockMvc.perform(
            patch("/api/v1/words/$wordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Assert
        val updated = wordRepository.findById(wordId).get()
        assert(updated.meaning.contains("tireless"))
    }

    @Test
    fun `Word endpoints require authentication`() {
        mockMvc.perform(get("/api/v1/words"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(post("/api/v1/words"))
            .andExpect(status().isUnauthorized)
    }
}
