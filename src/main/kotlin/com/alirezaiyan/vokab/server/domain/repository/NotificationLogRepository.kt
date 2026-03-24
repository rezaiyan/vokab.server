package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.NotificationLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface NotificationLogRepository : JpaRepository<NotificationLog, Long> {
    fun findTopByUserIdOrderBySentAtDesc(userId: Long): NotificationLog?

    @Query("""
        SELECT nl FROM NotificationLog nl
        WHERE nl.userId = :userId AND nl.sentAt >= :since
        ORDER BY nl.sentAt DESC
    """)
    fun findRecentByUserId(userId: Long, since: Instant): List<NotificationLog>

    fun countByUserIdAndOpenedAtIsNotNullAndSentAtAfter(userId: Long, since: Instant): Long
}
