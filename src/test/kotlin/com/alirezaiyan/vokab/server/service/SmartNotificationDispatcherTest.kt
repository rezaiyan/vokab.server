package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.NotificationSchedule
import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.NotificationScheduleRepository
import com.alirezaiyan.vokab.server.presentation.dto.NotificationResponse
import com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto
import com.alirezaiyan.vokab.server.service.NotificationTypeSelector.NotificationType
import com.alirezaiyan.vokab.server.service.push.PushNotificationService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SmartNotificationDispatcherTest {

    private lateinit var notificationScheduleRepository: NotificationScheduleRepository
    private lateinit var notificationTypeSelector: NotificationTypeSelector
    private lateinit var notificationContentBuilder: NotificationContentBuilder
    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var milestoneDetector: MilestoneDetector
    private lateinit var userProgressService: UserProgressService
    private lateinit var notificationEngagementService: NotificationEngagementService
    private val objectMapper: ObjectMapper = ObjectMapper()

    private lateinit var dispatcher: SmartNotificationDispatcher

    @BeforeEach
    fun setUp() {
        notificationScheduleRepository = mockk()
        notificationTypeSelector = mockk()
        notificationContentBuilder = mockk()
        pushNotificationService = mockk()
        milestoneDetector = mockk()
        userProgressService = mockk()
        notificationEngagementService = mockk()

        dispatcher = SmartNotificationDispatcher(
            notificationScheduleRepository,
            notificationTypeSelector,
            notificationContentBuilder,
            pushNotificationService,
            milestoneDetector,
            userProgressService,
            notificationEngagementService,
            objectMapper
        )
    }

    // ── dispatchForCurrentHour: basic orchestration ───────────────────────────

    @Test
    fun `should send notification when type is selected and push succeeds`() {
        // Arrange
        val user = createUser(id = 1L)
        val schedule = createSchedule(user)
        val payload = createPayload(type = NotificationType.DUE_CARDS)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns listOf(schedule)
        every { notificationTypeSelector.selectType(user, schedule) } returns NotificationType.DUE_CARDS
        every { notificationContentBuilder.build(user, NotificationType.DUE_CARDS) } returns payload
        every {
            pushNotificationService.sendNotificationToUser(
                userId = 1L,
                title = payload.title,
                body = payload.body,
                data = payload.data
            )
        } returns listOf(NotificationResponse(success = true))
        justRun {
            notificationEngagementService.recordSendAndPersistLog(
                schedule = schedule,
                notificationType = NotificationType.DUE_CARDS.name,
                title = payload.title,
                body = payload.body,
                dataPayload = any()
            )
        }

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 1) {
            pushNotificationService.sendNotificationToUser(
                userId = 1L,
                title = payload.title,
                body = payload.body,
                data = payload.data
            )
        }
        verify(exactly = 1) {
            notificationEngagementService.recordSendAndPersistLog(
                schedule = schedule,
                notificationType = NotificationType.DUE_CARDS.name,
                title = payload.title,
                body = payload.body,
                dataPayload = any()
            )
        }
    }

    @Test
    fun `should skip sending notification when type selector returns NONE`() {
        // Arrange
        val user = createUser(id = 2L)
        val schedule = createSchedule(user)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns listOf(schedule)
        every { notificationTypeSelector.selectType(user, schedule) } returns NotificationType.NONE

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 0) { notificationContentBuilder.build(any(), any()) }
        verify(exactly = 0) { pushNotificationService.sendNotificationToUser(any(), any(), any(), any()) }
    }

    @Test
    fun `should not call recordSendAndPersistLog when push delivery returns all failures`() {
        // Arrange
        val user = createUser(id = 3L)
        val schedule = createSchedule(user)
        val payload = createPayload(type = NotificationType.DUE_CARDS)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns listOf(schedule)
        every { notificationTypeSelector.selectType(user, schedule) } returns NotificationType.DUE_CARDS
        every { notificationContentBuilder.build(user, NotificationType.DUE_CARDS) } returns payload
        every {
            pushNotificationService.sendNotificationToUser(
                userId = 3L,
                title = any(),
                body = any(),
                data = any()
            )
        } returns listOf(NotificationResponse(success = false, error = "NOT_REGISTERED"))

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 0) { notificationEngagementService.recordSendAndPersistLog(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should not call recordSendAndPersistLog when push delivery returns empty list`() {
        // Arrange
        val user = createUser(id = 4L)
        val schedule = createSchedule(user)
        val payload = createPayload(type = NotificationType.STREAK_RISK)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns listOf(schedule)
        every { notificationTypeSelector.selectType(user, schedule) } returns NotificationType.STREAK_RISK
        every { notificationContentBuilder.build(user, NotificationType.STREAK_RISK) } returns payload
        every {
            pushNotificationService.sendNotificationToUser(
                userId = 4L,
                title = any(),
                body = any(),
                data = any()
            )
        } returns emptyList()

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 0) { notificationEngagementService.recordSendAndPersistLog(any(), any(), any(), any(), any()) }
    }

    // ── PROGRESS_MILESTONE triggers milestone snapshot ────────────────────────

    @Test
    fun `should record milestone snapshot when PROGRESS_MILESTONE notification is sent successfully`() {
        // Arrange
        val user = createUser(id = 5L)
        val schedule = createSchedule(user)
        val payload = createPayload(type = NotificationType.PROGRESS_MILESTONE)
        val stats = createProgressStats(totalWords = 100)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns listOf(schedule)
        every { notificationTypeSelector.selectType(user, schedule) } returns NotificationType.PROGRESS_MILESTONE
        every { notificationContentBuilder.build(user, NotificationType.PROGRESS_MILESTONE) } returns payload
        every {
            pushNotificationService.sendNotificationToUser(
                userId = 5L,
                title = any(),
                body = any(),
                data = any()
            )
        } returns listOf(NotificationResponse(success = true))
        justRun {
            notificationEngagementService.recordSendAndPersistLog(any(), any(), any(), any(), any())
        }
        every { userProgressService.calculateProgressStats(user) } returns stats
        justRun { milestoneDetector.recordMilestoneSnapshot(user, stats) }

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 1) { milestoneDetector.recordMilestoneSnapshot(user, stats) }
    }

    @Test
    fun `should not record milestone snapshot when notification type is not PROGRESS_MILESTONE`() {
        // Arrange
        val user = createUser(id = 6L)
        val schedule = createSchedule(user)
        val payload = createPayload(type = NotificationType.DUE_CARDS)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns listOf(schedule)
        every { notificationTypeSelector.selectType(user, schedule) } returns NotificationType.DUE_CARDS
        every { notificationContentBuilder.build(user, NotificationType.DUE_CARDS) } returns payload
        every {
            pushNotificationService.sendNotificationToUser(
                userId = 6L,
                title = any(),
                body = any(),
                data = any()
            )
        } returns listOf(NotificationResponse(success = true))
        justRun {
            notificationEngagementService.recordSendAndPersistLog(any(), any(), any(), any(), any())
        }

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 0) { milestoneDetector.recordMilestoneSnapshot(any(), any()) }
        verify(exactly = 0) { userProgressService.calculateProgressStats(any()) }
    }

    // ── Error isolation ───────────────────────────────────────────────────────

    @Test
    fun `should continue dispatching remaining schedules when one user dispatch fails`() {
        // Arrange
        val user1 = createUser(id = 7L, email = "user1@test.com")
        val user2 = createUser(id = 8L, email = "user2@test.com")
        val schedule1 = createSchedule(user1, id = 1L)
        val schedule2 = createSchedule(user2, id = 2L)
        val payload = createPayload(type = NotificationType.DUE_CARDS)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns listOf(schedule1, schedule2)

        // First user throws, second user succeeds
        every { notificationTypeSelector.selectType(user1, schedule1) } throws RuntimeException("Selector crashed")
        every { notificationTypeSelector.selectType(user2, schedule2) } returns NotificationType.DUE_CARDS
        every { notificationContentBuilder.build(user2, NotificationType.DUE_CARDS) } returns payload
        every {
            pushNotificationService.sendNotificationToUser(
                userId = 8L,
                title = any(),
                body = any(),
                data = any()
            )
        } returns listOf(NotificationResponse(success = true))
        justRun {
            notificationEngagementService.recordSendAndPersistLog(any(), any(), any(), any(), any())
        }

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert — second user was still dispatched despite first user failing
        verify(exactly = 1) {
            pushNotificationService.sendNotificationToUser(
                userId = 8L,
                title = any(),
                body = any(),
                data = any()
            )
        }
    }

    @Test
    fun `should process nothing when no schedules are found for the current hour`() {
        // Arrange
        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns emptyList()

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 0) { notificationTypeSelector.selectType(any(), any()) }
        verify(exactly = 0) { pushNotificationService.sendNotificationToUser(any(), any(), any(), any()) }
    }

    @Test
    fun `should dispatch to all users when multiple schedules exist`() {
        // Arrange
        val users = (10L..12L).map { id -> createUser(id = id, email = "user$id@test.com") }
        val schedules = users.mapIndexed { i, user -> createSchedule(user, id = i.toLong() + 10) }
        val payload = createPayload(type = NotificationType.DAILY_INSIGHT)

        every { notificationScheduleRepository.findUsersToNotifyAtHour(any()) } returns schedules
        schedules.forEachIndexed { i, schedule ->
            every { notificationTypeSelector.selectType(users[i], schedule) } returns NotificationType.DAILY_INSIGHT
            every { notificationContentBuilder.build(users[i], NotificationType.DAILY_INSIGHT) } returns payload
            every {
                pushNotificationService.sendNotificationToUser(
                    userId = users[i].id!!,
                    title = any(),
                    body = any(),
                    data = any()
                )
            } returns listOf(NotificationResponse(success = true))
            justRun {
                notificationEngagementService.recordSendAndPersistLog(
                    schedule = schedule,
                    notificationType = any(),
                    title = any(),
                    body = any(),
                    dataPayload = any()
                )
            }
        }

        // Act
        dispatcher.dispatchForCurrentHour()

        // Assert
        verify(exactly = 3) { pushNotificationService.sendNotificationToUser(any(), any(), any(), any()) }
        verify(exactly = 3) { notificationEngagementService.recordSendAndPersistLog(any(), any(), any(), any(), any()) }
    }

    // ── Factory functions ──────────────────────────────────────────────────────

    private fun createUser(
        id: Long = 1L,
        email: String = "test@example.com",
        name: String = "Test User",
        currentStreak: Int = 3
    ): User = User(
        id = id,
        email = email,
        name = name,
        subscriptionStatus = SubscriptionStatus.FREE,
        currentStreak = currentStreak,
        longestStreak = currentStreak,
        active = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun createSchedule(
        user: User,
        id: Long = 1L,
        consecutiveIgnores: Int = 0
    ): NotificationSchedule = NotificationSchedule(
        id = id,
        user = user,
        optimalSendHour = 18,
        consecutiveIgnores = consecutiveIgnores
    )

    private fun createPayload(
        title: String = "Test Title",
        body: String = "Test Body",
        type: NotificationType = NotificationType.DUE_CARDS,
        data: Map<String, String> = mapOf("type" to type.name.lowercase(), "deep_link" to "vokab://review")
    ): NotificationPayload = NotificationPayload(
        title = title,
        body = body,
        data = data,
        type = type
    )

    private fun createProgressStats(
        totalWords: Int = 10,
        dueCards: Int = 3,
        level6Count: Int = 1
    ): ProgressStatsDto = ProgressStatsDto(
        totalWords = totalWords,
        dueCards = dueCards,
        level0Count = 0,
        level1Count = 2,
        level2Count = 2,
        level3Count = 2,
        level4Count = 2,
        level5Count = 1,
        level6Count = level6Count
    )
}
