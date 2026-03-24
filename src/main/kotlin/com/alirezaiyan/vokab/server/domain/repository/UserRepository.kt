package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun findByGoogleId(googleId: String): Optional<User>
    fun findByAppleId(appleId: String): Optional<User>
    fun findByRevenueCatUserId(revenueCatUserId: String): Optional<User>
    fun findByCurrentStreakGreaterThanAndActiveTrue(currentStreak: Int): List<User>

    @Query(
        "SELECT u FROM User u WHERE u.active = true " +
            "ORDER BY (" +
            "(SELECT COUNT(w) FROM Word w WHERE w.level = 6 AND w.user = u) * 10 " +
            "+ u.currentStreak * 3 " +
            "+ u.longestStreak * 2" +
            ") DESC"
    )
    fun findTopUsersByScore(pageable: Pageable): List<User>

    @Query(
        "SELECT COUNT(u) + 1 FROM User u WHERE u.active = true AND (" +
            "(SELECT COUNT(w) FROM Word w WHERE w.level = 6 AND w.user = u) * 10 " +
            "+ u.currentStreak * 3 " +
            "+ u.longestStreak * 2" +
            ") > :userScore"
    )
    fun findUserRankByScore(userScore: Long): Long

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.pushTokens pt
        WHERE u.active = true AND pt.active = true
    """)
    fun findAllActiveUsersWithPushTokens(): List<User>
}