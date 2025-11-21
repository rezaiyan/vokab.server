package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun findByGoogleId(googleId: String): Optional<User>
    fun findByAppleId(appleId: String): Optional<User>
    fun findByRevenueCatUserId(revenueCatUserId: String): Optional<User>
    fun findByCurrentStreakGreaterThanAndActiveTrue(currentStreak: Int): List<User>

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query(
        "update User u " +
            "set u.aiExtractionUsageCount = u.aiExtractionUsageCount + 1 " +
            "where u.id = :userId and u.aiExtractionUsageCount < :limit"
    )
    fun incrementAiExtractionUsageIfBelowLimit(
        @Param("userId") userId: Long,
        @Param("limit") limit: Int
    ): Int
}