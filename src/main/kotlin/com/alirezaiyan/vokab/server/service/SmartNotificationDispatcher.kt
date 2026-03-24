package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.NotificationSchedule
import com.alirezaiyan.vokab.server.domain.repository.NotificationScheduleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@Service
class SmartNotificationDispatcher(
    private val notificationScheduleRepository: NotificationScheduleRepository,
    private val dailyInsightService: DailyInsightService,          // temporary — Phase 3 replaces
    private val streakReminderService: StreakReminderService        // temporary — Phase 3 replaces
) {
    fun dispatchForCurrentHour() {
        val hour = LocalTime.now(ZoneOffset.UTC).hour
        val schedules = notificationScheduleRepository.findUsersToNotifyAtHour(hour)

        logger.info { "Dispatching notifications for hour=$hour, users=${schedules.size}" }

        for (schedule in schedules) {
            runCatching {
                dispatchForUser(schedule)
            }.onFailure { logger.error(it) { "Dispatch failed for user=${schedule.user.id}" } }
        }
    }

    private fun dispatchForUser(schedule: NotificationSchedule) {
        val user = schedule.user
        // Phase 2: delegate to existing logic
        // Phase 3: replace with NotificationTypeSelector
        if (user.currentStreak > 0) {
            streakReminderService.sendReminderForUser(user)
        } else {
            dailyInsightService.generateAndSendForUser(user)
        }
        schedule.lastSentDate = LocalDate.now(ZoneOffset.UTC)
        schedule.updatedAt = Instant.now()
        notificationScheduleRepository.save(schedule)
    }
}
