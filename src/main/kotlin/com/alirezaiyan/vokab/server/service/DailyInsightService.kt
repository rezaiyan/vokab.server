package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.DailyInsight
import com.alirezaiyan.vokab.server.domain.entity.NotificationCategory
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.DailyActivityRepository
import com.alirezaiyan.vokab.server.domain.repository.DailyInsightRepository
import com.alirezaiyan.vokab.server.domain.repository.NotificationScheduleRepository
import com.alirezaiyan.vokab.server.domain.repository.UserSettingsRepository
import com.alirezaiyan.vokab.server.service.push.PushNotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@Service
class DailyInsightService(
    private val dailyInsightRepository: DailyInsightRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val dailyActivityRepository: DailyActivityRepository,
    private val openRouterService: OpenRouterService,
    private val userProgressService: UserProgressService,
    private val pushNotificationService: PushNotificationService,
    private val featureAccessService: FeatureAccessService,
    private val analyticsService: AnalyticsService,
    private val notificationScheduleRepository: NotificationScheduleRepository
) {

    /**
     * Generate daily insight for a specific user.
     *
     * Logic:
     * 1. Gate on premium access.
     * 2. Return existing insight if already generated today (idempotent).
     * 3. Frequency cap: if the user has an active streak but hasn't reviewed yet,
     *    the 22:00 streak reminder will fire — skip the morning insight to avoid
     *    double-notifying, UNLESS the user's reminder time is ≥ 20:00 (in which
     *    case this insight IS their evening notification).
     * 4. If the user already reviewed today → send a celebration insight.
     *    Otherwise → send a motivational insight.
     */
    @Transactional
    fun generateDailyInsightForUser(user: User): DailyInsight? {
        logger.info { "Generating daily insight for user ${user.id}" }

        if (!featureAccessService.hasActivePremiumAccess(user)) {
            logger.debug { "User ${user.id} lacks premium access, skipping insight" }
            return null
        }

        val today = LocalDate.now().toString()

        val existingInsight = dailyInsightRepository.findByUserAndDate(user, today)
        if (existingInsight != null) {
            logger.debug { "Insight already exists for user ${user.id} on $today" }
            return existingInsight
        }

        val hasActivityToday = dailyActivityRepository.existsByUserAndActivityDate(user, LocalDate.now())
        val streakAtRisk = user.currentStreak > 0 && !hasActivityToday
        val reminderHour = userSettingsRepository.findByUser(user)
            ?.dailyReminderTime?.split(":")?.firstOrNull()?.toIntOrNull() ?: 18

        // Frequency cap: streak reminder fires at 22:00 — don't also send a morning insight
        if (streakAtRisk && reminderHour < 20) {
            logger.debug { "Skipping insight for user ${user.id} — streak reminder will fire tonight" }
            return null
        }

        return try {
            val stats = userProgressService.calculateProgressStats(user)
            val insightText = if (hasActivityToday) {
                openRouterService.generateCelebrationInsight(stats, user.name).block()
                    ?: "Great work today! 🎉 You're building something real."
            } else {
                val ctx = buildInsightContext(user, stats)
                openRouterService.generateDailyInsight(ctx).block()
                    ?: "Keep grinding! Every word you learn levels you up! 🎮✨"
            }

            val insight = DailyInsight(
                user = user,
                insightText = insightText,
                generatedAt = Instant.now(),
                date = today,
                sentViaPush = false
            )
            val saved = dailyInsightRepository.save(insight)
            logger.info { "Generated ${if (hasActivityToday) "celebration" else "motivational"} insight for user ${user.id}" }
            saved
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate daily insight for user ${user.id}" }
            null
        }
    }

    private fun buildInsightContext(user: User, stats: com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto): OpenRouterService.DailyInsightContext {
        val optimalStudyHour = runCatching {
            notificationScheduleRepository.findByUser(user)?.optimalSendHour
        }.getOrNull()

        val weeklyReport = runCatching { analyticsService.getWeeklyReport(user) }.getOrNull()
        val accuracyTrend = weeklyReport?.changePercent?.toFloat()

        val topDifficultWord = runCatching {
            analyticsService.getDifficultWords(user, minReviews = 3, limit = 1).firstOrNull()?.wordText
        }.getOrNull()

        val primaryLanguage = runCatching {
            analyticsService.getStatsByLanguagePair(user).firstOrNull()?.targetLanguage
        }.getOrNull()

        val sessionCompletionRate = runCatching {
            analyticsService.getStudyInsights(user).sessionCompletionRate?.toFloat()
        }.getOrNull()

        return OpenRouterService.DailyInsightContext(
            stats = stats,
            userName = user.name,
            optimalStudyHour = optimalStudyHour,
            accuracyTrend = accuracyTrend,
            topDifficultWord = topDifficultWord,
            primaryLanguage = primaryLanguage,
            sessionCompletionRate = sessionCompletionRate,
            currentStreak = user.currentStreak
        )
    }

    /**
     * Send daily insight via push notification.
     */
    @Transactional
    fun sendDailyInsightPush(insight: DailyInsight): Boolean {
        logger.info { "Sending daily insight push for user ${insight.user.id}" }

        return try {
            val responses = pushNotificationService.sendNotificationToUser(
                userId = insight.user.id!!,
                title = "💡 Daily Vocabulary Insight",
                body = insight.insightText,
                data = mapOf(
                    "type" to "daily_insight",
                    "insight_id" to insight.id.toString(),
                    "date" to insight.date
                ),
                category = NotificationCategory.USER
            )

            val success = responses.any { it.success }

            if (success) {
                val updatedInsight = insight.copy(sentViaPush = true, pushSentAt = Instant.now())
                dailyInsightRepository.save(updatedInsight)
                logger.info { "Successfully sent daily insight push for user ${insight.user.id}" }
            } else {
                logger.warn { "Failed to send daily insight push for user ${insight.user.id}" }
            }

            success
        } catch (e: Exception) {
            logger.error(e) { "Error sending daily insight push for user ${insight.user.id}" }
            false
        }
    }

    /**
     * Resolve users whose dailyReminderTime falls in the 30-minute window
     * [minuteWindowStart, minuteWindowStart+30) for the given hour.
     */
    @Transactional(readOnly = true)
    fun getUsersInReminderWindow(hour: Int, minuteWindowStart: Int): List<User> {
        val windowEnd = (minuteWindowStart + 30) % 60
        return userSettingsRepository.findAllWithNotificationsEnabled()
            .filter { settings ->
                val parts = settings.dailyReminderTime.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: return@filter false
                val m = parts.getOrNull(1)?.toIntOrNull() ?: return@filter false
                // windowEnd == 0 means the window wraps to the next hour (e.g., xx:30–xx:59 → next :00)
                h == hour && m >= minuteWindowStart && (windowEnd == 0 || m < windowEnd)
            }
            .mapNotNull { it.user }
            .filter { featureAccessService.hasActivePremiumAccess(it) }
    }

    /**
     * Generate and push insights for all users whose reminder window opens in
     * the current 30-minute slot. Called by the scheduler every 30 minutes.
     *
     * Returns the number of push notifications successfully sent.
     */
    @Transactional
    fun generateInsightsForUsersInWindow(hour: Int, minuteWindowStart: Int): Int {
        val users = getUsersInReminderWindow(hour, minuteWindowStart)
        logger.info {
            val windowLabel = "$hour:${minuteWindowStart.toString().padStart(2, '0')}"
            "Processing ${users.size} users in reminder window $windowLabel UTC"
        }

        var sentCount = 0
        for (user in users) {
            runCatching {
                val insight = generateDailyInsightForUser(user) ?: return@runCatching
                if (!insight.sentViaPush && sendDailyInsightPush(insight)) {
                    sentCount++
                }
            }.onFailure { logger.error(it) { "Failed to process insight for user ${user.id}" } }
        }

        logger.info { "Window job complete — $sentCount push(es) sent" }
        return sentCount
    }

    /**
     * Generate and push a daily insight for a single user. Used by SmartNotificationDispatcher.
     */
    @Transactional
    fun generateAndSendForUser(user: User) {
        val insight = generateDailyInsightForUser(user) ?: return
        if (!insight.sentViaPush) {
            sendDailyInsightPush(insight)
        }
    }

    /**
     * Get today's insight for a user (for fallback when push notification is missed).
     */
    fun getTodaysInsightForUser(user: User): DailyInsight? {
        val today = LocalDate.now().toString()
        return dailyInsightRepository.findByUserAndDate(user, today)
    }

    /**
     * Get latest insight for a user.
     */
    fun getLatestInsightForUser(user: User): DailyInsight? {
        return dailyInsightRepository.findLatestInsightByUser(user)
    }
}
