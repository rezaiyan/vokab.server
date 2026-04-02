package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "email_log")
data class EmailLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id")
    val userId: Long? = null,

    @Column(name = "recipient_email", nullable = false)
    val recipientEmail: String,

    @Column(nullable = false, length = 50)
    val category: String,

    @Column(name = "template_id", nullable = false, length = 100)
    val templateId: String,

    @Column(nullable = false, length = 500)
    val subject: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: EmailStatus = EmailStatus.QUEUED,

    @Column(length = 50)
    val provider: String? = null,

    @Column(name = "provider_id")
    val providerId: String? = null,

    @Column(name = "error_message")
    val errorMessage: String? = null,

    @Column(name = "sent_at")
    val sentAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

enum class EmailStatus {
    QUEUED, SENT, FAILED, BOUNCED
}
