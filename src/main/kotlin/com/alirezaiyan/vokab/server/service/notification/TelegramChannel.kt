package com.alirezaiyan.vokab.server.service.notification

import com.alirezaiyan.vokab.server.config.AppProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

private val logger = KotlinLogging.logger {}

@Component
class TelegramChannel(
    private val appProperties: AppProperties,
    webClientBuilder: WebClient.Builder
) : NotificationChannel {

    private val webClient = webClientBuilder.build()

    override fun send(title: String, body: String) {
        val config = appProperties.notifications.admin.telegram
        if (config.botToken.isBlank() || config.chatId.isBlank()) {
            logger.debug { "Telegram notification skipped: bot token or chat ID not configured" }
            return
        }

        try {
            val text = "$title\n$body"
            webClient.post()
                .uri("https://api.telegram.org/bot${config.botToken}/sendMessage")
                .bodyValue(mapOf("chat_id" to config.chatId, "text" to text))
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
            logger.debug { "Telegram admin notification sent" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send Telegram admin notification" }
        }
    }
}
