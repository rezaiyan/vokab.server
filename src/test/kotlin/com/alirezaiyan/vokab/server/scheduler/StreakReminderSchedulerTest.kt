package com.alirezaiyan.vokab.server.scheduler

import com.alirezaiyan.vokab.server.service.SmartNotificationDispatcher
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StreakReminderSchedulerTest {

    private lateinit var smartNotificationDispatcher: SmartNotificationDispatcher
    private lateinit var streakReminderScheduler: StreakReminderScheduler

    @BeforeEach
    fun setUp() {
        smartNotificationDispatcher = mockk()
        streakReminderScheduler = StreakReminderScheduler(smartNotificationDispatcher)
    }

    @Test
    fun `sendStreakReminders should delegate to SmartNotificationDispatcher`() {
        every { smartNotificationDispatcher.dispatchForCurrentHour() } just runs

        streakReminderScheduler.sendStreakReminders()

        verify(exactly = 1) { smartNotificationDispatcher.dispatchForCurrentHour() }
    }

    @Test
    fun `sendStreakReminders should handle exceptions gracefully`() {
        every { smartNotificationDispatcher.dispatchForCurrentHour() } throws RuntimeException("Dispatch error")

        streakReminderScheduler.sendStreakReminders()

        verify(exactly = 1) { smartNotificationDispatcher.dispatchForCurrentHour() }
    }
}
