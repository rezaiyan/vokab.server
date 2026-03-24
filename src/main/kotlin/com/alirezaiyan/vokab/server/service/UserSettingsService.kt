package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.NotificationSchedule
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.UserSettings
import com.alirezaiyan.vokab.server.domain.repository.NotificationScheduleRepository
import com.alirezaiyan.vokab.server.domain.repository.UserSettingsRepository
import com.alirezaiyan.vokab.server.presentation.dto.EngagementStatsDto
import com.alirezaiyan.vokab.server.presentation.dto.SettingsDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class UserSettingsService(
    private val repo: UserSettingsRepository,
    private val notificationScheduleRepository: NotificationScheduleRepository,
    private val notificationEngagementService: NotificationEngagementService
) {
    fun get(user: User): SettingsDto {
        val s = repo.findByUser(user) ?: repo.save(UserSettings(user = user))
        val schedule = notificationScheduleRepository.findByUser(user)
        val engagementStats = user.id?.let {
            runCatching { notificationEngagementService.getEngagementStats(it) }.getOrNull()
        }
        return s.toDto(schedule, engagementStats)
    }

    @Transactional
    fun update(user: User, dto: SettingsDto): SettingsDto {
        val current = repo.findByUser(user) ?: UserSettings(user = user)
        current.languageCode = dto.languageCode
        current.themeMode = dto.themeMode
        current.notificationsEnabled = dto.notificationsEnabled
        current.dailyReminderTime = dto.dailyReminderTime
        current.notificationFrequency = dto.notificationFrequency
        return repo.save(current).toDto(null, null)
    }
}

private fun UserSettings.toDto(
    schedule: NotificationSchedule?,
    engagementStats: NotificationEngagementService.EngagementStats?
) = SettingsDto(
    languageCode = languageCode,
    themeMode = themeMode,
    notificationsEnabled = notificationsEnabled,
    dailyReminderTime = dailyReminderTime,
    notificationFrequency = notificationFrequency,
    optimalSendHour = schedule?.optimalSendHour,
    dataConfidence = schedule?.dataConfidence,
    engagementStats = engagementStats?.let { stats ->
        EngagementStatsDto(
            openRatePercent = stats.openRatePercent,
            consecutiveIgnores = stats.consecutiveIgnores,
            suppressedUntil = schedule?.suppressedUntil
        )
    }
)
