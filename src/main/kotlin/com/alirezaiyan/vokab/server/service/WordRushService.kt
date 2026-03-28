package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.WordRushGame
import com.alirezaiyan.vokab.server.domain.repository.WordRushGameRepository
import com.alirezaiyan.vokab.server.presentation.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class WordRushService(
    private val wordRushGameRepository: WordRushGameRepository,
) {

    @Transactional
    fun syncGames(user: User, request: SyncWordRushRequest): SyncWordRushResponse {
        val syncedIds = mutableListOf<String>()

        for (gameReq in request.games) {
            if (wordRushGameRepository.existsByUserAndClientGameId(user, gameReq.clientGameId)) {
                syncedIds.add(gameReq.clientGameId)
                continue
            }

            wordRushGameRepository.save(
                WordRushGame(
                    user = user,
                    clientGameId = gameReq.clientGameId,
                    score = gameReq.score,
                    totalQuestions = gameReq.totalQuestions,
                    correctCount = gameReq.correctCount,
                    bestStreak = gameReq.bestStreak,
                    durationMs = gameReq.durationMs,
                    avgResponseMs = gameReq.avgResponseMs,
                    grade = gameReq.grade,
                    livesRemaining = gameReq.livesRemaining,
                    completedNormally = gameReq.completedNormally,
                    playedAt = gameReq.playedAt,
                )
            )
            syncedIds.add(gameReq.clientGameId)
        }

        logger.info { "Synced ${syncedIds.size}/${request.games.size} Word Rush games for user ${user.id}" }
        return SyncWordRushResponse(syncedGameIds = syncedIds)
    }

    @Transactional(readOnly = true)
    fun getInsights(user: User): WordRushInsightsResponse {
        val totalGames = wordRushGameRepository.countByUser(user)
        val totalCompleted = wordRushGameRepository.countByUserAndCompletedNormallyTrue(user)
        val completionRatePercent = if (totalGames > 0) {
            totalCompleted.toDouble() / totalGames * 100
        } else {
            0.0
        }

        val bestStreakGame = wordRushGameRepository.findFirstByUserOrderByBestStreakDesc(user)
        val bestStreakEver = bestStreakGame?.bestStreak ?: 0

        val avgScore = wordRushGameRepository.avgScoreByUser(user)
        val avgAccuracyPercent = wordRushGameRepository.avgAccuracyPercentByUser(user)
        val totalTimePlayedMs = wordRushGameRepository.sumDurationMsByUser(user)
        val avgResponseMs = wordRushGameRepository.avgResponseMsByUser(user)

        val avgDurationMs = if (totalGames > 0) {
            totalTimePlayedMs.toDouble() / totalGames
        } else {
            0.0
        }

        return WordRushInsightsResponse(
            totalGames = totalGames,
            totalCompleted = totalCompleted,
            completionRatePercent = completionRatePercent,
            bestStreakEver = bestStreakEver,
            avgScore = avgScore,
            avgAccuracyPercent = avgAccuracyPercent,
            totalTimePlayedMs = totalTimePlayedMs,
            avgDurationMs = avgDurationMs,
            avgResponseMs = avgResponseMs,
        )
    }

    @Transactional(readOnly = true)
    fun getHistory(user: User): List<WordRushGameResponse> {
        return wordRushGameRepository.findTop20ByUserOrderByPlayedAtDesc(user).map { game ->
            WordRushGameResponse(
                clientGameId = game.clientGameId,
                score = game.score,
                totalQuestions = game.totalQuestions,
                correctCount = game.correctCount,
                bestStreak = game.bestStreak,
                durationMs = game.durationMs,
                avgResponseMs = game.avgResponseMs,
                grade = game.grade,
                livesRemaining = game.livesRemaining,
                completedNormally = game.completedNormally,
                playedAt = game.playedAt,
            )
        }
    }
}
