package com.alirezaiyan.vokab.server.domain.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "users")
@JsonIgnoreProperties(value = ["pushTokens"], allowGetters = false)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, unique = true)
    val email: String,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(name = "google_id", unique = true)
    val googleId: String? = null,
    
    @Column(name = "profile_image_url")
    val profileImageUrl: String? = null,
    
    @Column(name = "revenuecat_user_id", unique = true)
    val revenueCatUserId: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    
    @Column(name = "subscription_expires_at")
    val subscriptionExpiresAt: Instant? = null,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
    
    @Column(name = "last_login_at")
    val lastLoginAt: Instant? = null,
    
    @Column(name = "current_streak", nullable = false)
    val currentStreak: Int = 0,
    
    @Column(name = "longest_streak", nullable = false)
    val longestStreak: Int = 0,
    
    // Free tier usage tracking for AI features
    @Column(name = "ai_extraction_usage_count", nullable = false)
    var aiExtractionUsageCount: Int = 0,
    
    @Column(nullable = false)
    val active: Boolean = true,
    
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val pushTokens: MutableList<PushToken> = mutableListOf()
) {
    /**
     * Override toString() to prevent LazyInitializationException
     * Kotlin data class auto-generates toString() that includes all fields
     * We exclude pushTokens to avoid lazy loading after session is closed
     */
    override fun toString(): String {
        return "User(id=$id, email='$email', name='$name', subscriptionStatus=$subscriptionStatus)"
    }
}

enum class SubscriptionStatus {
    FREE,
    TRIAL,
    ACTIVE,
    EXPIRED,
    CANCELLED
}

