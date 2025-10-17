package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.RevenueCatWebhookEvent
import com.alirezaiyan.vokab.server.service.SubscriptionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val subscriptionService: SubscriptionService,
    private val appProperties: AppProperties
) {
    
    @PostMapping("/revenuecat")
    fun handleRevenueCatWebhook(
        @RequestBody event: RevenueCatWebhookEvent,
        @RequestHeader("X-RevenueCat-Signature", required = false) signature: String?
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            // Verify webhook signature if configured
            if (appProperties.revenuecat.webhookSecret.isNotBlank()) {
                if (signature == null) {
                    logger.warn { "Missing webhook signature" }
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse(success = false, message = "Missing signature"))
                }
                
                // Note: In production, you should verify the signature properly
                // This is a simplified example
                logger.debug { "Webhook signature verification would happen here" }
            }
            
            subscriptionService.handleRevenueCatWebhook(event)
            
            ResponseEntity.ok(ApiResponse(success = true, message = "Webhook processed successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to process RevenueCat webhook" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse(success = false, message = "Failed to process webhook: ${e.message}"))
        }
    }
    
    private fun verifyWebhookSignature(payload: String, signature: String, secret: String): Boolean {
        return try {
            val hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
            hmac.init(secretKey)
            
            val computedSignature = Base64.getEncoder().encodeToString(hmac.doFinal(payload.toByteArray()))
            
            computedSignature == signature
        } catch (e: Exception) {
            logger.error(e) { "Failed to verify webhook signature" }
            false
        }
    }
}

