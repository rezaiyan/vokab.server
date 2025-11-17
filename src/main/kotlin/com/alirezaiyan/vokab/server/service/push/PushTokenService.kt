package com.alirezaiyan.vokab.server.service.push

import com.alirezaiyan.vokab.server.domain.entity.Platform
import com.alirezaiyan.vokab.server.domain.entity.PushToken
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.PushTokenRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class PushTokenService(
    private val pushTokenRepository: PushTokenRepository,
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun registerToken(
        userId: Long,
        token: String,
        platform: Platform,
        deviceId: String?
    ): PushToken {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val existingToken = pushTokenRepository.findByToken(token)
        
        return if (existingToken.isPresent) {
            handleExistingToken(existingToken.get(), user, token, platform, deviceId)
        } else {
            createNewToken(user, token, platform, deviceId)
        }
    }
    
    @Transactional
    fun deactivateToken(token: String) {
        pushTokenRepository.deactivateByToken(token)
        logger.info { "Push token deactivated: ${token.take(20)}..." }
    }
    
    @Transactional
    fun deactivateAllUserTokens(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        pushTokenRepository.deactivateAllByUser(user)
        logger.info { "All push tokens deactivated for user: $userId" }
    }
    
    @Transactional(readOnly = true)
    fun getActiveTokensForUser(userId: Long): List<PushToken> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        return pushTokenRepository.findByUserAndActiveTrue(user)
    }
    
    private fun handleExistingToken(
        existing: PushToken,
        user: User,
        token: String,
        platform: Platform,
        deviceId: String?
    ): PushToken {
        return if (existing.user.id != user.id) {
            pushTokenRepository.deactivateByToken(token)
            createNewToken(user, token, platform, deviceId)
        } else {
            updateExistingToken(existing, platform, deviceId)
        }
    }
    
    private fun updateExistingToken(
        existing: PushToken,
        platform: Platform,
        deviceId: String?
    ): PushToken {
        val updated = existing.copy(
            platform = platform,
            deviceId = deviceId,
            updatedAt = Instant.now(),
            active = true
        )
        return pushTokenRepository.save(updated)
    }
    
    private fun createNewToken(
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


