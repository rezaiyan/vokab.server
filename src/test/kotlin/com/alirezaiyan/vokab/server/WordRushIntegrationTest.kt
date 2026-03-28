package com.alirezaiyan.vokab.server

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.WordRushGameRepository
import com.alirezaiyan.vokab.server.presentation.dto.*
import com.alirezaiyan.vokab.server.service.WordRushService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WordRushIntegrationTest {

    @Autowired lateinit var wordRushService: WordRushService
    @Autowired lateinit var wordRushGameRepository: WordRushGameRepository
    @Autowired lateinit var testUserHelper: TestUserHelper

    lateinit var user: User

    @BeforeAll
    fun setupUser() {
        user = testUserHelper.saveAndCommit(User(email = "wordrush@test.com", name = "WordRush User"))
    }

    @AfterAll
    fun teardownUser() {
        testUserHelper.deleteByEmail("wordrush@test.com")
    }

    @BeforeEach
    fun setup() {
        testUserHelper.clearUserWordRushGames(user.id!!)
    }

    // -- Sync -----------------------------------------------------------------

    @Test
    fun `sync creates games and returns their ids`() {
        val request = SyncWordRushRequest(
            games = listOf(
                createGameRequest("game-1", score = 100, correctCount = 10, totalQuestions = 12, bestStreak = 5),
                createGameRequest("game-2", score = 200, correctCount = 15, totalQuestions = 15, bestStreak = 8),
                createGameRequest("game-3", score = 50, correctCount = 5, totalQuestions = 12, bestStreak = 2, completedNormally = false),
            )
        )

        val response = wordRushService.syncGames(user, request)

        assertEquals(listOf("game-1", "game-2", "game-3"), response.syncedGameIds)
        assertEquals(3, wordRushGameRepository.countByUser(user))
    }

    @Test
    fun `sync is idempotent for same clientGameId`() {
        val request = SyncWordRushRequest(
            games = listOf(
                createGameRequest("idempotent-1", score = 100),
            )
        )

        wordRushService.syncGames(user, request)
        val response2 = wordRushService.syncGames(user, request)

        assertEquals(listOf("idempotent-1"), response2.syncedGameIds)
        assertEquals(1, wordRushGameRepository.countByUser(user))
    }

    @Test
    fun `sync handles mix of new and existing games`() {
        val firstRequest = SyncWordRushRequest(
            games = listOf(createGameRequest("existing-1", score = 100))
        )
        wordRushService.syncGames(user, firstRequest)

        val secondRequest = SyncWordRushRequest(
            games = listOf(
                createGameRequest("existing-1", score = 100),
                createGameRequest("new-1", score = 200),
            )
        )
        val response = wordRushService.syncGames(user, secondRequest)

        assertEquals(listOf("existing-1", "new-1"), response.syncedGameIds)
        assertEquals(2, wordRushGameRepository.countByUser(user))
    }

    // -- Insights -------------------------------------------------------------

    @Test
    fun `insights returns zeros for user with no data`() {
        val insights = wordRushService.getInsights(user)

        assertEquals(0L, insights.totalGames)
        assertEquals(0L, insights.totalCompleted)
        assertEquals(0.0, insights.completionRatePercent)
        assertEquals(0, insights.bestStreakEver)
        assertEquals(0.0, insights.avgScore)
        assertEquals(0.0, insights.avgAccuracyPercent)
        assertEquals(0L, insights.totalTimePlayedMs)
        assertEquals(0.0, insights.avgDurationMs)
        assertEquals(0.0, insights.avgResponseMs)
    }

    @Test
    fun `insights calculates aggregates correctly after syncing 3 games`() {
        val request = SyncWordRushRequest(
            games = listOf(
                createGameRequest("g1", score = 100, correctCount = 10, totalQuestions = 12, bestStreak = 5, durationMs = 30000, avgResponseMs = 2000),
                createGameRequest("g2", score = 200, correctCount = 15, totalQuestions = 15, bestStreak = 8, durationMs = 45000, avgResponseMs = 1500),
                createGameRequest("g3", score = 50, correctCount = 5, totalQuestions = 12, bestStreak = 2, durationMs = 25000, avgResponseMs = 3000, completedNormally = false),
            )
        )
        wordRushService.syncGames(user, request)

        val insights = wordRushService.getInsights(user)

        assertEquals(3L, insights.totalGames)
        assertEquals(2L, insights.totalCompleted)  // g3 is not completed normally
        assertEquals(2.0 / 3.0 * 100, insights.completionRatePercent, 0.01)
        assertEquals(8, insights.bestStreakEver)  // max of 5, 8, 2
        // avgScore = (100+200+50) / 3 = 116.67
        assertEquals(116.67, insights.avgScore, 0.01)
        // avgAccuracy = avg(10/12*100, 15/15*100, 5/12*100) = avg(83.33, 100, 41.67) = 75.0
        assertEquals(75.0, insights.avgAccuracyPercent, 0.01)
        // totalTime = 30000 + 45000 + 25000 = 100000
        assertEquals(100000L, insights.totalTimePlayedMs)
        // avgDuration = 100000 / 3 = 33333.33
        assertEquals(33333.33, insights.avgDurationMs, 0.01)
        // avgResponseMs = avg(2000, 1500, 3000) = 2166.67
        assertEquals(2166.67, insights.avgResponseMs, 0.01)
    }

    // -- History --------------------------------------------------------------

    @Test
    fun `history returns games in descending playedAt order`() {
        val request = SyncWordRushRequest(
            games = listOf(
                createGameRequest("old-game", score = 50, playedAt = 1700000000000),
                createGameRequest("new-game", score = 100, playedAt = 1700100000000),
            )
        )
        wordRushService.syncGames(user, request)

        val history = wordRushService.getHistory(user)

        assertEquals(2, history.size)
        assertEquals("new-game", history[0].clientGameId)
        assertEquals("old-game", history[1].clientGameId)
    }

    @Test
    fun `history returns at most 20 games`() {
        val games = (1..25).map { i ->
            createGameRequest("game-$i", score = i * 10, playedAt = 1700000000000 + i * 1000)
        }
        wordRushService.syncGames(user, SyncWordRushRequest(games = games))

        val history = wordRushService.getHistory(user)

        assertEquals(20, history.size)
        // Most recent should be first
        assertEquals("game-25", history[0].clientGameId)
    }

    @Test
    fun `history returns empty list for user with no games`() {
        val history = wordRushService.getHistory(user)

        assertTrue(history.isEmpty())
    }

    // -- Data Isolation -------------------------------------------------------

    @Test
    fun `queries only return data for the requesting user`() {
        val otherUser = testUserHelper.saveAndCommit(User(email = "other-rush@test.com", name = "Other"))

        try {
            wordRushService.syncGames(
                user,
                SyncWordRushRequest(games = listOf(createGameRequest("my-game", score = 100)))
            )
            wordRushService.syncGames(
                otherUser,
                SyncWordRushRequest(games = listOf(createGameRequest("other-game", score = 200)))
            )

            val myInsights = wordRushService.getInsights(user)
            val otherInsights = wordRushService.getInsights(otherUser)

            assertEquals(1L, myInsights.totalGames)
            assertEquals(1L, otherInsights.totalGames)
            assertEquals(100.0, myInsights.avgScore, 0.01)
            assertEquals(200.0, otherInsights.avgScore, 0.01)
        } finally {
            testUserHelper.deleteByEmail("other-rush@test.com")
        }
    }

    // -- factory functions ----------------------------------------------------

    private fun createGameRequest(
        clientGameId: String,
        score: Int = 0,
        totalQuestions: Int = 12,
        correctCount: Int = 0,
        bestStreak: Int = 0,
        durationMs: Long = 30000,
        avgResponseMs: Long = 2000,
        grade: String = "B",
        livesRemaining: Int = 3,
        completedNormally: Boolean = true,
        playedAt: Long = System.currentTimeMillis(),
    ): SyncWordRushGameRequest = SyncWordRushGameRequest(
        clientGameId = clientGameId,
        score = score,
        totalQuestions = totalQuestions,
        correctCount = correctCount,
        bestStreak = bestStreak,
        durationMs = durationMs,
        avgResponseMs = avgResponseMs,
        grade = grade,
        livesRemaining = livesRemaining,
        completedNormally = completedNormally,
        playedAt = playedAt,
    )
}
