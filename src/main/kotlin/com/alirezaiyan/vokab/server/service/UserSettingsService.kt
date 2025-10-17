package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.UserSettings
import com.alirezaiyan.vokab.server.domain.repository.UserSettingsRepository
import com.alirezaiyan.vokab.server.presentation.dto.SettingsDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserSettingsService(
    private val repo: UserSettingsRepository
) {
    fun get(user: User): SettingsDto {
        val s = repo.findByUser(user) ?: repo.save(UserSettings(user = user))
        return s.toDto()
    }

    @Transactional
    fun update(user: User, dto: SettingsDto): SettingsDto {
        val current = repo.findByUser(user) ?: UserSettings(user = user)
        current.languageCode = dto.languageCode
        current.themeMode = dto.themeMode
        current.notificationsEnabled = dto.notificationsEnabled
        current.reviewReminders = dto.reviewReminders
        current.motivationalMessages = dto.motivationalMessages
        current.dailyReminderTime = dto.dailyReminderTime
        current.minimumDueCards = dto.minimumDueCards
        current.successesToAdvance = dto.successesToAdvance
        current.forgotPenalty = dto.forgotPenalty
        return repo.save(current).toDto()
    }
}

private fun UserSettings.toDto() = SettingsDto(
    languageCode = languageCode,
    themeMode = themeMode,
    notificationsEnabled = notificationsEnabled,
    reviewReminders = reviewReminders,
    motivationalMessages = motivationalMessages,
    dailyReminderTime = dailyReminderTime,
    minimumDueCards = minimumDueCards,
    successesToAdvance = successesToAdvance,
    forgotPenalty = forgotPenalty,
)


