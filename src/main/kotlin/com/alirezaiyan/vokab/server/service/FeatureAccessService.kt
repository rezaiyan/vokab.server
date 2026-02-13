package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class FeatureAccessService(
    private val appProperties: AppProperties,
    private val userRepository: UserRepository
) {
    
    /**
     * Check if a user has access to premium features
     *
     * Logic: Check if user's subscription is active and not expired
     */
    fun hasActivePremiumAccess(user: User): Boolean {
        return when (user.subscriptionStatus) {
            SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL -> {
                // Check if subscription has expired
                val expiresAt = user.subscriptionExpiresAt
                if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                    logger.info { "Subscription expired for user ${user.email} at $expiresAt" }
                    false
                } else {
                    logger.debug { "User ${user.email} has active premium access (${user.subscriptionStatus})" }
                    true
                }
            }
            SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED, SubscriptionStatus.FREE -> {
                logger.debug { "User ${user.email} does not have premium access (${user.subscriptionStatus})" }
                false
            }
        }
    }
    
    /**
     * Get feature flags for client (doesn't include sensitive server config)
     */
    fun getClientFeatureFlags(): ClientFeatureFlags {
        return ClientFeatureFlags(
            pushNotificationsEnabled = appProperties.features.pushNotificationsEnabled
        )
    }

    /**
     * Get user's feature access status
     */
    fun getUserFeatureAccess(user: User): UserFeatureAccess {
        return UserFeatureAccess(
            hasPremiumAccess = hasActivePremiumAccess(user)
        )
    }
}

/**
 * Feature flags safe to send to client
 */
data class ClientFeatureFlags(
    val pushNotificationsEnabled: Boolean
)

/**
 * User's personal feature access status
 * Simple binary premium/not-premium model
 */
data class UserFeatureAccess(
    val hasPremiumAccess: Boolean
)


