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
            "ORDER BY (u.currentStreak * 0.4 + u.longestStreak * 0.6) DESC"
    )
    fun findTopUsersByScore(pageable: Pageable): List<User>

    @Query(
        "SELECT COUNT(u) + 1 FROM User u WHERE u.active = true " +
            "AND (u.currentStreak * 0.4 + u.longestStreak * 0.6) > " +
            "(SELECT u2.currentStreak * 0.4 + u2.longestStreak * 0.6 FROM User u2 WHERE u2.id = :userId)"
    )
    fun findUserRank(userId: Long): Long
}