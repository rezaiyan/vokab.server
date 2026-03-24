package com.alirezaiyan.vokab.server.scheduler

import com.alirezaiyan.vokab.server.service.DailyInsightService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@Component
class DailyInsightScheduler(
    private val dailyInsightService: DailyInsightService
) {

    /**
     * Fires every 30 minutes. On each tick, fetches users whose dailyReminderTime
     * falls within the current 30-minute window (UTC) and sends insights only to them.
     *
     * This replaces the old fixed 9 AM batch, honouring each user's chosen reminder time.
     */
    @Scheduled(cron = "0 0/30 * * * *")
    fun generateDailyInsights() {
        val now = LocalTime.now(ZoneOffset.UTC)
        val hour = now.hour
        val minuteWindowStart = now.minute

        logger.info { "Daily insight scheduler tick — window ${hour}:${minuteWindowStart.toString().padStart(2, '0')} UTC" }

        try {
            val sent = dailyInsightService.generateInsightsForUsersInWindow(hour, minuteWindowStart)
            logger.info { "Daily insight scheduler complete — $sent push(es) sent" }
        } catch (e: Exception) {
            logger.error(e) { "Error in daily insight scheduler" }
        }
    }
}
