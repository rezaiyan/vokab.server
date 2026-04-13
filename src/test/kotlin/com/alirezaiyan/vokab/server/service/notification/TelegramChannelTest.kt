package com.alirezaiyan.vokab.server.service.notification

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.config.NotificationsConfig
import com.alirezaiyan.vokab.server.config.TelegramConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class TelegramChannelTest {

    private lateinit var webClient: WebClient
    private lateinit var webClientBuilder: WebClient.Builder
    private lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec
    private lateinit var requestBodySpec: WebClient.RequestBodySpec
    private lateinit var requestHeadersSpec: WebClient.RequestHeadersSpec<*>
    private lateinit var responseSpec: WebClient.ResponseSpec

    @BeforeEach
    fun setUp() {
        webClient = mockk()
        webClientBuilder = mockk()
        every { webClientBuilder.build() } returns webClient
        requestBodyUriSpec = mockk()
        requestBodySpec = mockk()
        requestHeadersSpec = mockk()
        responseSpec = mockk()
    }

    @Test
    fun `send should call Telegram Bot API with correct payload`() {
        val config = createConfig(botToken = "123:ABC", chatId = "87659200")
        val channel = TelegramChannel(config, webClientBuilder)

        stubWebClient()
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just("{\"ok\":true}")

        channel.send("Test Title", "Test body message")

        verify(exactly = 1) { webClient.post() }
    }

    @Test
    fun `send should be a no-op when bot token is blank`() {
        val config = createConfig(botToken = "", chatId = "87659200")
        val channel = TelegramChannel(config, webClientBuilder)

        channel.send("Title", "Body")

        verify(exactly = 0) { webClient.post() }
    }

    @Test
    fun `send should be a no-op when chat id is blank`() {
        val config = createConfig(botToken = "123:ABC", chatId = "")
        val channel = TelegramChannel(config, webClientBuilder)

        channel.send("Title", "Body")

        verify(exactly = 0) { webClient.post() }
    }

    @Test
    fun `send should not propagate exceptions`() {
        val config = createConfig(botToken = "123:ABC", chatId = "87659200")
        val channel = TelegramChannel(config, webClientBuilder)

        every { webClient.post() } throws RuntimeException("network error")

        // Must not throw
        channel.send("Title", "Body")
    }

    private fun stubWebClient() {
        every { webClient.post() } returns requestBodyUriSpec
        every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
        every { requestBodySpec.bodyValue(any()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
    }

    private fun createConfig(botToken: String, chatId: String) = AppProperties(
        notifications = NotificationsConfig(
            admin = NotificationsConfig.AdminConfig(
                enabled = true,
                telegram = TelegramConfig(
                    botToken = botToken,
                    chatId = chatId
                )
            )
        )
    )
}
