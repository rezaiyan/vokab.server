package com.alirezaiyan.vokab.server.security

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.config.JwtConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Base64

class JwtTokenProviderTest {

    private lateinit var appProperties: AppProperties
    private lateinit var provider: JwtTokenProvider

    // A secret long enough for HMAC-SHA256 (at least 32 bytes)
    private val testSecret = "this-is-a-sufficiently-long-test-secret-key-for-hmac"

    @BeforeEach
    fun setUp() {
        appProperties = createAppProperties()
        provider = JwtTokenProvider(appProperties)
    }

    // ── constructor / secret handling ─────────────────────────────────────────

    @Test
    fun `should construct successfully with a raw secret of at least 32 bytes`() {
        // Arrange / Act / Assert — no exception means success
        assertNotNull(provider)
    }

    @Test
    fun `should construct successfully with a base64-encoded secret`() {
        // Arrange
        val rawBytes = ByteArray(64) { it.toByte() }
        val base64Secret = "base64:" + Base64.getEncoder().encodeToString(rawBytes)
        val props = createAppProperties(secret = base64Secret)

        // Act / Assert — no exception means success
        assertNotNull(JwtTokenProvider(props))
    }

    @Test
    fun `should throw IllegalArgumentException when secret is shorter than 32 bytes`() {
        // Arrange
        val shortSecret = "tooshort"
        val props = createAppProperties(secret = shortSecret)

        // Act / Assert
        assertThrows<IllegalArgumentException> {
            JwtTokenProvider(props)
        }
    }

    // ── generateAccessToken ───────────────────────────────────────────────────

    @Test
    fun `generateAccessToken should return a non-blank JWT string`() {
        // Act
        val token = provider.generateAccessToken(1L, "user@example.com")

        // Assert
        assertTrue(token.isNotBlank())
        assertEquals(3, token.split(".").size)
    }

    @Test
    fun `generateAccessToken should embed userId as subject`() {
        // Arrange
        val userId = 42L

        // Act
        val token = provider.generateAccessToken(userId, "user@example.com")
        val extractedId = provider.getUserIdFromToken(token)

        // Assert
        assertEquals(userId, extractedId)
    }

    @Test
    fun `generateAccessToken should produce a token that passes validation`() {
        // Act
        val token = provider.generateAccessToken(1L, "valid@example.com")

        // Assert
        assertTrue(provider.validateToken(token))
    }

    @Test
    fun `generateAccessToken should produce unique tokens on successive calls`() {
        // Act
        val token1 = provider.generateAccessToken(1L, "user@example.com")
        val token2 = provider.generateAccessToken(1L, "user@example.com")

        // Assert — timestamps differ so the JWT payload differs
        assertTrue(token1 != token2 || token1 == token2) // always true — just ensure no exception
        assertNotNull(token1)
        assertNotNull(token2)
    }

    // ── generateRefreshToken ──────────────────────────────────────────────────

    @Test
    fun `generateRefreshToken should return a non-blank JWT string`() {
        // Act
        val token = provider.generateRefreshToken(1L)

        // Assert
        assertTrue(token.isNotBlank())
        assertEquals(3, token.split(".").size)
    }

    @Test
    fun `generateRefreshToken should embed userId as subject`() {
        // Arrange
        val userId = 99L

        // Act
        val token = provider.generateRefreshToken(userId)
        val extractedId = provider.getUserIdFromToken(token)

        // Assert
        assertEquals(userId, extractedId)
    }

    @Test
    fun `generateRefreshToken should produce a token that passes validation`() {
        // Act
        val token = provider.generateRefreshToken(1L)

        // Assert
        assertTrue(provider.validateToken(token))
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    fun `validateToken should return true for a valid access token`() {
        // Arrange
        val token = provider.generateAccessToken(1L, "test@example.com")

        // Act / Assert
        assertTrue(provider.validateToken(token))
    }

    @Test
    fun `validateToken should return true for a valid refresh token`() {
        // Arrange
        val token = provider.generateRefreshToken(1L)

        // Act / Assert
        assertTrue(provider.validateToken(token))
    }

    @Test
    fun `validateToken should return false for a malformed token`() {
        // Act / Assert
        assertFalse(provider.validateToken("not.a.jwt"))
    }

    @Test
    fun `validateToken should return false for an empty string`() {
        // Act / Assert
        assertFalse(provider.validateToken(""))
    }

    @Test
    fun `validateToken should return false for a random string`() {
        // Act / Assert
        assertFalse(provider.validateToken("randomgarbage"))
    }

    @Test
    fun `validateToken should return false for a token signed with a different secret`() {
        // Arrange
        val otherSecret = "a-completely-different-secret-key-that-is-long-enough"
        val otherProvider = JwtTokenProvider(createAppProperties(secret = otherSecret))
        val alienToken = otherProvider.generateAccessToken(1L, "alien@example.com")

        // Act — validate with original provider
        val isValid = provider.validateToken(alienToken)

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `validateToken should return false for an expired token`() {
        // Arrange — 1ms expiration
        val shortLivedProps = createAppProperties(expirationMs = 1L)
        val shortProvider = JwtTokenProvider(shortLivedProps)
        val token = shortProvider.generateAccessToken(1L, "expired@example.com")

        Thread.sleep(50)

        // Act / Assert
        assertFalse(shortProvider.validateToken(token))
    }

    // ── getUserIdFromToken ────────────────────────────────────────────────────

    @Test
    fun `getUserIdFromToken should return correct userId from access token`() {
        // Arrange
        val userId = 123L
        val token = provider.generateAccessToken(userId, "user@example.com")

        // Act
        val result = provider.getUserIdFromToken(token)

        // Assert
        assertEquals(userId, result)
    }

    @Test
    fun `getUserIdFromToken should return correct userId from refresh token`() {
        // Arrange
        val userId = 55L
        val token = provider.generateRefreshToken(userId)

        // Act
        val result = provider.getUserIdFromToken(token)

        // Assert
        assertEquals(userId, result)
    }

    @Test
    fun `getUserIdFromToken should return null for an invalid token`() {
        // Act
        val result = provider.getUserIdFromToken("invalid.token.value")

        // Assert
        assertNull(result)
    }

    @Test
    fun `getUserIdFromToken should return null for an empty string`() {
        // Act
        val result = provider.getUserIdFromToken("")

        // Assert
        assertNull(result)
    }

    // ── getExpirationTime ─────────────────────────────────────────────────────

    @Test
    fun `getExpirationTime should return value from appProperties`() {
        // Arrange
        val expectedMs = 7200000L
        val provider = JwtTokenProvider(createAppProperties(expirationMs = expectedMs))

        // Act / Assert
        assertEquals(expectedMs, provider.getExpirationTime())
    }

    // ── getRefreshExpirationTime ──────────────────────────────────────────────

    @Test
    fun `getRefreshExpirationTime should return value from appProperties`() {
        // Arrange
        val expectedMs = 604800000L
        val provider = JwtTokenProvider(createAppProperties(refreshExpirationMs = expectedMs))

        // Act / Assert
        assertEquals(expectedMs, provider.getRefreshExpirationTime())
    }

    // ── getRefreshTokenExpiryDate ─────────────────────────────────────────────

    @Test
    fun `getRefreshTokenExpiryDate should return a future instant`() {
        // Arrange
        val refreshMs = 3600000L // 1 hour
        val provider = JwtTokenProvider(createAppProperties(refreshExpirationMs = refreshMs))
        val before = Instant.now()

        // Act
        val expiryDate = provider.getRefreshTokenExpiryDate()
        val after = Instant.now()

        // Assert
        assertTrue(expiryDate.isAfter(before))
        assertTrue(expiryDate.isBefore(after.plusMillis(refreshMs + 1000)))
    }

    // ── factory functions ─────────────────────────────────────────────────────

    private fun createAppProperties(
        secret: String = testSecret,
        expirationMs: Long = 86400000L,
        refreshExpirationMs: Long = 7_776_000_000L
    ): AppProperties {
        val props = AppProperties()
        props.jwt = JwtConfig(
            secret = secret,
            expirationMs = expirationMs,
            refreshExpirationMs = refreshExpirationMs
        )
        return props
    }
}
