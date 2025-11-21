package com.alirezaiyan.vokab.server.scheduler

import com.alirezaiyan.vokab.server.service.StreakReminderService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class StreakReminderScheduler(
    private val streakReminderService: StreakReminderService
) {
    
    @Scheduled(cron = "0 0 22 * * *")
    fun sendStreakReminders() {
        logger.info { "Starting scheduled streak reminder job at 22:00" }
        
        try {
            streakReminderService.sendReminderNotifications()
            logger.info { "Scheduled streak reminder job completed successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Error in scheduled streak reminder job" }
        }
    }
}