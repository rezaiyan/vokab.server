package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "user_settings")
class UserSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User? = null,

    @Column(nullable = false)
    var languageCode: String = "en",

    @Column(nullable = false)
    var themeMode: String = "AUTO",

    // Notifications - simplified to on/off + time
    @Column(nullable = false)
    var notificationsEnabled: Boolean = true,

    @Column(nullable = false)
    var dailyReminderTime: String = "18:00"
)


