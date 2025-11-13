package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.config.OpenRouterConfig
import com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class OpenRouterServiceStreakReminderTest {

    private lateinit var appProperties: AppProperties
    private lateinit var openRouterService: OpenRouterService

    @BeforeEach
    fun setUp() {
        appProperties = AppProperties()
        appProperties.openrouter = OpenRouterConfig(
            apiKey = "test-api-key",
            baseUrl = "https://openrouter.ai/api/v1"
        )

        openRouterService = OpenRouterService(appProperties)
    }

    @Test
    fun `generateStreakReminderMessage should return Mono with message`() {
        val progressStats = ProgressStatsDto(
            totalWords = 100,
            dueCards = 5,
            level0Count = 10,
            level1Count = 20,
            level2Count = 15,
            level3Count = 15,
            level4Count = 15,
            level5Count = 15,
            level6Count = 10
        )

        openRouterService.generateStreakReminderMessage(
            currentStreak = 5,
            userName = "Test User",
            progressStats = progressStats
        )
    }

    @Test
    fun `generateStreakReminderMessage should work without progress stats`() {
        openRouterService.generateStreakReminderMessage(
            currentStreak = 10,
            userName = "Test User",
            progressStats = null
        )
    }
}

