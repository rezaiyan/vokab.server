package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "notification_schedule")
data class NotificationSchedule(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(name = "optimal_send_hour", nullable = false)
    var optimalSendHour: Int = 18,

    @Column(name = "timezone_offset_hrs", nullable = false)
    var timezoneOffsetHrs: Int = 0,

    @Column(name = "data_confidence", nullable = false)
    var dataConfidence: Int = 0,

    @Column(name = "last_computed_at")
    var lastComputedAt: Instant? = null,

    @Column(name = "last_sent_date")
    var lastSentDate: LocalDate? = null,

    @Column(name = "last_sent_type")
    var lastSentType: String? = null,

    @Column(name = "consecutive_ignores", nullable = false)
    var consecutiveIgnores: Int = 0,

    @Column(name = "suppressed_until")
    var suppressedUntil: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun toString(): String =
        "NotificationSchedule(id=$id, userId=${user.id}, optimalSendHour=$optimalSendHour)"
}
