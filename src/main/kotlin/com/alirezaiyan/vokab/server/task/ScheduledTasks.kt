package com.alirezaiyan.vokab.server.task

import com.alirezaiyan.vokab.server.domain.repository.RefreshTokenRepository
import com.alirezaiyan.vokab.server.service.DailyInsightService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
class ScheduledTasks(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val dailyInsightService: DailyInsightService
) {
    
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM every day
    @Transactional
    fun cleanupExpiredTokens() {
        logger.info { "Starting cleanup of expired refresh tokens" }
        val deleted = refreshTokenRepository.deleteExpiredTokens()
        logger.info { "Cleaned up $deleted expired refresh tokens" }
    }
    
    @Scheduled(cron = "0 0 9 * * *") // Run at 9 AM every day
    @Transactional
    fun generateDailyInsights() {
        logger.info { "Starting daily insight generation for all users" }
        try {
            val generatedCount = dailyInsightService.generateInsightsForAllUsers()
            logger.info { "Daily insight generation completed: $generatedCount insights generated and sent" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate daily insights" }
        }
    }
}

