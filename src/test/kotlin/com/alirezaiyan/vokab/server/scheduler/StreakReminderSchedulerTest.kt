package com.alirezaiyan.vokab.server.scheduler

import com.alirezaiyan.vokab.server.service.StreakReminderService
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StreakReminderSchedulerTest {

    private lateinit var streakReminderService: StreakReminderService
    private lateinit var streakReminderScheduler: StreakReminderScheduler

    @BeforeEach
    fun setUp() {
        streakReminderService = mockk()
        streakReminderScheduler = StreakReminderScheduler(streakReminderService)
    }

    @Test
    fun `sendStreakReminders should call sendReminderNotifications`() {
        every { streakReminderService.sendReminderNotifications() } just runs

        streakReminderScheduler.sendStreakReminders()

        verify(exactly = 1) { streakReminderService.sendReminderNotifications() }
    }

    @Test
    fun `sendStreakReminders should handle exceptions gracefully`() {
        every { streakReminderService.sendReminderNotifications() } throws RuntimeException("Service error")

        streakReminderScheduler.sendStreakReminders()

        verify(exactly = 1) { streakReminderService.sendReminderNotifications() }
    }
}




