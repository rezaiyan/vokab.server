package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "daily_insights")
data class DailyInsight(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    
    @Column(name = "insight_text", nullable = false, length = 500)
    val insightText: String,
    
    @Column(name = "generated_at", nullable = false)
    val generatedAt: Instant,
    
    @Column(name = "date", nullable = false)
    val date: String, // YYYY-MM-DD format
    
    @Column(name = "sent_via_push", nullable = false)
    val sentViaPush: Boolean = false,
    
    @Column(name = "push_sent_at")
    val pushSentAt: Instant? = null
) {
    constructor() : this(
        id = null,
        user = User(),
        insightText = "",
        generatedAt = Instant.now(),
        date = "",
        sentViaPush = false,
        pushSentAt = null
    )
}
