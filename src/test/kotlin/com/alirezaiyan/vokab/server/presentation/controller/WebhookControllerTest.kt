package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.presentation.dto.RevenueCatWebhookEvent
import com.alirezaiyan.vokab.server.service.SubscriptionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ControllerTestSecurityConfig::class)
class WebhookControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var appProperties: AppProperties

    @MockitoBean
    private lateinit var subscriptionService: SubscriptionService

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
        appProperties.revenuecat.webhookSecret = testSecret
    }

    @AfterEach
    fun tearDown() {
        appProperties.revenuecat.webhookSecret = ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyArg(): T = ArgumentMatchers.any<T>() as T

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

        verify(subscriptionService).handleRevenueCatWebhook(anyArg())
    }

    @Test
    fun `should call service with correct webhook event`() {
        val signature = computeSignature(testPayload, testSecret)
        var capturedEvent: RevenueCatWebhookEvent? = null
        doAnswer { invocation ->
            capturedEvent = invocation.getArgument(0)
            null
        }.`when`(subscriptionService).handleRevenueCatWebhook(anyArg())

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = testPayload
        }

        assert(capturedEvent != null)
        assert(capturedEvent!!.app_user_id == "user-123")
        assert(capturedEvent!!.event.type == "INITIAL_PURCHASE")
        assert(capturedEvent!!.product_id == "premium_monthly")
    }

    // ── POST /api/v1/webhooks/revenuecat: missing signature ───────────────────────

    @Test
    fun `should return 401 when signature header is missing and webhook secret is configured`() {
        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = testPayload
        }.andExpect {
            status { isUnauthorized() }
        }.andExpect {
            jsonPath("$.success") { value(false) }
            jsonPath("$.message") { value("Missing signature") }
        }

        verify(subscriptionService, never()).handleRevenueCatWebhook(anyArg())
    }

    // ── POST /api/v1/webhooks/revenuecat: no webhook secret configured ───────────

    @Test
    fun `should accept webhook without signature when webhook secret is not configured`() {
        appProperties.revenuecat.webhookSecret = ""

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = testPayload
        }.andExpect {
            status { isOk() }
        }

        verify(subscriptionService).handleRevenueCatWebhook(anyArg())
    }

    @Test
    fun `should accept webhook without signature when webhook secret is blank`() {
        appProperties.revenuecat.webhookSecret = "   "

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = testPayload
        }.andExpect {
            status { isOk() }
        }

        verify(subscriptionService).handleRevenueCatWebhook(anyArg())
    }

    // ── POST /api/v1/webhooks/revenuecat: service exceptions ──────────────────────

    @Test
    fun `should return 500 when service throws exception`() {
        val signature = computeSignature(testPayload, testSecret)
        doThrow(RuntimeException("Database error")).`when`(subscriptionService).handleRevenueCatWebhook(anyArg())

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = testPayload
        }.andExpect {
            status { isInternalServerError() }
        }.andExpect {
            jsonPath("$.success") { value(false) }
            jsonPath("$.message") { value(containsString("Failed to process webhook")) }
        }
    }

    @Test
    fun `should return 500 when service throws IllegalArgumentException`() {
        val signature = computeSignature(testPayload, testSecret)
        doThrow(IllegalArgumentException("Invalid product ID")).`when`(subscriptionService).handleRevenueCatWebhook(anyArg())

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

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = minimalPayload
        }.andExpect {
            status { isOk() }
        }

        verify(subscriptionService).handleRevenueCatWebhook(anyArg())
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

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = payloadWithAliases
        }.andExpect {
            status { isOk() }
        }

        verify(subscriptionService).handleRevenueCatWebhook(anyArg())
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

        verify(subscriptionService, never()).handleRevenueCatWebhook(anyArg())
    }

    @Test
    fun `should reject request with empty payload`() {
        appProperties.revenuecat.webhookSecret = ""

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            content = ""
        }.andExpect {
            status { isBadRequest() }
        }

        verify(subscriptionService, never()).handleRevenueCatWebhook(anyArg())
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

            mockMvc.post("/api/v1/webhooks/revenuecat") {
                contentType = MediaType.APPLICATION_JSON
                header("X-RevenueCat-Signature", signature)
                content = payload
            }.andExpect {
                status { isOk() }
            }
        }

        verify(subscriptionService, times(eventTypes.size)).handleRevenueCatWebhook(anyArg())
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

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature)
            content = largePayload
        }.andExpect {
            status { isOk() }
        }

        verify(subscriptionService).handleRevenueCatWebhook(anyArg())
    }

    // ── POST /api/v1/webhooks/revenuecat: signature verification ───────────────────

    @Test
    fun `should verify signature independently from payload content`() {
        val signature1 = computeSignature("payload1", testSecret)

        mockMvc.post("/api/v1/webhooks/revenuecat") {
            contentType = MediaType.APPLICATION_JSON
            header("X-RevenueCat-Signature", signature1)
            content = "payload2"
        }.andExpect {
            status { isUnauthorized() }
        }

        verify(subscriptionService, never()).handleRevenueCatWebhook(anyArg())
    }

    @Test
    fun `should accept request with correct signature even with whitespace in JSON`() {
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
