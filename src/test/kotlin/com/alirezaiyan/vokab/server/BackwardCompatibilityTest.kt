package com.alirezaiyan.vokab.server

import com.alirezaiyan.vokab.server.domain.entity.*
import com.alirezaiyan.vokab.server.domain.repository.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BackwardCompatibilityTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var wordRepository: WordRepository

    @Autowired
    private lateinit var userSettingsRepository: UserSettingsRepository

    @Autowired
    private lateinit var dailyInsightRepository: DailyInsightRepository

    @Autowired
    private lateinit var dailyActivityRepository: DailyActivityRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired
    private lateinit var pushTokenRepository: PushTokenRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Test
    fun `existing user without refresh tokens should work correctly`() {
        val user = User(
            email = "legacy@example.com",
            name = "Legacy User",
            subscriptionStatus = SubscriptionStatus.FREE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            currentStreak = 5,
            longestStreak = 10,
            aiExtractionUsageCount = 0,
            active = true
        )
        val savedUser = userRepository.save(user)
        assertNotNull(savedUser.id)

        val word = Word(
            user = savedUser,
            originalWord = "hello",
            translation = "hola",
            sourceLanguage = "en",
            targetLanguage = "es",
            level = 1,
            easeFactor = 2.5f,
            interval = 1,
            repetitions = 0,
            lastReviewDate = 0L,
            nextReviewDate = 0L,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedWord = wordRepository.save(word)
        assertNotNull(savedWord.id)

        val settings = UserSettings(
            user = savedUser,
            languageCode = "en",
            themeMode = "AUTO",
            notificationsEnabled = true,
            reviewReminders = true,
            motivationalMessages = true,
            dailyReminderTime = "18:00",
            minimumDueCards = 5,
            successesToAdvance = 1,
            forgotPenalty = 2
        )
        val savedSettings = userSettingsRepository.save(settings)
        assertNotNull(savedSettings.id)

        val retrievedUser = userRepository.findById(savedUser.id!!).orElse(null)
        assertNotNull(retrievedUser)
        assertTrue(retrievedUser.email == "legacy@example.com")
    }

    @Test
    fun `new refresh tokens table should work with existing users`() {
        val user = User(
            email = "newuser@example.com",
            name = "New User",
            subscriptionStatus = SubscriptionStatus.FREE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            currentStreak = 0,
            longestStreak = 0,
            aiExtractionUsageCount = 0,
            active = true
        )
        val savedUser = userRepository.save(user)

        val refreshToken = RefreshToken(
            tokenHash = "test_hash_123",
            user = savedUser,
            familyId = "family_123",
            expiresAt = Instant.now().plusSeconds(604800),
            createdAt = Instant.now(),
            revoked = false
        )
        val savedToken = refreshTokenRepository.save(refreshToken)
        assertNotNull(savedToken.id)

        val retrievedToken = refreshTokenRepository.findById(savedToken.id!!).orElse(null)
        assertNotNull(retrievedToken)
        assertTrue(retrievedToken.tokenHash == "test_hash_123")
    }

    @Test
    fun `all existing entities should persist and retrieve correctly`() {
        val user = User(
            email = "compat@example.com",
            name = "Compat User",
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            currentStreak = 3,
            longestStreak = 7,
            aiExtractionUsageCount = 2,
            active = true
        )
        val savedUser = userRepository.save(user)

        val pushToken = PushToken(
            user = savedUser,
            token = "push_token_123",
            platform = Platform.ANDROID,
            deviceId = "device_123",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = true
        )
        val savedPushToken = pushTokenRepository.save(pushToken)
        assertNotNull(savedPushToken.id)

        val dailyInsight = DailyInsight(
            user = savedUser,
            insightText = "Test insight",
            generatedAt = Instant.now(),
            date = "2025-01-01",
            sentViaPush = false
        )
        val savedInsight = dailyInsightRepository.save(dailyInsight)
        assertNotNull(savedInsight.id)

        val dailyActivity = DailyActivity(
            user = savedUser,
            activityDate = LocalDate.now(),
            reviewCount = 5
        )
        val savedActivity = dailyActivityRepository.save(dailyActivity)
        assertNotNull(savedActivity.id)

        val subscription = Subscription(
            user = savedUser,
            productId = "premium_monthly",
            status = SubscriptionStatus.ACTIVE,
            startedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(2592000),
            isTrial = false,
            autoRenew = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val savedSubscription = subscriptionRepository.save(subscription)
        assertNotNull(savedSubscription.id)

        val retrievedUser = userRepository.findById(savedUser.id!!).orElse(null)
        assertNotNull(retrievedUser)
        assertTrue(retrievedUser.email == "compat@example.com")
    }
}

