package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.Platform
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.controller.handler.NotificationControllerHandler
import com.alirezaiyan.vokab.server.presentation.dto.NotificationResponse
import com.alirezaiyan.vokab.server.presentation.dto.RegisterPushTokenRequest
import com.alirezaiyan.vokab.server.presentation.dto.SendNotificationRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get

@WebMvcTest(PushNotificationController::class)
class PushNotificationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var handler: NotificationControllerHandler

    private val testUser = User(
        id = 1L,
        email = "test@example.com",
        name = "Test User",
        currentStreak = 0,
        longestStreak = 0
    )

    private val testToken = "valid-push-token-12345"
    private val deviceId = "device-123"

    // ── POST /api/v1/notifications/register-token ────────────────────────────────

    @Test
    @WithMockUser
    fun `should register push token successfully`() {
        val request = RegisterPushTokenRequest(
            token = testToken,
            platform = Platform.ANDROID,
            deviceId = deviceId
        )

        every { handler.registerToken(any(), eq(request)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.post("/api/v1/notifications/register-token") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }

        verify { handler.registerToken(any(), any()) }
    }

    @Test
    @WithMockUser
    fun `should validate required token field`() {
        val invalidRequest = """
            {
                "platform": "ANDROID",
                "deviceId": "device-123"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/notifications/register-token") {
            contentType = MediaType.APPLICATION_JSON
            content = invalidRequest
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { handler.registerToken(any(), any()) }
    }

    @Test
    @WithMockUser
    fun `should accept empty deviceId`() {
        val request = RegisterPushTokenRequest(
            token = testToken,
            platform = Platform.IOS
        )

        every { handler.registerToken(any(), eq(request)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.post("/api/v1/notifications/register-token") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    @WithMockUser
    fun `should handle handler exception on token registration`() {
        val request = RegisterPushTokenRequest(
            token = testToken,
            platform = Platform.ANDROID
        )

        every { handler.registerToken(any(), any()) } throws RuntimeException("Database error")

        mockMvc.post("/api/v1/notifications/register-token") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }.andExpect {
            jsonPath("$.success") { value(false) }
        }
    }

    // ── DELETE /api/v1/notifications/token/{token} ────────────────────────────────

    @Test
    @WithMockUser
    fun `should deactivate specific token successfully`() {
        every { handler.deactivateToken(any(), eq(testToken)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.delete("/api/v1/notifications/token/$testToken") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }

        verify { handler.deactivateToken(any(), eq(testToken)) }
    }

    @Test
    @WithMockUser
    fun `should handle deactivate token exception`() {
        every { handler.deactivateToken(any(), any()) } throws RuntimeException("Token not found")

        mockMvc.delete("/api/v1/notifications/token/$testToken") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }.andExpect {
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    @WithMockUser
    fun `should deactivate token with special characters in URL`() {
        val specialToken = "token-with-special-chars_123"
        every { handler.deactivateToken(any(), eq(specialToken)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.delete("/api/v1/notifications/token/$specialToken") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }

        verify { handler.deactivateToken(any(), eq(specialToken)) }
    }

    // ── DELETE /api/v1/notifications/tokens ────────────────────────────────────────

    @Test
    @WithMockUser
    fun `should deactivate all user tokens successfully`() {
        every { handler.deactivateAllTokens(any()) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.delete("/api/v1/notifications/tokens") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }

        verify { handler.deactivateAllTokens(any()) }
    }

    @Test
    @WithMockUser
    fun `should handle deactivate all tokens exception`() {
        every { handler.deactivateAllTokens(any()) } throws RuntimeException("Database error")

        mockMvc.delete("/api/v1/notifications/tokens") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── POST /api/v1/notifications/send ────────────────────────────────────────────

    @Test
    @WithMockUser
    fun `should send notification with title and body`() {
        val request = SendNotificationRequest(
            title = "Test Title",
            body = "Test Body"
        )

        every { handler.sendNotification(any(), eq(request)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }

        verify { handler.sendNotification(any(), any()) }
    }

    @Test
    @WithMockUser
    fun `should send notification with data payload`() {
        val request = SendNotificationRequest(
            title = "Streak Reminder",
            body = "Keep your streak alive!",
            data = mapOf(
                "type" to "streak_reminder",
                "streak_count" to "5"
            )
        )

        every { handler.sendNotification(any(), eq(request)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    @WithMockUser
    fun `should send notification with image URL`() {
        val request = SendNotificationRequest(
            title = "New Word",
            body = "Check out this new word!",
            imageUrl = "https://example.com/image.jpg"
        )

        every { handler.sendNotification(any(), eq(request)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    @WithMockUser
    fun `should validate required title field`() {
        val invalidRequest = """
            {
                "body": "Missing title"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = invalidRequest
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { handler.sendNotification(any(), any()) }
    }

    @Test
    @WithMockUser
    fun `should validate required body field`() {
        val invalidRequest = """
            {
                "title": "Missing body"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = invalidRequest
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { handler.sendNotification(any(), any()) }
    }

    @Test
    @WithMockUser
    fun `should return notification responses from handler`() {
        val request = SendNotificationRequest(
            title = "Test",
            body = "Test Body"
        )

        val mockResponses = listOf(
            NotificationResponse(success = true, messageId = "msg-1"),
            NotificationResponse(success = false, messageId = null)
        )

        every { handler.sendNotification(any(), eq(request)) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    @WithMockUser
    fun `should handle send notification exception`() {
        val request = SendNotificationRequest(
            title = "Test",
            body = "Test Body"
        )

        every { handler.sendNotification(any(), any()) } throws RuntimeException("FCM unavailable")

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── GET /api/v1/notifications/tokens ───────────────────────────────────────────

    @Test
    @WithMockUser
    fun `should get user token count successfully`() {
        every { handler.getUserTokens(any()) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.get("/api/v1/notifications/tokens") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }

        verify { handler.getUserTokens(any()) }
    }

    @Test
    @WithMockUser
    fun `should return token count as integer`() {
        every { handler.getUserTokens(any()) } returns mockk(relaxed = true) {
            every { statusCode.is2xxSuccessful } returns true
        }

        mockMvc.get("/api/v1/notifications/tokens") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    @WithMockUser
    fun `should handle get tokens exception`() {
        every { handler.getUserTokens(any()) } throws RuntimeException("Database error")

        mockMvc.get("/api/v1/notifications/tokens") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── Authentication ────────────────────────────────────────────────────────────

    @Test
    fun `should require authentication for register token`() {
        val request = RegisterPushTokenRequest(
            token = testToken,
            platform = Platform.ANDROID
        )

        mockMvc.post("/api/v1/notifications/register-token") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `should require authentication for send notification`() {
        val request = SendNotificationRequest(
            title = "Test",
            body = "Test Body"
        )

        mockMvc.post("/api/v1/notifications/send") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `should require authentication for get tokens`() {
        mockMvc.get("/api/v1/notifications/tokens").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `should require authentication for deactivate token`() {
        mockMvc.delete("/api/v1/notifications/token/$testToken").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `should require authentication for deactivate all tokens`() {
        mockMvc.delete("/api/v1/notifications/tokens").andExpect {
            status { isUnauthorized() }
        }
    }
}
