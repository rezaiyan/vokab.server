package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.DailyActivityRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@Service
class StreakReminderService(
    private val userRepository: UserRepository,
    private val dailyActivityRepository: DailyActivityRepository,
    private val pushNotificationService: PushNotificationService,
    private val openRouterService: OpenRouterService,
    private val userProgressService: UserProgressService
) {
    
    @Transactional(readOnly = true)
    fun findUsersNeedingReminder(): List<User> {
        val today = LocalDate.now()
        val usersWithActiveStreaks = userRepository.findByCurrentStreakGreaterThanAndActiveTrue(0)
        
        logger.info { "Found ${usersWithActiveStreaks.size} users with active streaks" }
        
        val usersNeedingReminder = usersWithActiveStreaks.filter { user ->
            val hasActivityToday = dailyActivityRepository.findByUserAndActivityDate(user, today).isPresent
            !hasActivityToday
        }
        
        logger.info { "Found ${usersNeedingReminder.size} users needing reminder (no activity today)" }
        
        return usersNeedingReminder
    }
    
    @Transactional
    fun sendReminderNotifications() {
        logger.info { "Starting streak reminder notification job" }
        
        val usersNeedingReminder = findUsersNeedingReminder()
        
        if (usersNeedingReminder.isEmpty()) {
            logger.info { "No users need reminders today" }
            return
        }
        
        var successCount = 0
        var failureCount = 0
        
        usersNeedingReminder.forEach { user ->
            try {
                val progressStats = userProgressService.calculateProgressStats(user)
                
                val personalizedMessage = openRouterService.generateStreakReminderMessage(
                    currentStreak = user.currentStreak,
                    userName = user.name,
                    progressStats = progressStats
                ).block() ?: "You have a ${user.currentStreak}-day streak! 🔥 Complete your review today to keep it going!"
                
                val title = "Don't lose your streak!"
                val body = personalizedMessage
                
                val data = mapOf(
                    "type" to "streak_reminder",
                    "currentStreak" to user.currentStreak.toString()
                )
                
                val results = pushNotificationService.sendNotificationToUser(
                    userId = user.id!!,
                    title = title,
                    body = body,
                    data = data
                )
                
                val successful = results.any { it.success }
                if (successful) {
                    successCount++
                    logger.debug { "Reminder sent successfully to user ${user.email} (streak: ${user.currentStreak})" }
                } else {
                    failureCount++
                    logger.warn { "Failed to send reminder to user ${user.email}" }
                }
            } catch (e: Exception) {
                failureCount++
                logger.error(e) { "Error sending reminder to user ${user.email}" }
                
                try {
                    val fallbackTitle = "Don't lose your streak!"
                    val fallbackBody = "You have a ${user.currentStreak}-day streak. Complete your review today to keep it going!"
                    
                    val data = mapOf(
                        "type" to "streak_reminder",
                        "currentStreak" to user.currentStreak.toString()
                    )
                    
                    pushNotificationService.sendNotificationToUser(
                        userId = user.id!!,
                        title = fallbackTitle,
                        body = fallbackBody,
                        data = data
                    )
                    logger.info { "Sent fallback reminder to user ${user.email}" }
                } catch (fallbackError: Exception) {
                    logger.error(fallbackError) { "Failed to send fallback reminder to user ${user.email}" }
                }
            }
        }
        
        logger.info { "Streak reminder job completed. Success: $successCount, Failures: $failureCount" }
    }
}

