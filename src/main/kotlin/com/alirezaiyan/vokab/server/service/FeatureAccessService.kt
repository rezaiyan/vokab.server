package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import com.alirezaiyan.vokab.server.domain.entity.User
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class FeatureAccessService(
    private val appProperties: AppProperties
) {
    
    /**
     * Check if premium features are globally enabled via feature flag
     */
    fun arePremiumFeaturesEnabled(): Boolean {
        return appProperties.features.premiumFeaturesEnabled
    }
    
    /**
     * Check if AI image extraction is enabled
     */
    fun isAiImageExtractionEnabled(): Boolean {
        return appProperties.features.aiImageExtractionEnabled
    }
    
    /**
     * Check if AI daily insight is enabled
     */
    fun isAiDailyInsightEnabled(): Boolean {
        return appProperties.features.aiDailyInsightEnabled
    }
    
    /**
     * Check if a user has access to premium features
     * 
     * Logic:
     * 1. If premium features are disabled globally (feature flag), return false for everyone
     * 2. Check if user's subscription is active and not expired
     */
    fun hasActivePremiumAccess(user: User): Boolean {
        // Check global feature flag first
        if (!arePremiumFeaturesEnabled()) {
            logger.debug { "Premium features globally disabled for all users" }
            return false
        }
        
        // Check user's subscription status
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
     * Check if user can use AI image extraction
     * 
     * Logic:
     * 1. If AI feature is disabled globally, return false for everyone
     * 2. If user has premium, unlimited usage
     * 3. If user is free tier, check if they have remaining free usages
     */
    fun canUseAiImageExtraction(user: User): Boolean {
        if (!isAiImageExtractionEnabled()) {
            return false
        }
        
        // Premium users have unlimited access
        if (hasActivePremiumAccess(user)) {
            return true
        }
        
        // Free users get limited usages
        val freeLimit = appProperties.features.freeAiExtractionLimit
        val remainingUsages = freeLimit - user.aiExtractionUsageCount
        
        logger.debug { "Free user ${user.email}: ${user.aiExtractionUsageCount}/$freeLimit AI extractions used, remaining: $remainingUsages" }
        
        return remainingUsages > 0
    }
    
    /**
     * Get remaining free AI extraction usages for a user
     */
    fun getRemainingAiExtractionUsages(user: User): Int {
        // Premium users have unlimited
        if (hasActivePremiumAccess(user)) {
            return Int.MAX_VALUE
        }
        
        val freeLimit = appProperties.features.freeAiExtractionLimit
        val remaining = freeLimit - user.aiExtractionUsageCount
        return maxOf(0, remaining)
    }
    
    /**
     * Increment AI extraction usage count
     * Only increment for free users (premium users have unlimited)
     */
    fun incrementAiExtractionUsage(user: User) {
        if (!hasActivePremiumAccess(user)) {
            user.aiExtractionUsageCount++
            logger.info { "Incremented AI extraction usage for ${user.email}: ${user.aiExtractionUsageCount}/${appProperties.features.freeAiExtractionLimit}" }
        }
    }
    
    /**
     * Check if user can use AI daily insight
     * Requires: AI feature enabled + Premium access
     */
    fun canUseAiDailyInsight(user: User): Boolean {
        if (!isAiDailyInsightEnabled()) {
            return false
        }
        return hasActivePremiumAccess(user)
    }
    
    /**
     * Get feature flags for client (doesn't include sensitive server config)
     */
    fun getClientFeatureFlags(): ClientFeatureFlags {
        return ClientFeatureFlags(
            premiumFeaturesEnabled = appProperties.features.premiumFeaturesEnabled,
            aiImageExtractionEnabled = appProperties.features.aiImageExtractionEnabled,
            aiDailyInsightEnabled = appProperties.features.aiDailyInsightEnabled,
            pushNotificationsEnabled = appProperties.features.pushNotificationsEnabled,
            subscriptionsEnabled = appProperties.features.subscriptionsEnabled
        )
    }
    
    /**
     * Get user's feature access status
     */
    fun getUserFeatureAccess(user: User): UserFeatureAccess {
        val hasPremium = hasActivePremiumAccess(user)
        
        return UserFeatureAccess(
            hasPremiumAccess = hasPremium,
            canUseAiImageExtraction = canUseAiImageExtraction(user),
            canUseAiDailyInsight = canUseAiDailyInsight(user),
            subscriptionStatus = user.subscriptionStatus,
            subscriptionExpiresAt = user.subscriptionExpiresAt?.toString(),
            aiExtractionUsageCount = user.aiExtractionUsageCount,
            aiExtractionUsageLimit = appProperties.features.freeAiExtractionLimit,
            remainingAiExtractions = getRemainingAiExtractionUsages(user)
        )
    }
}

/**
 * Feature flags safe to send to client
 */
data class ClientFeatureFlags(
    val premiumFeaturesEnabled: Boolean,
    val aiImageExtractionEnabled: Boolean,
    val aiDailyInsightEnabled: Boolean,
    val pushNotificationsEnabled: Boolean,
    val subscriptionsEnabled: Boolean
)

/**
 * User's personal feature access status
 */
data class UserFeatureAccess(
    val hasPremiumAccess: Boolean,
    val canUseAiImageExtraction: Boolean,
    val canUseAiDailyInsight: Boolean,
    val subscriptionStatus: SubscriptionStatus,
    val subscriptionExpiresAt: String?,
    val aiExtractionUsageCount: Int,
    val aiExtractionUsageLimit: Int,
    val remainingAiExtractions: Int
)


