package com.alirezaiyan.vokab.server.presentation.dto

import jakarta.validation.constraints.Pattern

data class SettingsDto(
    val languageCode: String,
    val themeMode: String,
    val notificationsEnabled: Boolean,
    @field:Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$", message = "Must be HH:MM format")
    val dailyReminderTime: String = "18:00"
)


