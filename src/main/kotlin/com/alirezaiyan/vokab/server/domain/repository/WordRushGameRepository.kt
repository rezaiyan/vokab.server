package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.WordRushGame
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface WordRushGameRepository : JpaRepository<WordRushGame, Long> {

    fun existsByUserAndClientGameId(user: User, clientGameId: String): Boolean

    fun countByUser(user: User): Long

    fun countByUserAndCompletedNormallyTrue(user: User): Long

    fun findFirstByUserOrderByBestStreakDesc(user: User): WordRushGame?

    @Query("SELECT COALESCE(SUM(g.score), 0) FROM WordRushGame g WHERE g.user = :user")
    fun sumScoreByUser(@Param("user") user: User): Long

    @Query("SELECT COALESCE(SUM(g.durationMs), 0) FROM WordRushGame g WHERE g.user = :user")
    fun sumDurationMsByUser(@Param("user") user: User): Long

    @Query("SELECT COALESCE(AVG(CAST(g.score AS double)), 0.0) FROM WordRushGame g WHERE g.user = :user")
    fun avgScoreByUser(@Param("user") user: User): Double

    @Query(
        "SELECT COALESCE(AVG(CAST(g.correctCount AS double) / NULLIF(g.totalQuestions, 0) * 100), 0.0) " +
            "FROM WordRushGame g WHERE g.user = :user"
    )
    fun avgAccuracyPercentByUser(@Param("user") user: User): Double

    @Query("SELECT COALESCE(AVG(CAST(g.avgResponseMs AS double)), 0.0) FROM WordRushGame g WHERE g.user = :user")
    fun avgResponseMsByUser(@Param("user") user: User): Double

    fun findTop20ByUserOrderByPlayedAtDesc(user: User): List<WordRushGame>

    fun findByUser(user: User): List<WordRushGame>
}
