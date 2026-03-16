package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.ReviewEvent
import com.alirezaiyan.vokab.server.domain.entity.StudySession
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.ReviewEventRepository
import com.alirezaiyan.vokab.server.domain.repository.StudySessionRepository
import com.alirezaiyan.vokab.server.presentation.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@Service
class AnalyticsService(
    private val studySessionRepository: StudySessionRepository,
    private val reviewEventRepository: ReviewEventRepository
) {

    @Transactional
    fun syncSessions(user: User, request: SyncAnalyticsRequest): SyncAnalyticsResponse {
        val syncedIds = mutableListOf<String>()

        for (sessionReq in request.sessions) {
            val existing = studySessionRepository.findByUserAndClientSessionId(user, sessionReq.clientSessionId)
            if (existing.isPresent) {
                syncedIds.add(sessionReq.clientSessionId)
                continue
            }

            val session = studySessionRepository.save(
                StudySession(
                    user = user,
                    clientSessionId = sessionReq.clientSessionId,
                    startedAt = sessionReq.startedAt,
                    endedAt = sessionReq.endedAt,
                    durationMs = sessionReq.durationMs,
                    totalCards = sessionReq.totalCards,
                    correctCount = sessionReq.correctCount,
                    incorrectCount = sessionReq.incorrectCount,
                    reviewType = sessionReq.reviewType,
                    completedNormally = sessionReq.completedNormally,
                )
            )

            val events = sessionReq.events.map { eventReq ->
                ReviewEvent(
                    session = session,
                    user = user,
                    wordId = eventReq.wordId,
                    wordText = eventReq.wordText,
                    wordTranslation = eventReq.wordTranslation,
                    sourceLanguage = eventReq.sourceLanguage,
                    targetLanguage = eventReq.targetLanguage,
                    rating = eventReq.rating,
                    previousLevel = eventReq.previousLevel,
                    newLevel = eventReq.newLevel,
                    responseTimeMs = eventReq.responseTimeMs,
                    reviewedAt = eventReq.reviewedAt,
                )
            }
            reviewEventRepository.saveAll(events)

            syncedIds.add(sessionReq.clientSessionId)
        }

        logger.info { "Synced ${syncedIds.size} sessions for user ${user.id}" }
        return SyncAnalyticsResponse(syncedSessionIds = syncedIds)
    }

    @Transactional(readOnly = true)
    fun getStudyInsights(user: User): StudyInsightsResponse {
        val totalCards = reviewEventRepository.countByUser(user)
        val totalCorrect = reviewEventRepository.countCorrectByUser(user)
        val totalStudyTime = studySessionRepository.totalStudyTimeByUser(user)
        val totalSessions = studySessionRepository.countByUser(user)
        val daysStudied = studySessionRepository.countDistinctStudyDays(user.id!!)
        val uniqueWords = reviewEventRepository.countDistinctWordsReviewed(user)
        val abandoned = studySessionRepository.countAbandonedByUser(user)
        val wordsMastered = reviewEventRepository.countWordsMastered(user)
        val avgResponseTime = reviewEventRepository.getAverageResponseTime(user)

        val accuracy = if (totalCards > 0) (totalCorrect.toDouble() / totalCards * 100) else 0.0
        val avgSession = if (totalSessions > 0) totalStudyTime / totalSessions else null
        val completionRate = if (totalSessions > 0) ((totalSessions - abandoned).toDouble() / totalSessions * 100) else null

        return StudyInsightsResponse(
            totalCardsReviewed = totalCards,
            totalCorrect = totalCorrect,
            accuracyPercent = accuracy,
            totalStudyTimeMs = totalStudyTime,
            totalSessions = totalSessions,
            daysStudied = daysStudied,
            uniqueWordsReviewed = uniqueWords,
            averageResponseTimeMs = avgResponseTime?.toLong(),
            averageSessionDurationMs = avgSession,
            sessionCompletionRate = completionRate,
            wordsMasteredCount = wordsMastered
        )
    }

    @Transactional(readOnly = true)
    fun getDailyStats(user: User, startDate: String, endDate: String): List<DailyStatsResponse> {
        val start = LocalDate.parse(startDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val end = LocalDate.parse(endDate).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1
        val sessions = studySessionRepository.findByUserAndDateRange(user, start, end)

        return sessions.groupBy {
            Instant.ofEpochMilli(it.startedAt).atZone(ZoneOffset.UTC).toLocalDate().toString()
        }.map { (date, daySessions) ->
            DailyStatsResponse(
                date = date,
                sessionsCount = daySessions.size,
                cardsReviewed = daySessions.sumOf { it.totalCards },
                correctCount = daySessions.sumOf { it.correctCount },
                incorrectCount = daySessions.sumOf { it.incorrectCount },
                studyTimeMs = daySessions.sumOf { it.durationMs }
            )
        }.sortedBy { it.date }
    }

    @Transactional(readOnly = true)
    fun getDifficultWords(user: User, minReviews: Int, limit: Int): List<DifficultWordResponse> {
        return reviewEventRepository.findDifficultWords(user, minReviews, PageRequest.of(0, limit))
            .map { p ->
                DifficultWordResponse(
                    wordId = p.wordId,
                    wordText = p.wordText,
                    wordTranslation = p.wordTranslation,
                    sourceLanguage = p.sourceLanguage,
                    targetLanguage = p.targetLanguage,
                    totalReviews = p.total.toInt(),
                    errorCount = p.errors.toInt(),
                    errorRate = if (p.total > 0) p.errors.toDouble() / p.total else 0.0
                )
            }
    }

    @Transactional(readOnly = true)
    fun getMostReviewedWords(user: User, limit: Int): List<MostReviewedWordResponse> {
        return reviewEventRepository.findMostReviewedWords(user, PageRequest.of(0, limit))
            .map { p ->
                MostReviewedWordResponse(
                    wordId = p.wordId,
                    wordText = p.wordText,
                    wordTranslation = p.wordTranslation,
                    totalReviews = p.total.toInt()
                )
            }
    }

    @Transactional(readOnly = true)
    fun getAccuracyByLevel(user: User): List<AccuracyByLevelResponse> {
        return reviewEventRepository.getAccuracyByLevel(user).map { p ->
            AccuracyByLevelResponse(
                level = p.level,
                totalReviews = p.total,
                correctCount = p.correct,
                accuracyPercent = if (p.total > 0) (p.correct.toDouble() / p.total * 100) else 0.0
            )
        }
    }

    @Transactional(readOnly = true)
    fun getAccuracyByHour(user: User): List<HourlyAccuracyResponse> {
        return reviewEventRepository.getAccuracyByHour(user.id!!).map { p ->
            HourlyAccuracyResponse(
                hour = p.hour,
                totalReviews = p.total,
                correctCount = p.correct,
                accuracyPercent = if (p.total > 0) (p.correct.toDouble() / p.total * 100) else 0.0
            )
        }
    }

    @Transactional(readOnly = true)
    fun getAccuracyByDayOfWeek(user: User): List<DayOfWeekAccuracyResponse> {
        return reviewEventRepository.getAccuracyByDayOfWeek(user.id!!).map { p ->
            DayOfWeekAccuracyResponse(
                dayOfWeek = p.dayOfWeek,
                totalReviews = p.total,
                correctCount = p.correct,
                accuracyPercent = if (p.total > 0) (p.correct.toDouble() / p.total * 100) else 0.0
            )
        }
    }

    @Transactional(readOnly = true)
    fun getRecentSessions(user: User, limit: Int): List<StudySessionResponse> {
        return studySessionRepository.findByUserOrderByStartedAtDesc(user, PageRequest.of(0, limit))
            .map { session ->
                StudySessionResponse(
                    clientSessionId = session.clientSessionId,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    durationMs = session.durationMs,
                    totalCards = session.totalCards,
                    correctCount = session.correctCount,
                    incorrectCount = session.incorrectCount,
                    reviewType = session.reviewType,
                    completedNormally = session.completedNormally
                )
            }
    }

    @Transactional(readOnly = true)
    fun getHeatmap(user: User, startMs: Long, endMs: Long): List<HeatmapDayResponse> {
        return reviewEventRepository.getHeatmapData(user.id!!, startMs, endMs).map { p ->
            HeatmapDayResponse(
                date = p.day,
                count = p.count.toInt()
            )
        }
    }

    @Transactional(readOnly = true)
    fun getLevelTransitions(user: User): List<LevelTransitionResponse> {
        return reviewEventRepository.getLevelTransitions(user).map { p ->
            LevelTransitionResponse(
                fromLevel = p.fromLevel,
                toLevel = p.toLevel,
                count = p.count
            )
        }
    }

    @Transactional(readOnly = true)
    fun getWordsMastered(user: User, limit: Int): List<MasteredWordResponse> {
        return reviewEventRepository.findWordsMastered(user, PageRequest.of(0, limit)).map { p ->
            MasteredWordResponse(
                wordId = p.wordId,
                wordText = p.wordText,
                wordTranslation = p.wordTranslation,
                masteredAt = p.masteredAt
            )
        }
    }

    @Transactional(readOnly = true)
    fun getStatsByLanguagePair(user: User): List<LanguagePairStatsResponse> {
        return reviewEventRepository.getStatsByLanguagePair(user).map { p ->
            LanguagePairStatsResponse(
                sourceLanguage = p.sourceLanguage,
                targetLanguage = p.targetLanguage,
                totalReviews = p.total,
                correctCount = p.correct,
                uniqueWords = p.uniqueWords,
                accuracyPercent = if (p.total > 0) (p.correct.toDouble() / p.total * 100) else 0.0
            )
        }
    }

    @Transactional(readOnly = true)
    fun getMonthlyStats(user: User): List<MonthlyStatsResponse> {
        return reviewEventRepository.getMonthlyStats(user.id!!).map { p ->
            MonthlyStatsResponse(
                year = p.yr,
                month = p.mo,
                totalReviews = p.total,
                correctCount = p.correct,
                accuracyPercent = if (p.total > 0) (p.correct.toDouble() / p.total * 100) else 0.0
            )
        }
    }

    @Transactional(readOnly = true)
    fun getResponseTimeTrend(user: User): List<ResponseTimeTrendResponse> {
        return reviewEventRepository.getResponseTimeTrend(user.id!!).map { p ->
            ResponseTimeTrendResponse(
                year = p.yr,
                week = p.wk,
                avgResponseTimeMs = p.avgMs
            )
        }
    }

    @Transactional(readOnly = true)
    fun getComebackWords(user: User): List<ComebackWordResponse> {
        return reviewEventRepository.findComebackWords(user).map { p ->
            ComebackWordResponse(
                wordId = p.wordId,
                wordText = p.wordText,
                wordTranslation = p.wordTranslation
            )
        }
    }
}
