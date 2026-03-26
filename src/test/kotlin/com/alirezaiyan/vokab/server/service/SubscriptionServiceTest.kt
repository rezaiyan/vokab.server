package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.Subscription
import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.SubscriptionRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.RevenueCatEvent
import com.alirezaiyan.vokab.server.presentation.dto.RevenueCatWebhookEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional

class SubscriptionServiceTest {

    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var userRepository: UserRepository
    private lateinit var eventService: EventService

    private lateinit var subscriptionService: SubscriptionService

    @BeforeEach
    fun setUp() {
        subscriptionRepository = mockk()
        userRepository = mockk()
        eventService = mockk()

        subscriptionService = SubscriptionService(subscriptionRepository, userRepository, eventService)

        // Default stubs used by many tests — assign id=1L to newly saved users so id!! won't NPE
        every { userRepository.save(any()) } answers {
            val u = firstArg<User>()
            if (u.id == null) u.copy(id = 1L) else u
        }
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        every { eventService.trackAsync(any(), any(), any()) } just Runs
    }

    // ── handleRevenueCatWebhook: user resolution ───────────────────────────────

    @Test
    fun `should use existing user when revenueCatUserId is found`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId(any()) } returns Optional.empty()

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "RENEWAL"))

        verify(exactly = 0) { userRepository.save(match { it.email.endsWith("@revenuecat.temporary") }) }
    }

    @Test
    fun `should create new user when revenueCatUserId is not found`() {
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.empty()
        every { subscriptionRepository.findByRevenueCatSubscriptionId(any()) } returns Optional.empty()

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "RENEWAL"))

        verify(atLeast = 1) {
            userRepository.save(match {
                it.email == "rc-user-1@revenuecat.temporary" && it.revenueCatUserId == "rc-user-1"
            })
        }
    }

    // ── INITIAL_PURCHASE ───────────────────────────────────────────────────────

    @Test
    fun `should save subscription and activate user on INITIAL_PURCHASE`() {
        val user = createUser()
        val futureExpiry = Instant.now().plusSeconds(86400)
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(
                type = "INITIAL_PURCHASE",
                productId = "premium_monthly",
                purchasedAtMs = System.currentTimeMillis(),
                expirationAtMs = futureExpiry.toEpochMilli(),
                isTrial = false,
            )
        )

        val savedSubscription = slot<Subscription>()
        verify(exactly = 1) { subscriptionRepository.save(capture(savedSubscription)) }
        assertEquals(SubscriptionStatus.ACTIVE, savedSubscription.captured.status)
        assertEquals("premium_monthly", savedSubscription.captured.productId)
        assertEquals(false, savedSubscription.captured.isTrial)

        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.ACTIVE }) }
    }

    @Test
    fun `should track subscription_started event on INITIAL_PURCHASE when not trial`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "INITIAL_PURCHASE", productId = "premium_monthly", isTrial = false)
        )

        verify(exactly = 1) { eventService.trackAsync(1L, "subscription_started", any()) }
    }

    @Test
    fun `should track trial_started event on INITIAL_PURCHASE when isTrial is true`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "INITIAL_PURCHASE", productId = "premium_monthly", isTrial = true)
        )

        verify(exactly = 1) { eventService.trackAsync(1L, "trial_started", any()) }
    }

    @Test
    fun `should skip INITIAL_PURCHASE processing when productId is null`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "INITIAL_PURCHASE", productId = null)
        )

        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    // ── RENEWAL ────────────────────────────────────────────────────────────────

    @Test
    fun `should update existing subscription to ACTIVE on RENEWAL`() {
        val user = createUser()
        val existingSubscription = createSubscription(user, status = SubscriptionStatus.CANCELLED)
        val futureExpiry = Instant.now().plusSeconds(86400)
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId("event-id-1") } returns Optional.of(existingSubscription)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "RENEWAL", expirationAtMs = futureExpiry.toEpochMilli())
        )

        verify(exactly = 1) {
            subscriptionRepository.save(match {
                it.status == SubscriptionStatus.ACTIVE &&
                    it.expiresAt?.toEpochMilli() == futureExpiry.toEpochMilli()
            })
        }
        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.ACTIVE }) }
    }

    @Test
    fun `should still update user on RENEWAL when no existing subscription found`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId("event-id-1") } returns Optional.empty()

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "RENEWAL"))

        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.ACTIVE }) }
    }

    @Test
    fun `should track subscription_renewed event on RENEWAL`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId(any()) } returns Optional.empty()

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "RENEWAL"))

        verify(exactly = 1) { eventService.trackAsync(1L, "subscription_renewed", any()) }
    }

    // ── CANCELLATION ───────────────────────────────────────────────────────────

    @Test
    fun `should set status to CANCELLED when expiresAt is in the future on CANCELLATION`() {
        val user = createUser()
        val futureExpiry = Instant.now().plusSeconds(86400)
        val existingSubscription = createSubscription(user, status = SubscriptionStatus.ACTIVE)
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId("event-id-1") } returns Optional.of(existingSubscription)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "CANCELLATION", expirationAtMs = futureExpiry.toEpochMilli())
        )

        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.CANCELLED }) }
    }

    @Test
    fun `should set status to EXPIRED when expiresAt is in the past on CANCELLATION`() {
        val user = createUser()
        val pastExpiry = Instant.now().minusSeconds(86400)
        val existingSubscription = createSubscription(user, status = SubscriptionStatus.ACTIVE)
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId("event-id-1") } returns Optional.of(existingSubscription)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "CANCELLATION", expirationAtMs = pastExpiry.toEpochMilli())
        )

        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.EXPIRED }) }
    }

    @Test
    fun `should set subscription to CANCELLED with autoRenew false on CANCELLATION`() {
        val user = createUser()
        val futureExpiry = Instant.now().plusSeconds(86400)
        val existingSubscription = createSubscription(user, status = SubscriptionStatus.ACTIVE)
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId("event-id-1") } returns Optional.of(existingSubscription)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "CANCELLATION", expirationAtMs = futureExpiry.toEpochMilli())
        )

        verify(exactly = 1) {
            subscriptionRepository.save(match {
                it.status == SubscriptionStatus.CANCELLED && !it.autoRenew
            })
        }
    }

    // ── UNCANCELLATION ─────────────────────────────────────────────────────────

    @Test
    fun `should set subscription to ACTIVE with autoRenew true on UNCANCELLATION`() {
        val user = createUser()
        val existingSubscription = createSubscription(user, status = SubscriptionStatus.CANCELLED)
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId("event-id-1") } returns Optional.of(existingSubscription)

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "UNCANCELLATION"))

        verify(exactly = 1) {
            subscriptionRepository.save(match {
                it.status == SubscriptionStatus.ACTIVE && it.autoRenew && it.cancelledAt == null
            })
        }
        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.ACTIVE }) }
    }

    // ── NON_RENEWING_PURCHASE ──────────────────────────────────────────────────

    @Test
    fun `should save subscription with autoRenew false on NON_RENEWING_PURCHASE`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "NON_RENEWING_PURCHASE", productId = "lifetime_access")
        )

        verify(exactly = 1) {
            subscriptionRepository.save(match {
                it.productId == "lifetime_access" && !it.autoRenew && it.status == SubscriptionStatus.ACTIVE
            })
        }
        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.ACTIVE }) }
    }

    @Test
    fun `should skip NON_RENEWING_PURCHASE processing when productId is null`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(
            createWebhookEvent(type = "NON_RENEWING_PURCHASE", productId = null)
        )

        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    // ── EXPIRATION ─────────────────────────────────────────────────────────────

    @Test
    fun `should set subscription and user to EXPIRED on EXPIRATION`() {
        val user = createUser()
        val existingSubscription = createSubscription(user, status = SubscriptionStatus.ACTIVE)
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId("event-id-1") } returns Optional.of(existingSubscription)

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "EXPIRATION"))

        verify(exactly = 1) {
            subscriptionRepository.save(match { it.status == SubscriptionStatus.EXPIRED })
        }
        verify(exactly = 1) { userRepository.save(match { it.subscriptionStatus == SubscriptionStatus.EXPIRED }) }
    }

    @Test
    fun `should track subscription_expired event on EXPIRATION`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)
        every { subscriptionRepository.findByRevenueCatSubscriptionId(any()) } returns Optional.empty()

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "EXPIRATION"))

        verify(exactly = 1) { eventService.trackAsync(1L, "subscription_expired", any()) }
    }

    // ── BILLING_ISSUE ──────────────────────────────────────────────────────────

    @Test
    fun `should not save subscription on BILLING_ISSUE`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "BILLING_ISSUE"))

        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    @Test
    fun `should not update user subscription status on BILLING_ISSUE`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "BILLING_ISSUE"))

        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ── SUBSCRIBER_ALIAS ───────────────────────────────────────────────────────

    @Test
    fun `should not save subscription on SUBSCRIBER_ALIAS`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "SUBSCRIBER_ALIAS"))

        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    // ── Unknown event type ─────────────────────────────────────────────────────

    @Test
    fun `should not throw on unknown event type`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        org.junit.jupiter.api.assertDoesNotThrow {
            subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "UNKNOWN_FUTURE_TYPE"))
        }
    }

    @Test
    fun `should not save anything on unknown event type`() {
        val user = createUser()
        every { userRepository.findByRevenueCatUserId("rc-user-1") } returns Optional.of(user)

        subscriptionService.handleRevenueCatWebhook(createWebhookEvent(type = "UNKNOWN_FUTURE_TYPE"))

        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ── getUserSubscriptions ───────────────────────────────────────────────────

    @Test
    fun `should return list of subscription dtos for valid user`() {
        val user = createUser()
        val subscription = createSubscription(user, id = 10L)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { subscriptionRepository.findByUser(user) } returns listOf(subscription)

        val result = subscriptionService.getUserSubscriptions(1L)

        assertEquals(1, result.size)
        assertEquals(10L, result[0].id)
        assertEquals("premium_monthly", result[0].productId)
        assertEquals(SubscriptionStatus.ACTIVE, result[0].status)
    }

    @Test
    fun `should return empty list when user has no subscriptions`() {
        val user = createUser()
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { subscriptionRepository.findByUser(user) } returns emptyList()

        val result = subscriptionService.getUserSubscriptions(1L)

        assertEquals(0, result.size)
    }

    @Test
    fun `should throw IllegalArgumentException when user not found in getUserSubscriptions`() {
        every { userRepository.findById(99L) } returns Optional.empty()

        assertThrows<IllegalArgumentException> {
            subscriptionService.getUserSubscriptions(99L)
        }
    }

    // ── getActiveSubscription ──────────────────────────────────────────────────

    @Test
    fun `should return active subscription dto when one exists`() {
        val user = createUser()
        val subscription = createSubscription(user, id = 20L, status = SubscriptionStatus.ACTIVE)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { subscriptionRepository.findActiveSubscription(user) } returns Optional.of(subscription)

        val result = subscriptionService.getActiveSubscription(1L)

        assertNotNull(result)
        assertEquals(20L, result!!.id)
        assertEquals(SubscriptionStatus.ACTIVE, result.status)
    }

    @Test
    fun `should return null when no active subscription exists`() {
        val user = createUser()
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { subscriptionRepository.findActiveSubscription(user) } returns Optional.empty()

        val result = subscriptionService.getActiveSubscription(1L)

        assertNull(result)
    }

    @Test
    fun `should throw IllegalArgumentException when user not found in getActiveSubscription`() {
        every { userRepository.findById(99L) } returns Optional.empty()

        assertThrows<IllegalArgumentException> {
            subscriptionService.getActiveSubscription(99L)
        }
    }

    // ── factory functions ──────────────────────────────────────────────────────

    private fun createUser(
        id: Long = 1L,
        email: String = "test@example.com",
        revenueCatUserId: String = "rc-user-1",
        subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    ): User = User(
        id = id,
        email = email,
        name = "Test User",
        revenueCatUserId = revenueCatUserId,
        subscriptionStatus = subscriptionStatus,
        currentStreak = 0,
        longestStreak = 0,
    )

    private fun createSubscription(
        user: User,
        id: Long = 1L,
        productId: String = "premium_monthly",
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        revenueCatSubscriptionId: String = "event-id-1",
    ): Subscription = Subscription(
        id = id,
        user = user,
        revenueCatSubscriptionId = revenueCatSubscriptionId,
        productId = productId,
        status = status,
        startedAt = Instant.now().minusSeconds(3600),
        expiresAt = Instant.now().plusSeconds(86400),
    )

    private fun createWebhookEvent(
        type: String,
        appUserId: String = "rc-user-1",
        eventId: String = "event-id-1",
        productId: String? = "premium_monthly",
        purchasedAtMs: Long? = System.currentTimeMillis(),
        expirationAtMs: Long? = null,
        isTrial: Boolean? = false,
        cancellationAtMs: Long? = null,
    ): RevenueCatWebhookEvent = RevenueCatWebhookEvent(
        event = RevenueCatEvent(
            id = eventId,
            type = type,
            app_user_id = appUserId,
            aliases = null,
        ),
        api_version = "1.0",
        app_user_id = appUserId,
        product_id = productId,
        purchased_at_ms = purchasedAtMs,
        expiration_at_ms = expirationAtMs,
        is_trial_conversion = isTrial,
        cancellation_at_ms = cancellationAtMs,
        auto_resume_at_ms = null,
    )
}
