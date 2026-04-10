package com.alirezaiyan.vokab.server.service.notification

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.domain.event.UserSignedUpEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class AdminNotificationListener(
    private val notificationChannel: NotificationChannel,
    private val appProperties: AppProperties
) {

    @Async
    @EventListener
    fun onUserSignedUp(event: UserSignedUpEvent) {
        if (!appProperties.notifications.admin.enabled) return

        try {
            notificationChannel.send(
                title = "New Signup",
                body = "${event.name} joined via ${event.provider}"
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send admin notification for signup userId=${event.userId}" }
        }
    }
}
