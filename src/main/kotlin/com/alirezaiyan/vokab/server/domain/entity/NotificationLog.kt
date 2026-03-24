package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "notification_log")
class NotificationLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,                             // no FK intentionally

    @Column(name = "notification_type", nullable = false)
    val notificationType: String,

    @Column(name = "title")
    val title: String? = null,

    @Column(name = "body", columnDefinition = "TEXT")
    val body: String? = null,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now(),

    @Column(name = "opened_at")
    var openedAt: Instant? = null,

    @Column(name = "data_payload", columnDefinition = "jsonb")
    val dataPayload: String? = null              // JSON string
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationLog) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = if (id != 0L) id.hashCode() else System.identityHashCode(this)
}
