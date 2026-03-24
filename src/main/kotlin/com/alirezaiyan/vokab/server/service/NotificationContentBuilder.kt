package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.service.NotificationTypeSelector.NotificationType
import com.alirezaiyan.vokab.server.service.NotificationTypeSelector.NotificationType.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

data class NotificationPayload(
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val type: NotificationType
)

@Service
class NotificationContentBuilder(
    private val openRouterService: OpenRouterService,
    private val userProgressService: UserProgressService,
    private val analyticsService: AnalyticsService,
    private val dailyInsightService: DailyInsightService,
    private val milestoneDetector: MilestoneDetector
) {
    fun build(user: User, type: NotificationType): NotificationPayload {
        return when (type) {
            STREAK_RISK        -> buildStreakRisk(user)
            PROGRESS_MILESTONE -> buildMilestone(user)
            WEEKLY_PREVIEW     -> buildWeeklyPreview(user)
            DUE_CARDS          -> buildDueCards(user)
            COMEBACK_ALERT     -> buildComebackAlert(user)
            DAILY_INSIGHT      -> buildDailyInsight(user)
            NONE               -> error("Should not build payload for NONE type")
        }
    }

    private fun buildStreakRisk(user: User): NotificationPayload {
        val stats = userProgressService.calculateProgressStats(user)
        val body = openRouterService.generateStreakReminderMessage(user.currentStreak, user.name, stats)
            .blockOptional()
            .orElse("Your ${user.currentStreak}-day streak ends at midnight. Keep it alive! 🔥")
        return NotificationPayload(
            title = "Your ${user.currentStreak}-day streak ends at midnight 🔥",
            body = body,
            data = mapOf(
                "type" to "streak_risk",
                "current_streak" to user.currentStreak.toString(),
                "deep_link" to "vokab://review"
            ),
            type = STREAK_RISK
        )
    }

    private fun buildDueCards(user: User): NotificationPayload {
        val stats = userProgressService.calculateProgressStats(user)
        val estimatedMinutes = maxOf(1, (stats.dueCards * 8) / 60)
        val primaryLang = runCatching { analyticsService.getStatsByLanguagePair(user) }
            .getOrNull()?.firstOrNull()?.targetLanguage ?: "vocabulary"
        return NotificationPayload(
            title = "📚 ${stats.dueCards} words are waiting",
            body = "Your $primaryLang review takes ~$estimatedMinutes min.",
            data = mapOf(
                "type" to "due_cards",
                "due_count" to stats.dueCards.toString(),
                "deep_link" to "vokab://review/due"
            ),
            type = DUE_CARDS
        )
    }

    private fun buildComebackAlert(user: User): NotificationPayload {
        val difficultWords = analyticsService.getDifficultWords(user, minReviews = 3, limit = 1)
        val word = difficultWords.first()
        return NotificationPayload(
            title = "\"${word.wordText}\" wants a rematch 🔄",
            body = "You've missed this one recently. 60 seconds to lock it in.",
            data = mapOf(
                "type" to "comeback_alert",
                "word_id" to word.wordId.toString(),
                "word_text" to word.wordText,
                "deep_link" to "vokab://word/${word.wordId}"
            ),
            type = COMEBACK_ALERT
        )
    }

    private fun buildWeeklyPreview(user: User): NotificationPayload {
        val report = analyticsService.getWeeklyReport(user)
        val changePercent = report.changePercent ?: 0.0
        val trend = when {
            changePercent > 5  -> "▲ ${changePercent.toInt()}% more than last week"
            changePercent < -5 -> "▼ ${(-changePercent).toInt()}% less than last week"
            else -> "steady pace"
        }
        return NotificationPayload(
            title = "Your week in review 📊",
            body = "${report.cardsReviewed} cards · ${report.accuracyPercent.toInt()}% accuracy · $trend",
            data = mapOf(
                "type" to "weekly_preview",
                "deep_link" to "vokab://stats/weekly"
            ),
            type = WEEKLY_PREVIEW
        )
    }

    private fun buildMilestone(user: User): NotificationPayload {
        val milestone = milestoneDetector.getPendingMilestone(user)
            ?: return buildFallbackInsight()
        val stats = userProgressService.calculateProgressStats(user)
        val body = openRouterService.generateMilestoneMessage(milestone, stats, user.name)
            .blockOptional()
            .orElse("You hit a new milestone: ${milestone.description}! 🏆")
        return NotificationPayload(
            title = milestone.title,
            body = body,
            data = mapOf(
                "type" to "milestone",
                "milestone_type" to milestone.type,
                "deep_link" to "vokab://stats/progress"
            ),
            type = PROGRESS_MILESTONE
        )
    }

    private fun buildDailyInsight(user: User): NotificationPayload {
        val insight = dailyInsightService.generateDailyInsightForUser(user)
            ?: return buildFallbackInsight()
        return NotificationPayload(
            title = "💡 Your vocabulary insight",
            body = insight.insightText,
            data = mapOf(
                "type" to "daily_insight",
                "insight_id" to insight.id.toString(),
                "deep_link" to "vokab://insights"
            ),
            type = DAILY_INSIGHT
        )
    }

    private fun buildFallbackInsight(): NotificationPayload {
        return NotificationPayload(
            title = "💡 Keep up the great work!",
            body = "Every word you review brings you closer to fluency.",
            data = mapOf("type" to "daily_insight", "deep_link" to "vokab://insights"),
            type = DAILY_INSIGHT
        )
    }
}
