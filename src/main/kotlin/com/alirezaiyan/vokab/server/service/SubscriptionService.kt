package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.Subscription
import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.SubscriptionRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.RevenueCatWebhookEvent
import com.alirezaiyan.vokab.server.presentation.dto.SubscriptionDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository
) {
    
    @Transactional
    fun handleRevenueCatWebhook(event: RevenueCatWebhookEvent) {
        logger.info { "Processing RevenueCat webhook event: ${event.event.type}" }
        
        val user = getUserByRevenueCatId(event.app_user_id)
            ?: createUserForRevenueCat(event.app_user_id)
        
        when (event.event.type) {
            "INITIAL_PURCHASE" -> handleInitialPurchase(user, event)
            "RENEWAL" -> handleRenewal(user, event)
            "CANCELLATION" -> handleCancellation(user, event)
            "UNCANCELLATION" -> handleUncancellation(user, event)
            "NON_RENEWING_PURCHASE" -> handleNonRenewingPurchase(user, event)
            "EXPIRATION" -> handleExpiration(user, event)
            "BILLING_ISSUE" -> handleBillingIssue(user, event)
            "SUBSCRIBER_ALIAS" -> handleSubscriberAlias(user, event)
            else -> logger.warn { "Unknown webhook event type: ${event.event.type}" }
        }
    }
    
    @Transactional(readOnly = true)
    fun getUserSubscriptions(userId: Long): List<SubscriptionDto> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        return subscriptionRepository.findByUser(user)
            .map { it.toDto() }
    }
    
    @Transactional(readOnly = true)
    fun getActiveSubscription(userId: Long): SubscriptionDto? {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        return subscriptionRepository.findActiveSubscription(user)
            .map { it.toDto() }
            .orElse(null)
    }
    
    private fun handleInitialPurchase(user: User, event: RevenueCatWebhookEvent) {
        val productId = event.product_id ?: return
        val startedAt = Instant.ofEpochMilli(event.purchased_at_ms ?: System.currentTimeMillis())
        val expiresAt = event.expiration_at_ms?.let { Instant.ofEpochMilli(it) }
        
        val subscription = Subscription(
            user = user,
            revenueCatSubscriptionId = event.event.id,
            productId = productId,
            status = SubscriptionStatus.ACTIVE,
            startedAt = startedAt,
            expiresAt = expiresAt,
            isTrial = event.is_trial_conversion == true
        )
        
        subscriptionRepository.save(subscription)
        
        // Update user subscription status
        updateUserSubscriptionStatus(user, SubscriptionStatus.ACTIVE, expiresAt)
        
        logger.info { "Initial purchase processed for user: ${user.email}, product: $productId" }
    }
    
    private fun handleRenewal(user: User, event: RevenueCatWebhookEvent) {
        val expiresAt = event.expiration_at_ms?.let { Instant.ofEpochMilli(it) }
        
        val subscription = subscriptionRepository.findByRevenueCatSubscriptionId(event.event.id)
            .orElse(null)
        
        if (subscription != null) {
            val updated = subscription.copy(
                status = SubscriptionStatus.ACTIVE,
                expiresAt = expiresAt,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updated)
        }
        
        updateUserSubscriptionStatus(user, SubscriptionStatus.ACTIVE, expiresAt)
        
        logger.info { "Subscription renewed for user: ${user.email}" }
    }
    
    private fun handleCancellation(user: User, event: RevenueCatWebhookEvent) {
        val cancelledAt = event.cancellation_at_ms?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        val expiresAt = event.expiration_at_ms?.let { Instant.ofEpochMilli(it) }
        
        val subscription = subscriptionRepository.findByRevenueCatSubscriptionId(event.event.id)
            .orElse(null)
        
        if (subscription != null) {
            val updated = subscription.copy(
                status = SubscriptionStatus.CANCELLED,
                cancelledAt = cancelledAt,
                autoRenew = false,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updated)
        }
        
        // User still has access until expiration
        val status = if (expiresAt != null && expiresAt.isAfter(Instant.now())) {
            SubscriptionStatus.CANCELLED
        } else {
            SubscriptionStatus.EXPIRED
        }
        
        updateUserSubscriptionStatus(user, status, expiresAt)
        
        logger.info { "Subscription cancelled for user: ${user.email}" }
    }
    
    private fun handleUncancellation(user: User, event: RevenueCatWebhookEvent) {
        val expiresAt = event.expiration_at_ms?.let { Instant.ofEpochMilli(it) }
        
        val subscription = subscriptionRepository.findByRevenueCatSubscriptionId(event.event.id)
            .orElse(null)
        
        if (subscription != null) {
            val updated = subscription.copy(
                status = SubscriptionStatus.ACTIVE,
                cancelledAt = null,
                autoRenew = true,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updated)
        }
        
        updateUserSubscriptionStatus(user, SubscriptionStatus.ACTIVE, expiresAt)
        
        logger.info { "Subscription uncancelled for user: ${user.email}" }
    }
    
    private fun handleNonRenewingPurchase(user: User, event: RevenueCatWebhookEvent) {
        val productId = event.product_id ?: return
        val startedAt = Instant.ofEpochMilli(event.purchased_at_ms ?: System.currentTimeMillis())
        val expiresAt = event.expiration_at_ms?.let { Instant.ofEpochMilli(it) }
        
        val subscription = Subscription(
            user = user,
            revenueCatSubscriptionId = event.event.id,
            productId = productId,
            status = SubscriptionStatus.ACTIVE,
            startedAt = startedAt,
            expiresAt = expiresAt,
            autoRenew = false
        )
        
        subscriptionRepository.save(subscription)
        updateUserSubscriptionStatus(user, SubscriptionStatus.ACTIVE, expiresAt)
        
        logger.info { "Non-renewing purchase processed for user: ${user.email}" }
    }
    
    private fun handleExpiration(user: User, event: RevenueCatWebhookEvent) {
        val subscription = subscriptionRepository.findByRevenueCatSubscriptionId(event.event.id)
            .orElse(null)
        
        if (subscription != null) {
            val updated = subscription.copy(
                status = SubscriptionStatus.EXPIRED,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updated)
        }
        
        updateUserSubscriptionStatus(user, SubscriptionStatus.EXPIRED, null)
        
        logger.info { "Subscription expired for user: ${user.email}" }
    }
    
    private fun handleBillingIssue(user: User, event: RevenueCatWebhookEvent) {
        logger.warn { "Billing issue for user: ${user.email}" }
        // Could send notification to user about billing issue
    }
    
    private fun handleSubscriberAlias(user: User, event: RevenueCatWebhookEvent) {
        logger.info { "Subscriber alias event for user: ${user.email}" }
        // Handle subscriber alias if needed
    }
    
    private fun getUserByRevenueCatId(revenueCatUserId: String): User? {
        return userRepository.findByRevenueCatUserId(revenueCatUserId).orElse(null)
    }
    
    private fun createUserForRevenueCat(revenueCatUserId: String): User {
        val user = User(
            email = "$revenueCatUserId@revenuecat.temporary",
            name = "RevenueCat User",
            revenueCatUserId = revenueCatUserId
        )
        return userRepository.save(user)
    }
    
    private fun updateUserSubscriptionStatus(
        user: User,
        status: SubscriptionStatus,
        expiresAt: Instant?
    ) {
        val updated = user.copy(
            subscriptionStatus = status,
            subscriptionExpiresAt = expiresAt,
            updatedAt = Instant.now()
        )
        userRepository.save(updated)
    }
    
    private fun Subscription.toDto(): SubscriptionDto {
        return SubscriptionDto(
            id = this.id!!,
            productId = this.productId,
            status = this.status,
            startedAt = this.startedAt.toString(),
            expiresAt = this.expiresAt?.toString(),
            cancelledAt = this.cancelledAt?.toString(),
            isTrial = this.isTrial,
            autoRenew = this.autoRenew
        )
    }
}

