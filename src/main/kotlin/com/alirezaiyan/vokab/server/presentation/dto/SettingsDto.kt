package com.alirezaiyan.vokab.server.presentation.dto

data class SettingsDto(
    val languageCode: String,
    val themeMode: String,
    val notificationsEnabled: Boolean,
    val dailyReminderTime: String
)


