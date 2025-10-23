package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.DailyInsight
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.DailyInsightRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Service
class DailyInsightService(
    private val dailyInsightRepository: DailyInsightRepository,
    private val userRepository: UserRepository,
    private val openRouterService: OpenRouterService,
    private val userProgressService: UserProgressService,
    private val pushNotificationService: PushNotificationService,
    private val featureAccessService: FeatureAccessService
) {
    
    /**
     * Generate daily insight for a specific user
     */
    @Transactional
    fun generateDailyInsightForUser(user: User): DailyInsight? {
        logger.info { "Generating daily insight for user: ${user.email}" }
        
        // Check if user has premium access for AI insights
        if (!featureAccessService.canUseAiDailyInsight(user)) {
            logger.info { "User ${user.email} doesn't have premium access for AI insights" }
            return null
        }
        
        // Check if user has vocabulary data
        val progressStats = userProgressService.calculateProgressStats(user)
        if (progressStats.totalWords == 0) {
            logger.info { "User ${user.email} has no vocabulary data, skipping insight generation" }
            return null
        }
        
        val today = LocalDate.now().toString()
        
        // Check if insight already exists for today
        val existingInsight = dailyInsightRepository.findByUserAndDate(user, today)
        if (existingInsight != null) {
            logger.info { "Daily insight already exists for user ${user.email} on $today" }
            return existingInsight
        }
        
        return try {
            // Generate AI insight
            val insightText = openRouterService.generateDailyInsight(progressStats).block()
                ?: "Keep grinding! Every word you learn levels you up! 🎮✨"
            
            // Create and save insight
            val dailyInsight = DailyInsight(
                user = user,
                insightText = insightText,
                generatedAt = Instant.now(),
                date = today,
                sentViaPush = false
            )
            
            val savedInsight = dailyInsightRepository.save(dailyInsight)
            logger.info { "Successfully generated daily insight for user ${user.email}" }
            
            savedInsight
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate daily insight for user ${user.email}" }
            null
        }
    }
    
    /**
     * Send daily insight via push notification
     */
    @Transactional
    fun sendDailyInsightPush(insight: DailyInsight): Boolean {
        logger.info { "Sending daily insight push for user: ${insight.user.email}" }
        
        return try {
            val responses = pushNotificationService.sendNotificationToUser(
                userId = insight.user.id!!,
                title = "💡 Daily Vocabulary Insight",
                body = insight.insightText,
                data = mapOf(
                    "type" to "daily_insight",
                    "insight_id" to insight.id.toString(),
                    "date" to insight.date
                )
            )
            
            val success = responses.any { it.success }
            
            if (success) {
                // Update insight as sent
                val updatedInsight = insight.copy(
                    sentViaPush = true,
                    pushSentAt = Instant.now()
                )
                dailyInsightRepository.save(updatedInsight)
                logger.info { "Successfully sent daily insight push for user ${insight.user.email}" }
            } else {
                logger.warn { "Failed to send daily insight push for user ${insight.user.email}" }
            }
            
            success
        } catch (e: Exception) {
            logger.error(e) { "Error sending daily insight push for user ${insight.user.email}" }
            false
        }
    }
    
    /**
     * Get today's insight for a user (for fallback when push notification is missed)
     */
    fun getTodaysInsightForUser(user: User): DailyInsight? {
        val today = LocalDate.now().toString()
        return dailyInsightRepository.findByUserAndDate(user, today)
    }
    
    /**
     * Get latest insight for a user
     */
    fun getLatestInsightForUser(user: User): DailyInsight? {
        return dailyInsightRepository.findLatestInsightByUser(user)
    }
    
    /**
     * Generate insights for all eligible users
     */
    @Transactional
    fun generateInsightsForAllUsers(): Int {
        logger.info { "Starting batch daily insight generation" }
        
        // Get all users with vocabulary data and premium access
        val eligibleUsers = userRepository.findAll().filter { user ->
            val progressStats = userProgressService.calculateProgressStats(user)
            val hasAccess = featureAccessService.canUseAiDailyInsight(user)
            val hasVocabulary = progressStats.totalWords > 0
            
            hasAccess && hasVocabulary
        }
        
        logger.info { "Found ${eligibleUsers.size} eligible users for daily insights" }
        
        var generatedCount = 0
        var sentCount = 0
        
        for (user in eligibleUsers) {
            try {
                val insight = generateDailyInsightForUser(user)
                if (insight != null) {
                    generatedCount++
                    
                    // Send push notification
                    if (sendDailyInsightPush(insight)) {
                        sentCount++
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to process user ${user.email} for daily insights" }
            }
        }
        
        logger.info { "Batch insight generation completed: $generatedCount generated, $sentCount sent" }
        return generatedCount
    }
}
