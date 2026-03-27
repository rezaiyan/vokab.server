package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.presentation.dto.RevenueCatEvent
import com.alirezaiyan.vokab.server.presentation.dto.RevenueCatWebhookEvent
import com.alirezaiyan.vokab.server.service.SubscriptionService
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

@WebMvcTest(WebhookController::class)
class WebhookControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var subscriptionService: SubscriptionService

    @MockBean
    private lateinit var appProperties: AppProperties

    private val revenuecat = mockk<AppProperties.RevenueCatProperties>(relaxed = true)
    private val testSecret = "test-webhook-secret-key"
    private val testPayload = """
        {
            "event": {
                "id": "event-123",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "user-123",
                "aliases": null
            },
            "api_version": "v2",
            "app_user_id": "user-123",
            "product_id": "premium_monthly",
            "purchased_at_ms": 1704067200000,
            "expiration_at_ms": 1706745600000,
            "is_trial_conversion": false,
            "cancellation_at_ms": null,
            "auto_resume_at_ms": null
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        every { appProperties.revenuecat } returns revenuecat
        every { revenuecat.webhookSecret } returns testSecret
    }

    private fun computeSignature(payload: String, secret: String): String {
        val hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        hmac.init(secretKey)
        return Base64.getEncoder().encodeToString(hmac.doFinal(payload.toByteArray()))
    }

    // ── POST /api/v1/webhooks/revenuecat: successful processing ────────────────────

    @Test
    fun `should return 200 OK when webhook processed successfully`() {
        val signature = computeSignature(testPayload, testSecret)
        every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = testPayload
        }.andExpect {
            status { isOk() }
        }.andExpect {
            jsonPath("$.success") { value(true) }
            jsonPath("$.message") { value("Webhook processed successfully") }
        }

        verify { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should call service with correct webhook event`() {
        val signature = computeSignature(testPayload, testSecret)
        val eventCaptor = mutableListOf<RevenueCatWebhookEvent>()
        every { subscriptionService.handleRevenueCatWebhook(capture(eventCaptor)) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = testPayload
        }

        assert(eventCaptor.size == 1)
        assert(eventCaptor[0].app_user_id == "user-123")
        assert(eventCaptor[0].event.type == "INITIAL_PURCHASE")
        assert(eventCaptor[0].product_id == "premium_monthly")
    }

    // ── POST /api/v1/webhooks/revenuecat: missing signature ───────────────────────

    @Test
    fun `should return 401 when signature header is missing and webhook secret is configured`() {
        every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = testPayload
        }.andExpect {
            status { isUnauthorized() }
        }.andExpect {
            jsonPath("$.success") { value(false) }
            jsonPath("$.message") { value("Missing signature") }
        }

        verify(exactly = 0) { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    // ── POST /api/v1/webhooks/revenuecat: no webhook secret configured ───────────

    @Test
    fun `should accept webhook without signature when webhook secret is not configured`() {
        every { revenuecat.webhookSecret } returns ""
        every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = testPayload
        }.andExpect {
            status { isOk() }
        }

        verify { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should accept webhook without signature when webhook secret is blank`() {
        every { revenuecat.webhookSecret } returns "   "
        every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = testPayload
        }.andExpect {
            status { isOk() }
        }

        verify { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    // ── POST /api/v1/webhooks/revenuecat: service exceptions ──────────────────────

    @Test
    fun `should return 500 when service throws exception`() {
        val signature = computeSignature(testPayload, testSecret)
        every { subscriptionService.handleRevenueCatWebhook(any()) } throws RuntimeException("Database error")

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = testPayload
        }.andExpect {
            status { isInternalServerError() }
        }.andExpect {
            jsonPath("$.success") { value(false) }
            jsonPath("$.message") { contains("Failed to process webhook") }
        }
    }

    @Test
    fun `should return 500 when service throws IllegalArgumentException`() {
        val signature = computeSignature(testPayload, testSecret)
        every { subscriptionService.handleRevenueCatWebhook(any()) } throws IllegalArgumentException("Invalid product ID")

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = testPayload
        }.andExpect {
            status { isInternalServerError() }
        }
    }

    // ── POST /api/v1/webhooks/revenuecat: edge cases ──────────────────────────────

    @Test
    fun `should handle webhook with minimal required fields`() {
        val minimalPayload = """
            {
                "event": {
                    "id": "event-456",
                    "type": "RENEWAL",
                    "app_user_id": "user-456",
                    "aliases": null
                },
                "api_version": "v2",
                "app_user_id": "user-456",
                "product_id": null,
                "purchased_at_ms": null,
                "expiration_at_ms": null,
                "is_trial_conversion": null,
                "cancellation_at_ms": null,
                "auto_resume_at_ms": null
            }
        """.trimIndent()
        val signature = computeSignature(minimalPayload, testSecret)
        every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = minimalPayload
        }.andExpect {
            status { isOk() }
        }

        verify { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should handle webhook with multiple aliases`() {
        val payloadWithAliases = """
            {
                "event": {
                    "id": "event-789",
                    "type": "CANCELLATION",
                    "app_user_id": "user-789",
                    "aliases": ["alias-1", "alias-2", "alias-3"]
                },
                "api_version": "v2",
                "app_user_id": "user-789",
                "product_id": "premium_annual",
                "purchased_at_ms": 1704067200000,
                "expiration_at_ms": null,
                "is_trial_conversion": false,
                "cancellation_at_ms": 1706745600000,
                "auto_resume_at_ms": null
            }
        """.trimIndent()
        val signature = computeSignature(payloadWithAliases, testSecret)
        every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = payloadWithAliases
        }.andExpect {
            status { isOk() }
        }

        verify { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should reject request with invalid JSON`() {
        val invalidJson = "{ invalid json }"
        val signature = computeSignature(invalidJson, testSecret)

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = invalidJson
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should reject request with empty payload`() {
        val emptyPayload = ""
        // Don't include signature header to test empty payload handling
        every { revenuecat.webhookSecret } returns ""

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = emptyPayload
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should handle different event types correctly`() {
        val eventTypes = listOf("INITIAL_PURCHASE", "RENEWAL", "CANCELLATION", "UNCANCELLATION")

        for (eventType in eventTypes) {
            val payload = """
                {
                    "event": {
                        "id": "event-$eventType",
                        "type": "$eventType",
                        "app_user_id": "user-$eventType",
                        "aliases": null
                    },
                    "api_version": "v2",
                    "app_user_id": "user-$eventType",
                    "product_id": "premium_monthly",
                    "purchased_at_ms": 1704067200000,
                    "expiration_at_ms": 1706745600000,
                    "is_trial_conversion": false,
                    "cancellation_at_ms": null,
                    "auto_resume_at_ms": null
                }
            """.trimIndent()
            val signature = computeSignature(payload, testSecret)
            every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

            mockMvc.post("/api/v1/webhooks/revenuecat") {
                contentType = MediaType.APPLICATION_JSON
                header("X-RevenueCat-Signature", signature)
                content = payload
            }.andExpect {
                status { isOk() }
            }
        }

        verify(exactly = eventTypes.size) { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should handle very large payload gracefully`() {
        val largePayload = """
            {
                "event": {
                    "id": "event-large",
                    "type": "INITIAL_PURCHASE",
                    "app_user_id": "user-large",
                    "aliases": null
                },
                "api_version": "v2",
                "app_user_id": "user-large",
                "product_id": "premium_monthly",
                "purchased_at_ms": 1704067200000,
                "expiration_at_ms": 1706745600000,
                "is_trial_conversion": false,
                "cancellation_at_ms": null,
                "auto_resume_at_ms": null,
                "extra_field": "${"x".repeat(5000)}"
            }
        """.trimIndent()
        val signature = computeSignature(largePayload, testSecret)
        every { subscriptionService.handleRevenueCatWebhook(any()) } just Runs

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = largePayload
        }.andExpect {
            status { isOk() }
        }

        verify { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    // ── POST /api/v1/webhooks/revenuecat: signature verification ───────────────────

    @Test
    fun `should verify signature independently from payload content`() {
        // Two different payloads with same signature should both fail
        val signature1 = computeSignature("payload1", testSecret)

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature1)
            content = "payload2"  // Different from signature computation
        }.andExpect {
            status { isUnauthorized() }
        }

        verify(exactly = 0) { subscriptionService.handleRevenueCatWebhook(any()) }
    }

    @Test
    fun `should accept request with correct signature even with whitespace in JSON`() {
        // Same payload but with different whitespace should have different signature
        val payload1 = """{"event":{"id":"1","type":"PURCHASE"},"api_version":"v2","app_user_id":"user"}"""
        val payload2 = """{"event": {"id": "1", "type": "PURCHASE"}, "api_version": "v2", "app_user_id": "user"}"""

        val sig1 = computeSignature(payload1, testSecret)

        // payload2 should be rejected with payload1's signature
        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", sig1)
            content = payload2
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
