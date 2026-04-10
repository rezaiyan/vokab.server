package com.alirezaiyan.vokab.server.service.notification

/**
 * Abstraction over admin notification delivery.
 * Implementations: Telegram, Slack, email, etc.
 */
interface NotificationChannel {
    fun send(title: String, body: String)
}
