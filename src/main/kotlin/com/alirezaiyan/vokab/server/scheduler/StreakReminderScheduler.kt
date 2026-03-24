package com.alirezaiyan.vokab.server.scheduler

import com.alirezaiyan.vokab.server.service.SmartNotificationDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class StreakReminderScheduler(
    private val smartNotificationDispatcher: SmartNotificationDispatcher
) {
    // Streak reminders are now dispatched via SmartNotificationDispatcher
    // based on each user's optimal send hour (computed from review behavior).
    // The fixed 22:00 UTC job has been replaced by ScheduledTasks.dispatchSmartNotifications().
    fun sendStreakReminders() {
        logger.info { "sendStreakReminders delegating to SmartNotificationDispatcher" }
        smartNotificationDispatcher.dispatchForCurrentHour()
    }
}
