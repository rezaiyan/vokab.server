package com.alirezaiyan.vokab.server.config

import com.alirezaiyan.vokab.server.domain.repository.NotificationScheduleRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.service.NotificationTimingService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class NotificationScheduleBootstrapper(
    private val userRepository: UserRepository,
    private val notificationScheduleRepository: NotificationScheduleRepository,
    private val notificationTimingService: NotificationTimingService
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val activeUsers = userRepository.findAllActiveUsersWithPushTokens()
        if (activeUsers.isEmpty()) return

        val scheduledUserIds = notificationScheduleRepository.findAllScheduledUserIds()
        val unscheduled = activeUsers.filter { it.id !in scheduledUserIds }

        if (unscheduled.isEmpty()) return
        logger.info { "Bootstrapping notification schedules for ${unscheduled.size} users" }
        notificationTimingService.refreshSchedulesForAllUsers(unscheduled)
    }
}
