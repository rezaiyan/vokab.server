package com.alirezaiyan.vokab.server.service.notification

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.config.NotificationsConfig
import com.alirezaiyan.vokab.server.domain.event.UserSignedUpEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminNotificationListenerTest {

    private lateinit var channel: NotificationChannel
    private lateinit var listener: AdminNotificationListener

    @BeforeEach
    fun setUp() {
        channel = mockk(relaxed = true)
        val appProperties = AppProperties(
            notifications = NotificationsConfig(
                admin = NotificationsConfig.AdminConfig(enabled = true)
            )
        )
        listener = AdminNotificationListener(channel, appProperties)
    }

    @Test
    fun `should send notification when user signs up`() {
        val event = UserSignedUpEvent(
            userId = 42L,
            name = "Ali",
            email = "ali@example.com",
            provider = "google"
        )

        listener.onUserSignedUp(event)

        verify(exactly = 1) {
            channel.send(
                match { it.contains("New Signup") },
                match { it.contains("Ali") && it.contains("google") }
            )
        }
    }

    @Test
    fun `should not send notification when admin notifications are disabled`() {
        val disabledProps = AppProperties(
            notifications = NotificationsConfig(
                admin = NotificationsConfig.AdminConfig(enabled = false)
            )
        )
        val disabledListener = AdminNotificationListener(channel, disabledProps)

        val event = UserSignedUpEvent(
            userId = 1L,
            name = "Test",
            email = "test@example.com",
            provider = "apple"
        )

        disabledListener.onUserSignedUp(event)

        verify(exactly = 0) { channel.send(any(), any()) }
    }

    @Test
    fun `should not propagate channel exceptions`() {
        val failingChannel = mockk<NotificationChannel>()
        val listener = AdminNotificationListener(
            failingChannel,
            AppProperties(
                notifications = NotificationsConfig(
                    admin = NotificationsConfig.AdminConfig(enabled = true)
                )
            )
        )

        io.mockk.every { failingChannel.send(any(), any()) } throws RuntimeException("channel down")

        val event = UserSignedUpEvent(
            userId = 1L,
            name = "Test",
            email = "test@example.com",
            provider = "google"
        )

        // Must not throw
        listener.onUserSignedUp(event)
    }
}
