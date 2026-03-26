package com.alirezaiyan.vokab.server.integration

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.UserDto
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
 * Integration tests for user authentication and basic user operations.
 * Tests the full user lifecycle: auth, profile access, and basic CRUD.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val testUserId = 1L
    private val testEmail = "integration-test-user@example.com"
    private val testUserName = "Integration Test User"

    @BeforeEach
    fun setUp() {
        // Clean up any existing test users
        userRepository.findByEmail(testEmail)?.let { userRepository.delete(it) }
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Get current user returns authenticated user details`() {
        // Arrange: Create a test user
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "IntegrationTestUser",
            currentStreak = 5,
            longestStreak = 10,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedUser = userRepository.save(user)

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value(testEmail))
            .andExpect(jsonPath("$.data.name").value(testUserName))
    }

    @Test
    fun `Get current user without authentication returns unauthorized`() {
        // Act & Assert
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Update user profile succeeds with valid data`() {
        // Arrange
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "OldAlias",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedUser = userRepository.save(user)

        val updateRequest = """
            {
                "name": "Updated Name",
                "displayAlias": "NewAlias"
            }
        """.trimIndent()

        // Act & Assert
        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Updated Name"))
            .andExpect(jsonPath("$.data.displayAlias").value("NewAlias"))

        // Verify database was actually updated
        val updated = userRepository.findByEmail(testEmail)
        assert(updated != null)
        assert(updated!!.name == "Updated Name")
        assert(updated.displayAlias == "NewAlias")
    }

    @Test
    fun `API health check endpoint is accessible without authentication`() {
        // Act & Assert
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = testEmail, roles = ["USER"])
    fun `Get feature flags succeeds for authenticated user`() {
        // Arrange
        val user = User(
            id = null,
            email = testEmail,
            name = testUserName,
            displayAlias = "FeatureTester",
            currentStreak = 0,
            longestStreak = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userRepository.save(user)

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/feature-access"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.featureFlags").isMap)
            .andExpect(jsonPath("$.data.userAccess").isMap)
    }
}
