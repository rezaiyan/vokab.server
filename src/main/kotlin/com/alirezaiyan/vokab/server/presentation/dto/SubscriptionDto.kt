package com.alirezaiyan.vokab.server.presentation.dto

import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus

data class SubscriptionDto(
    val id: Long,
    val productId: String,
    val status: SubscriptionStatus,
    val startedAt: String,
    val expiresAt: String?,
    val cancelledAt: String?,
    val isTrial: Boolean,
    val autoRenew: Boolean
)

data class RevenueCatWebhookEvent(
    val event: RevenueCatEvent,
    val api_version: String,
    val app_user_id: String,
    val product_id: String?,
    val purchased_at_ms: Long?,
    val expiration_at_ms: Long?,
    val is_trial_conversion: Boolean?,
    val cancellation_at_ms: Long?,
    val auto_resume_at_ms: Long?
)

data class RevenueCatEvent(
    val id: String,
    val type: String,
    val app_user_id: String,
    val aliases: List<String>?
)

