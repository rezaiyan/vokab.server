package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "refresh_tokens",
    indexes = [
        Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_refresh_tokens_family_id", columnList = "family_id"),
        Index(name = "idx_refresh_tokens_hash", columnList = "token_hash"),
        Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
    ]
)
data class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    val tokenHash: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    
    @Column(name = "family_id", nullable = false, length = 64)
    val familyId: String,
    
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "revoked_at")
    val revokedAt: Instant? = null,
    
    @Column(nullable = false)
    val revoked: Boolean = false,
    
    @Column(name = "replaced_by")
    val replacedBy: Long? = null,
    
    @Column(name = "device_id", length = 255)
    val deviceId: String? = null,
    
    @Column(name = "user_agent", length = 512)
    val userAgent: String? = null,
    
    @Column(name = "ip_address", length = 45)
    val ipAddress: String? = null
)

