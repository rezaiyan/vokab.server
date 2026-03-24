package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.NotificationSchedule
import com.alirezaiyan.vokab.server.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NotificationScheduleRepository : JpaRepository<NotificationSchedule, Long> {
    fun findByUser(user: User): NotificationSchedule?
    fun findByUserId(userId: Long): NotificationSchedule?

    @Query("""
        SELECT ns FROM NotificationSchedule ns
        WHERE ns.optimalSendHour = :hour
          AND (ns.suppressedUntil IS NULL OR ns.suppressedUntil < CURRENT_DATE)
          AND (ns.lastSentDate IS NULL OR ns.lastSentDate < CURRENT_DATE)
    """)
    fun findUsersToNotifyAtHour(hour: Int): List<NotificationSchedule>
}
