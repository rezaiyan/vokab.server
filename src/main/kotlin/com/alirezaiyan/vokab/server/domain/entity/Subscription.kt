package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "subscriptions")
data class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    
    @Column(name = "revenuecat_subscription_id", unique = true)
    val revenueCatSubscriptionId: String? = null,
    
    @Column(name = "product_id", nullable = false)
    val productId: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: SubscriptionStatus,
    
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,
    
    @Column(name = "expires_at")
    val expiresAt: Instant? = null,
    
    @Column(name = "cancelled_at")
    val cancelledAt: Instant? = null,
    
    @Column(name = "is_trial")
    val isTrial: Boolean = false,
    
    @Column(name = "auto_renew")
    val autoRenew: Boolean = true,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)

