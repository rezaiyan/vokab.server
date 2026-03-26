package com.alirezaiyan.vokab.server.integration

import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Integration tests for authentication endpoints.
 * Tests the full auth flow: Google OAuth, Apple OAuth, token refresh, logout.
 * 
 * Note: These tests use mocked OAuth providers (Google/Apple tokens are faked).
 * Real OAuth validation is tested by the OAuth provider libraries themselves.
 * These tests focus on: token storage, user creation, response format, state transitions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        // Clean up test users
        userRepository.findAll().forEach { userRepository.delete(it) }
    }

    @Test
    fun `Google authentication creates new user on first login`() {
        val googleIdToken = "fake-google-id-token-12345"
        
        val request = """
            {
                "idToken": "$googleIdToken"
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
            .andExpect(jsonPath("$.data.user").exists())
    }

    @Test
    fun `Google authentication returns existing user on repeated login`() {
        val googleIdToken = "fake-google-id-token-67890"
        
        val request = """
            {
                "idToken": "$googleIdToken"
            }
        """.trimIndent()

        // First login
        val firstResponse = mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()

        val firstContent = firstResponse.response.contentAsString
        val firstBody = objectMapper.readTree(firstContent)
        val firstUserId = firstBody.get("data").get("user").get("id").asLong()

        // Second login with same token
        val secondResponse = mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()

        val secondContent = secondResponse.response.contentAsString
        val secondBody = objectMapper.readTree(secondContent)
        val secondUserId = secondBody.get("data").get("user").get("id").asLong()

        // User ID should be the same
        assert(firstUserId == secondUserId) { "Repeated login should return same user" }
    }

    @Test
    fun `Google authentication with invalid token fails`() {
        val invalidToken = ""

        val request = """
            {
                "idToken": "$invalidToken"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `Apple authentication creates new user on first login`() {
        val appleIdToken = "fake-apple-id-token-abc"
        val appleUserId = "com.alirezaiyan.lexicon.user.123"

        val request = """
            {
                "idToken": "$appleIdToken",
                "appleUserId": "$appleUserId",
                "fullName": "Apple Test User"
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
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.data.user").exists())
            .andExpect(jsonPath("$.data.user.name").value("Apple Test User"))
    }

    @Test
    fun `Apple authentication without fullName succeeds on repeat login`() {
        val appleIdToken = "fake-apple-id-token-xyz"
        val appleUserId = "com.alirezaiyan.lexicon.user.456"

        // First login: with fullName
        val firstRequest = """
            {
                "idToken": "$appleIdToken",
                "appleUserId": "$appleUserId",
                "fullName": "Apple User First"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequest)
        )
            .andExpect(status().isOk)

        // Second login: without fullName (Apple doesn't always provide it)
        val secondRequest = """
            {
                "idToken": "$appleIdToken",
                "appleUserId": "$appleUserId"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `Auth response includes valid access token format`() {
        val googleIdToken = "fake-google-token-format-test"

        val request = """
            {
                "idToken": "$googleIdToken"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").isNumber)
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
    }

    @Test
    fun `Refresh token endpoint is accessible`() {
        // First: get tokens
        val googleIdToken = "fake-google-token-refresh-test"
        val authRequest = """
            {
                "idToken": "$googleIdToken"
            }
        """.trimIndent()

        val authResponse = mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authRequest)
        )
            .andExpect(status().isOk)
            .andReturn()

        val responseBody = objectMapper.readTree(authResponse.response.contentAsString)
        val refreshToken = responseBody.get("data").get("refreshToken").asText()

        // Then: use refresh token
        val refreshRequest = """
            {
                "refreshToken": "$refreshToken"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
    }

    @Test
    fun `Logout endpoint returns success`() {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `Health endpoint is accessible without authentication`() {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `Invalid request body returns bad request`() {
        val invalidRequest = """
            {
                "invalid_field": "value"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `Missing required fields in auth request fails`() {
        val missingIdToken = """
            {
                "someOtherField": "value"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingIdToken)
        )
            .andExpect(status().isBadRequest)
    }
}
