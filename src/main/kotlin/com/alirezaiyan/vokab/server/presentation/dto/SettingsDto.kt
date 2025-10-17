package com.alirezaiyan.vokab.server.presentation.dto

data class SettingsDto(
    val languageCode: String,
    val themeMode: String,
    val notificationsEnabled: Boolean,
    val reviewReminders: Boolean,
    val motivationalMessages: Boolean,
    val dailyReminderTime: String,
    val minimumDueCards: Int,
    val successesToAdvance: Int,
    val forgotPenalty: Int,
)


