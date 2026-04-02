package com.alirezaiyan.vokab.server.service.email

import com.alirezaiyan.vokab.server.domain.entity.EmailSubscription
import com.alirezaiyan.vokab.server.domain.repository.EmailSubscriptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class EmailSubscriptionService(
    private val emailSubscriptionRepository: EmailSubscriptionRepository
) {

    @Transactional(readOnly = true)
    fun getPreferences(userId: Long): List<EmailSubscription> {
        return emailSubscriptionRepository.findByUserId(userId)
    }

    @Transactional
    fun subscribe(userId: Long, category: String): EmailSubscription {
        val existing = emailSubscriptionRepository.findByUserIdAndCategory(userId, category)
        return if (existing != null) {
            emailSubscriptionRepository.save(existing.copy(subscribed = true, updatedAt = Instant.now()))
        } else {
            emailSubscriptionRepository.save(
                EmailSubscription(userId = userId, category = category, subscribed = true)
            )
        }.also { logger.info { "User $userId subscribed to $category" } }
    }

    @Transactional
    fun unsubscribe(userId: Long, category: String): EmailSubscription {
        val existing = emailSubscriptionRepository.findByUserIdAndCategory(userId, category)
        return if (existing != null) {
            emailSubscriptionRepository.save(existing.copy(subscribed = false, updatedAt = Instant.now()))
        } else {
            emailSubscriptionRepository.save(
                EmailSubscription(userId = userId, category = category, subscribed = false)
            )
        }.also { logger.info { "User $userId unsubscribed from $category" } }
    }

    /**
     * Bootstrap default subscriptions for a new user.
     * Call this during user registration.
     */
    @Transactional
    fun initDefaults(userId: Long, categories: List<String> = DEFAULT_CATEGORIES) {
        categories.forEach { category ->
            if (emailSubscriptionRepository.findByUserIdAndCategory(userId, category) == null) {
                emailSubscriptionRepository.save(
                    EmailSubscription(userId = userId, category = category, subscribed = true)
                )
            }
        }
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf("newsletter", "product_updates", "weekly_digest")
    }
}
