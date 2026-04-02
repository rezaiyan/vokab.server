package com.alirezaiyan.vokab.server.service.email

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

private val logger = KotlinLogging.logger {}

/**
 * Email provider using Resend (resend.com).
 * Activated when app.email.provider=resend and API key is set.
 */
@Component
@ConditionalOnProperty(name = ["app.email.provider"], havingValue = "resend")
class ResendEmailProvider(
    private val emailConfig: EmailConfig
) : EmailProvider {

    override val name = "resend"

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl("https://api.resend.com")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${emailConfig.apiKey}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    override fun send(request: EmailSendRequest): EmailSendResult {
        val from = request.from ?: emailConfig.fromAddress
        val payload = mapOf(
            "from" to from,
            "to" to listOf(request.to),
            "subject" to request.subject,
            "html" to request.bodyHtml,
            "text" to (request.bodyText ?: ""),
            "reply_to" to (request.replyTo ?: emailConfig.replyTo)
        ).filterValues { it != null && it != "" }

        logger.info { "Sending email via Resend to ${request.to}: ${request.subject}" }

        val response = webClient.post()
            .uri("/emails")
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw RuntimeException("Empty response from Resend")

        val id = response["id"]?.toString()
            ?: throw RuntimeException("No message ID in Resend response: $response")

        logger.info { "Email sent via Resend: id=$id" }
        return EmailSendResult(providerId = id, provider = name)
    }
}
