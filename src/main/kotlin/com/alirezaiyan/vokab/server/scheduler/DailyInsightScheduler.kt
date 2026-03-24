package com.alirezaiyan.vokab.server.scheduler

import com.alirezaiyan.vokab.server.service.SmartNotificationDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class DailyInsightScheduler(
    private val smartNotificationDispatcher: SmartNotificationDispatcher
) {
    // Daily insight delivery is now routed through SmartNotificationDispatcher,
    // which sends to each user at their computed optimal UTC hour.
    // The window-based 30-min job has been replaced by ScheduledTasks.dispatchSmartNotifications().
    fun generateDailyInsights() {
        logger.info { "generateDailyInsights delegating to SmartNotificationDispatcher" }
        smartNotificationDispatcher.dispatchForCurrentHour()
    }
}
