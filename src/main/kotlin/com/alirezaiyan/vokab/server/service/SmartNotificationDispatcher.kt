package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.NotificationLog
import com.alirezaiyan.vokab.server.domain.entity.NotificationSchedule
import com.alirezaiyan.vokab.server.domain.repository.NotificationLogRepository
import com.alirezaiyan.vokab.server.domain.repository.NotificationScheduleRepository
import com.alirezaiyan.vokab.server.service.NotificationTypeSelector.NotificationType
import com.alirezaiyan.vokab.server.service.push.PushNotificationService
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@Service
class SmartNotificationDispatcher(
    private val notificationScheduleRepository: NotificationScheduleRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationTypeSelector: NotificationTypeSelector,
    private val notificationContentBuilder: NotificationContentBuilder,
    private val pushNotificationService: PushNotificationService,
    private val milestoneDetector: MilestoneDetector,
    private val userProgressService: UserProgressService,
    private val objectMapper: ObjectMapper
) {
    fun dispatchForCurrentHour() {
        val hour = LocalTime.now(ZoneOffset.UTC).hour
        val schedules = notificationScheduleRepository.findUsersToNotifyAtHour(hour)
        logger.info { "Smart dispatch: hour=$hour, candidates=${schedules.size}" }

        for (schedule in schedules) {
            runCatching { dispatchForUser(schedule) }
                .onFailure { logger.error(it) { "Dispatch failed for user=${schedule.user.id}" } }
        }
    }

    private fun dispatchForUser(schedule: NotificationSchedule) {
        val user = schedule.user
        val userId = user.id ?: error("User id is null for schedule=${schedule.id}")
        val type = notificationTypeSelector.selectType(user, schedule)

        if (type == NotificationType.NONE) {
            logger.debug { "No notification selected for user=${user.id}" }
            return
        }

        val payload = notificationContentBuilder.build(user, type)
        val results = pushNotificationService.sendNotificationToUser(
            userId = userId,
            title = payload.title,
            body = payload.body,
            data = payload.data
        )

        val sent = results.any { it.success }
        if (sent) {
            notificationLogRepository.save(
                NotificationLog(
                    userId = userId,
                    notificationType = type.name,
                    title = payload.title,
                    body = payload.body,
                    dataPayload = objectMapper.writeValueAsString(payload.data)
                )
            )

            schedule.lastSentDate = LocalDate.now(ZoneOffset.UTC)
            schedule.lastSentType = type.name
            schedule.updatedAt = Instant.now()
            notificationScheduleRepository.save(schedule)

            if (type == NotificationType.PROGRESS_MILESTONE) {
                val stats = userProgressService.calculateProgressStats(user)
                milestoneDetector.recordMilestoneSnapshot(user, stats)
            }

            logger.info { "Sent $type to user=${user.id}" }
        } else {
            logger.warn { "Push delivery failed for user=${user.id}, type=$type" }
        }
    }
}
