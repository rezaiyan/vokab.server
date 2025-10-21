package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.*
import com.alirezaiyan.vokab.server.service.AuthService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {
    
    @PostMapping("/google")
    fun authenticateWithGoogle(
        @Valid @RequestBody request: GoogleAuthRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        return try {
            val response = authService.authenticateWithGoogle(request.idToken)
            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error(e) { "Google authentication failed" }
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse(success = false, message = "Authentication failed: ${e.message}"))
        }
    }
    
    @PostMapping("/apple")
    fun authenticateWithApple(
        @Valid @RequestBody request: AppleAuthRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        return try {
            val response = authService.authenticateWithApple(request.idToken, request.fullName)
            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error(e) { "Apple authentication failed" }
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse(success = false, message = "Authentication failed: ${e.message}"))
        }
    }
    
    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        return try {
            val response = authService.refreshAccessToken(request.refreshToken)
            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error(e) { "Token refresh failed" }
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse(success = false, message = "Token refresh failed: ${e.message}"))
        }
    }
    
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            authService.logout(user.id!!, request.refreshToken)
            ResponseEntity.ok(ApiResponse(success = true, message = "Logged out successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Logout failed" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Logout failed: ${e.message}"))
        }
    }
    
    @PostMapping("/logout-all")
    fun logoutAll(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            authService.logoutAll(user.id!!)
            ResponseEntity.ok(ApiResponse(success = true, message = "All sessions logged out successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Logout all failed" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Logout all failed: ${e.message}"))
        }
    }
}

