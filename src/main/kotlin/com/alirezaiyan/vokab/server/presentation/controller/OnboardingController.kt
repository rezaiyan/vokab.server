package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.config.RateLimitConfig
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.SuggestVocabularyRequest
import com.alirezaiyan.vokab.server.presentation.dto.SuggestVocabularyResponse
import com.alirezaiyan.vokab.server.service.OpenRouterService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

/**
 * Public onboarding API: submit language preferences and receive suggested vocabulary.
 * No authentication required. Rate limited by IP.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController(
    private val openRouterService: OpenRouterService,
    private val rateLimitConfig: RateLimitConfig
) {

    /**
     * POST /api/v1/onboarding/preferences
     * Body: { "targetLanguage": "German", "currentLevel": "beginner", "nativeLanguage": "English" }
     * Returns: { "success": true, "data": { "targetLanguage", "nativeLanguage", "currentLevel", "items": [...] } }
     * Items are up to 100 vocabulary entries (originalWord, translation, description) for the user to review and import.
     */
    @PostMapping("/preferences")
    fun submitPreferences(
        @Valid @RequestBody request: SuggestVocabularyRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<SuggestVocabularyResponse>> {
        val clientIp = getClientIp(httpRequest)
        logger.info { "Onboarding preferences from $clientIp: target=${request.targetLanguage}, level=${request.currentLevel}, native=${request.nativeLanguage}" }

        val bucket = rateLimitConfig.getOnboardingBucket(clientIp)
        if (!bucket.tryConsume(1)) {
            logger.warn { "Onboarding rate limit exceeded for IP $clientIp" }
            return ResponseEntity.status(429)
                .body(ApiResponse(success = false, message = "Rate limit exceeded. Please try again in a minute."))
        }

        return try {
            val items = openRouterService.generateVocabularyFromPreferences(
                targetLanguage = request.targetLanguage.trim(),
                currentLevel = request.currentLevel.trim(),
                nativeLanguage = request.nativeLanguage.trim()
            ).block() ?: emptyList()

            val response = SuggestVocabularyResponse(
                targetLanguage = request.targetLanguage.trim(),
                nativeLanguage = request.nativeLanguage.trim(),
                currentLevel = request.currentLevel.trim(),
                items = items
            )
            logger.info { "Onboarding: returning ${items.size} vocabulary items to $clientIp" }
            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (error: Exception) {
            logger.error(error) { "Onboarding vocabulary generation failed for $clientIp" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = error.message ?: "Failed to generate vocabulary"))
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").firstOrNull()?.trim() ?: request.remoteAddr
        } else {
            request.remoteAddr ?: "unknown"
        }
    }
}
