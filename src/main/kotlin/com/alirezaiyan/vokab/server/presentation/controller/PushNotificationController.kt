package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.*
import com.alirezaiyan.vokab.server.service.PushNotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/notifications")
class PushNotificationController(
    private val pushNotificationService: PushNotificationService
) {
    
    @PostMapping("/register-token")
    fun registerPushToken(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: RegisterPushTokenRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            pushNotificationService.registerPushToken(
                userId = user.id!!,
                token = request.token,
                platform = request.platform,
                deviceId = request.deviceId
            )
            ResponseEntity.ok(ApiResponse(success = true, message = "Push token registered successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to register push token" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to register token: ${e.message}"))
        }
    }
    
    @DeleteMapping("/token/{token}")
    fun deactivateToken(
        @AuthenticationPrincipal user: User,
        @PathVariable token: String
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            pushNotificationService.deactivatePushToken(token)
            ResponseEntity.ok(ApiResponse(success = true, message = "Token deactivated successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to deactivate token" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to deactivate token: ${e.message}"))
        }
    }
    
    @DeleteMapping("/tokens")
    fun deactivateAllTokens(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            pushNotificationService.deactivateAllUserTokens(user.id!!)
            ResponseEntity.ok(ApiResponse(success = true, message = "All tokens deactivated successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to deactivate tokens" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to deactivate tokens: ${e.message}"))
        }
    }
    
    @PostMapping("/send")
    fun sendNotification(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: SendNotificationRequest
    ): ResponseEntity<ApiResponse<List<NotificationResponse>>> {
        return try {
            val responses = pushNotificationService.sendNotificationToUser(
                userId = user.id!!,
                title = request.title,
                body = request.body,
                data = request.data,
                imageUrl = request.imageUrl
            )
            ResponseEntity.ok(ApiResponse(success = true, data = responses))
        } catch (e: Exception) {
            logger.error(e) { "Failed to send notification" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to send notification: ${e.message}"))
        }
    }
    
    @GetMapping("/tokens")
    fun getUserTokens(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<Int>> {
        return try {
            val tokens = pushNotificationService.getUserTokens(user.id!!)
            ResponseEntity.ok(ApiResponse(success = true, data = tokens.size, message = "${tokens.size} active tokens"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get user tokens" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to get tokens: ${e.message}"))
        }
    }
}

