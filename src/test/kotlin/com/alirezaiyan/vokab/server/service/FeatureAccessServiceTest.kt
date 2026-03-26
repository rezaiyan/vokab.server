package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.config.FeatureFlagsConfig
import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class FeatureAccessServiceTest {

    private lateinit var appProperties: AppProperties
    private lateinit var userRepository: UserRepository
    private lateinit var featureAccessService: FeatureAccessService

    @BeforeEach
    fun setUp() {
        appProperties = mockk()
        userRepository = mockk()
        featureAccessService = FeatureAccessService(appProperties, userRepository)
    }

    // --- hasActivePremiumAccess ---

    @Test
    fun `hasActivePremiumAccess should return true when status is ACTIVE and not expired`() {
        // Arrange
        val user = createUser(
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = Instant.now().plusSeconds(86400)
        )

        // Act
        val result = featureAccessService.hasActivePremiumAccess(user)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `hasActivePremiumAccess should return true when status is TRIAL and not expired`() {
        // Arrange
        val user = createUser(
            subscriptionStatus = SubscriptionStatus.TRIAL,
            subscriptionExpiresAt = Instant.now().plusSeconds(86400)
        )

        // Act
        val result = featureAccessService.hasActivePremiumAccess(user)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `hasActivePremiumAccess should return false when status is ACTIVE but expired`() {
        // Arrange
        val user = createUser(
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = Instant.now().minusSeconds(1)
        )

        // Act
        val result = featureAccessService.hasActivePremiumAccess(user)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `hasActivePremiumAccess should return false when status is FREE`() {
        // Arrange
        val user = createUser(subscriptionStatus = SubscriptionStatus.FREE)

        // Act
        val result = featureAccessService.hasActivePremiumAccess(user)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `hasActivePremiumAccess should return false when status is EXPIRED`() {
        // Arrange
        val user = createUser(subscriptionStatus = SubscriptionStatus.EXPIRED)

        // Act
        val result = featureAccessService.hasActivePremiumAccess(user)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `hasActivePremiumAccess should return false when status is CANCELLED`() {
        // Arrange
        val user = createUser(subscriptionStatus = SubscriptionStatus.CANCELLED)

        // Act
        val result = featureAccessService.hasActivePremiumAccess(user)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `hasActivePremiumAccess should return true when status is ACTIVE and expiresAt is null`() {
        // Arrange
        val user = createUser(
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null
        )

        // Act
        val result = featureAccessService.hasActivePremiumAccess(user)

        // Assert
        assertTrue(result)
    }

    // --- getClientFeatureFlags ---

    @Test
    fun `getClientFeatureFlags should return push notifications enabled from app properties`() {
        // Arrange
        every { appProperties.features } returns FeatureFlagsConfig(pushNotificationsEnabled = true)

        // Act
        val result = featureAccessService.getClientFeatureFlags()

        // Assert
        assertTrue(result.pushNotificationsEnabled)
    }

    @Test
    fun `getClientFeatureFlags should return push notifications disabled from app properties`() {
        // Arrange
        every { appProperties.features } returns FeatureFlagsConfig(pushNotificationsEnabled = false)

        // Act
        val result = featureAccessService.getClientFeatureFlags()

        // Assert
        assertFalse(result.pushNotificationsEnabled)
    }

    // --- getUserFeatureAccess ---

    @Test
    fun `getUserFeatureAccess should return premium access status as true for active subscription`() {
        // Arrange
        val user = createUser(
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = Instant.now().plusSeconds(86400)
        )

        // Act
        val result = featureAccessService.getUserFeatureAccess(user)

        // Assert
        assertTrue(result.hasPremiumAccess)
    }

    @Test
    fun `getUserFeatureAccess should return premium access status as false for free user`() {
        // Arrange
        val user = createUser(subscriptionStatus = SubscriptionStatus.FREE)

        // Act
        val result = featureAccessService.getUserFeatureAccess(user)

        // Assert
        assertFalse(result.hasPremiumAccess)
    }

    // --- Factory functions ---

    private fun createUser(
        id: Long = 1L,
        email: String = "test@example.com",
        subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
        subscriptionExpiresAt: Instant? = null
    ): User = User(
        id = id,
        email = email,
        name = "Test User",
        subscriptionStatus = subscriptionStatus,
        subscriptionExpiresAt = subscriptionExpiresAt,
        currentStreak = 0,
        longestStreak = 0,
        active = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
