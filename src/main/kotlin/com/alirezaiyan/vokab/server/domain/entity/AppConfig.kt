package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "app_config")
data class AppConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val namespace: String,

    @Column(nullable = false)
    val key: String,

    @Column
    val value: String? = null,

    @Column(nullable = false)
    val type: String = "string",

    @Column
    val description: String? = null,

    @Column(nullable = false)
    val enabled: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "app_config_history")
data class AppConfigHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val namespace: String,

    @Column(nullable = false)
    val key: String,

    @Column(name = "old_value")
    val oldValue: String? = null,

    @Column(name = "new_value")
    val newValue: String? = null,

    @Column(name = "changed_by")
    val changedBy: String? = null,

    @Column(name = "changed_at", nullable = false, updatable = false)
    val changedAt: Instant = Instant.now()
)
