package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.SubscriptionDto
import com.alirezaiyan.vokab.server.service.SubscriptionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/subscriptions")
class SubscriptionController(
    private val subscriptionService: SubscriptionService
) {
    
    @GetMapping
    fun getUserSubscriptions(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<List<SubscriptionDto>>> {
        return try {
            val subscriptions = subscriptionService.getUserSubscriptions(user.id!!)
            ResponseEntity.ok(ApiResponse(success = true, data = subscriptions))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get user subscriptions" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to get subscriptions: ${e.message}"))
        }
    }
    
    @GetMapping("/active")
    fun getActiveSubscription(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<SubscriptionDto?>> {
        return try {
            val subscription = subscriptionService.getActiveSubscription(user.id!!)
            ResponseEntity.ok(ApiResponse(success = true, data = subscription))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get active subscription" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to get active subscription: ${e.message}"))
        }
    }
}

