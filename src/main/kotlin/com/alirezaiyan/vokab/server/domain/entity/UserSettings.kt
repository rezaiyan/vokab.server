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

    // Notifications
    @Column(nullable = false)
    var notificationsEnabled: Boolean = true,

    @Column(nullable = false)
    var reviewReminders: Boolean = true,

    @Column(nullable = false)
    var motivationalMessages: Boolean = true,

    @Column(nullable = false)
    var dailyReminderTime: String = "18:00",

    @Column(nullable = false)
    var minimumDueCards: Int = 5,

    // Review settings
    @Column(nullable = false)
    var successesToAdvance: Int = 1,

    @Column(nullable = false)
    var forgotPenalty: Int = 2,
)


