package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.Platform
import com.alirezaiyan.vokab.server.domain.entity.PushToken
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.PushTokenRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.NotificationResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class PushNotificationService(
    private val pushTokenRepository: PushTokenRepository,
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun registerPushToken(
        userId: Long,
        token: String,
        platform: Platform,
        deviceId: String?
    ): PushToken {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        // Check if token already exists
        val existingToken = pushTokenRepository.findByToken(token)
        
        return if (existingToken.isPresent) {
            val existing = existingToken.get()
            if (existing.user.id != userId) {
                // Token belongs to different user, deactivate old and create new
                pushTokenRepository.deactivateByToken(token)
                createNewPushToken(user, token, platform, deviceId)
            } else {
                // Update existing token
                val updated = existing.copy(
                    platform = platform,
                    deviceId = deviceId,
                    updatedAt = Instant.now(),
                    active = true
                )
                pushTokenRepository.save(updated)
            }
        } else {
            createNewPushToken(user, token, platform, deviceId)
        }
    }
    
    @Transactional
    fun deactivatePushToken(token: String) {
        pushTokenRepository.deactivateByToken(token)
        logger.info { "Push token deactivated" }
    }
    
    @Transactional
    fun deactivateAllUserTokens(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        pushTokenRepository.deactivateAllByUser(user)
        logger.info { "All push tokens deactivated for user: $userId" }
    }
    
    fun sendNotificationToUser(
        userId: Long,
        title: String,
        body: String,
        data: Map<String, String>? = null,
        imageUrl: String? = null
    ): List<NotificationResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val tokens = pushTokenRepository.findByUserAndActiveTrue(user)
        
        if (tokens.isEmpty()) {
            logger.warn { "No active push tokens found for user: $userId" }
            return emptyList()
        }
        
        return tokens.map { pushToken ->
            sendNotification(pushToken.token, title, body, data, imageUrl)
        }
    }
    
    fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>? = null,
        imageUrl: String? = null
    ): NotificationResponse {
        return try {
            val notificationBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body)
            
            imageUrl?.let { notificationBuilder.setImage(it) }
            
            val messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(notificationBuilder.build())
            
            data?.let { messageBuilder.putAllData(it) }
            
            val message = messageBuilder.build()
            
            val messageId = FirebaseMessaging.getInstance().send(message)
            
            logger.info { "Notification sent successfully. Message ID: $messageId" }
            
            NotificationResponse(
                success = true,
                messageId = messageId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to send notification" }
            
            // If token is invalid, deactivate it
            if (e.message?.contains("registration-token-not-registered") == true ||
                e.message?.contains("invalid-registration-token") == true) {
                deactivatePushToken(token)
            }
            
            NotificationResponse(
                success = false,
                error = e.message
            )
        }
    }
    
    @Transactional(readOnly = true)
    fun getUserTokens(userId: Long): List<PushToken> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        return pushTokenRepository.findByUserAndActiveTrue(user)
    }
    
    private fun createNewPushToken(
        user: User,
        token: String,
        platform: Platform,
        deviceId: String?
    ): PushToken {
        val pushToken = PushToken(
            user = user,
            token = token,
            platform = platform,
            deviceId = deviceId
        )
        val saved = pushTokenRepository.save(pushToken)
        logger.info { "Push token registered for user: ${user.email}, platform: $platform" }
        return saved
    }
}

