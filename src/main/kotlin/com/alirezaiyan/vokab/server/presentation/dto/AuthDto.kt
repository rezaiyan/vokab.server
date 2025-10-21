package com.alirezaiyan.vokab.server.presentation.dto

import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class GoogleAuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String
)

data class AppleAuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String,
    val authorizationCode: String? = null,
    val fullName: String? = null
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserDto
)

data class UserDto(
    val id: Long,
    val email: String,
    val name: String,
    val profileImageUrl: String?,
    val subscriptionStatus: SubscriptionStatus,
    val subscriptionExpiresAt: String?,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0
)

