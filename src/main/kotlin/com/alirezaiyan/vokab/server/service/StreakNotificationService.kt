package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.NotificationCategory
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.service.push.PushNotificationService
import com.alirezaiyan.vokab.server.domain.repository.DailyActivityRepository
import com.alirezaiyan.vokab.server.domain.repository.DailyInsightRepository
import com.alirezaiyan.vokab.server.domain.repository.NotificationLogRepository
import com.alirezaiyan.vokab.server.domain.repository.PushTokenRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@Service
class StreakNotificationService(
    private val userRepository: UserRepository,
    private val dailyActivityRepository: DailyActivityRepository,
    private val dailyInsightRepository: DailyInsightRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val streakService: StreakService,
    private val openRouterService: OpenRouterService,
    private val pushNotificationService: PushNotificationService,
    private val userProgressService: UserProgressService,
    private val notificationLogRepository: NotificationLogRepository
) {

    /**
     * Check if a streak number is a milestone based on pattern:
     * Early milestones: 1, 3, 5
     * For power 10: multipliers 1, 2, 5 (10, 20, 50)
     * For powers >= 100: multipliers 1, 2, 3, 5 (100, 200, 300, 500, 1000, 2000, 3000, 5000, ...)
     * Pattern: 1, 3, 5, 10, 20, 50, 100, 200, 300, 500, 1000, 2000, 3000, 5000, ...
     */
    fun isMilestoneStreak(streak: Int): Boolean {
        if (streak <= 0) return false

        // Early milestones
        if (streak in setOf(1, 3, 5)) return true

        // Check for power-of-10 milestones
        var power = 10
        while (power <= streak * 5) {
            // For power 10, use multipliers 1, 2, 5 (skip 3)
            // For powers >= 100, use multipliers 1, 2, 3, 5
            val multipliers = if (power == 10) setOf(1, 2, 5) else setOf(1, 2, 3, 5)

            for (multiplier in multipliers) {
                val milestone = power * multiplier
                if (streak == milestone) return true
            }

            // Move to next power of 10
            power *= 10
        }

        return false
    }

    /**
     * Get users who need streak reset warnings.
     * Users with active milestone streaks who haven't logged in today (UTC date),
     * have active push tokens, and haven't already been notified today.
     */
    @Transactional(readOnly = true)
    fun getUsersNeedingNotifications(): List<User> {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val startOfToday = today.atStartOfDay().toInstant(ZoneOffset.UTC)

        val allUsers = userRepository.findByCurrentStreakGreaterThanAndActiveTrue(0)

        return allUsers.filter { user ->
            val hasTodayActivity = dailyActivityRepository.existsByUserAndActivityDate(user, today)
            val hasPushTokens = pushTokenRepository.findByUserAndActiveTrue(user).isNotEmpty()
            val alreadyReceivedInsightToday =
                dailyInsightRepository.existsByUserAndDateAndSentViaPushTrue(user, todayStr)
            // Skip if smart dispatcher already sent a STREAK_RISK notification today
            val alreadyReceivedStreakRiskToday = user.id?.let {
                notificationLogRepository.existsByUserIdAndNotificationTypeAndSentAtAfter(
                    it, "STREAK_RISK", startOfToday
                )
            } ?: false

            !hasTodayActivity && isMilestoneStreak(user.currentStreak) && hasPushTokens
                && !alreadyReceivedInsightToday && !alreadyReceivedStreakRiskToday
        }
    }

    /**
     * Send streak reset warning notification to a user.
     * Validates Firebase push tokens are available before sending.
     */
    fun sendStreakResetWarning(user: User): Boolean {
        logger.info { "Sending streak reset warning to user=${user.id}, streak=${user.currentStreak}" }

        return try {
            val pushTokens = pushTokenRepository.findByUserAndActiveTrue(user)
            if (pushTokens.isEmpty()) {
                logger.debug { "User=${user.id} has no active push tokens, skipping notification" }
                return false
            }

            logger.debug { "User=${user.id} has ${pushTokens.size} active push token(s)" }

            val streakInfo = streakService.getUserStreak(user.id!!)
            val currentStreak = streakInfo.currentStreak

            if (!isMilestoneStreak(currentStreak)) {
                logger.debug { "User=${user.id} streak ($currentStreak) is no longer a milestone, skipping" }
                return false
            }

            val progressStats = userProgressService.calculateProgressStats(user)
            val message = openRouterService.generateStreakResetWarning(
                currentStreak = currentStreak,
                progressStats = progressStats,
                userName = user.name
            ).block() ?: getDefaultStreakMessage(currentStreak)

            val responses = pushNotificationService.sendNotificationToUser(
                userId = user.id,
                title = "🔥 Don't Lose Your Streak!",
                body = message,
                data = mapOf(
                    "type" to "streak_warning",
                    "current_streak" to currentStreak.toString()
                ),
                category = NotificationCategory.USER
            )

            if (responses.isEmpty()) {
                logger.warn { "No notification responses received for user=${user.id} — tokens may have been deactivated" }
                return false
            }

            val success = responses.any { it.success }

            if (success) {
                logger.info { "Sent streak reset warning to user=${user.id} (streak=$currentStreak, ${responses.count { it.success }}/${responses.size} tokens)" }
            } else {
                logger.warn { "Failed to send streak reset warning to user=${user.id} — all ${responses.size} token(s) failed" }
            }

            success
        } catch (e: Exception) {
            logger.error(e) { "Error sending streak reset warning to user=${user.id}" }
            false
        }
    }

    /**
     * Process all users who need notifications.
     */
    fun processStreakResetNotifications(): Int {
        logger.info { "Processing streak reset notifications" }

        val usersNeedingNotifications = getUsersNeedingNotifications()
        logger.info { "Found ${usersNeedingNotifications.size} users needing streak reset notifications" }

        var sentCount = 0

        for (user in usersNeedingNotifications) {
            try {
                if (sendStreakResetWarning(user)) {
                    sentCount++
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to process notification for user=${user.id}" }
            }
        }

        logger.info { "Sent $sentCount streak reset notifications" }
        return sentCount
    }

    /**
     * Default message fallback if AI generation fails.
     */
    private fun getDefaultStreakMessage(streak: Int): String {
        return when {
            streak >= 100 -> "You're on a $streak-day streak! 🏆 Keep it going - log in today!"
            streak >= 50 -> "Amazing $streak-day streak! 💪 Don't let it slip away - review now!"
            streak >= 20 -> "Wow! $streak days strong! 🔥 Your streak needs you today!"
            streak >= 10 -> "$streak days of dedication! 🌟 Log in to keep your streak alive!"
            streak >= 5 -> "You've built a $streak-day streak! 💫 Keep it going today!"
            streak >= 3 -> "Nice $streak-day streak! ✨ Don't miss today!"
            else -> "Your $streak-day streak is waiting! 🎯 Log in now to keep it going!"
        }
    }
}
