package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.StreakResponse
import com.alirezaiyan.vokab.server.security.RS256JwtTokenProvider
import com.alirezaiyan.vokab.server.service.StreakService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/streak")
class StreakController(
    private val streakService: StreakService,
    private val jwtTokenProvider: RS256JwtTokenProvider
) {
    
    /**
     * Record user activity (called when review section opens)
     * POST /api/v1/streak/record
     */
    @PostMapping("/record")
    fun recordActivity(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<ApiResponse<StreakResponse>> {
        return try {
            val token = authorization.removePrefix("Bearer ")
            val userId = jwtTokenProvider.getUserIdFromToken(token)
                ?: throw IllegalArgumentException("Invalid token")
            
            logger.debug { "Recording activity for user: $userId" }
            
            val user = streakService.recordActivity(userId)
            
            val response = StreakResponse(
                currentStreak = user.currentStreak,
                longestStreak = user.longestStreak
            )
            
            ResponseEntity.ok(ApiResponse(success = true, data = response, message = "Activity recorded successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to record activity" }
            ResponseEntity.badRequest().body(
                ApiResponse<StreakResponse>(success = false, message = "Failed to record activity: ${e.message}")
            )
        }
    }
    
    /**
     * Get user's current streak
     * GET /api/v1/streak
     */
    @GetMapping
    fun getStreak(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<ApiResponse<StreakResponse>> {
        return try {
            val token = authorization.removePrefix("Bearer ")
            val userId = jwtTokenProvider.getUserIdFromToken(token)
                ?: throw IllegalArgumentException("Invalid token")
            
            val streakInfo = streakService.getUserStreak(userId)
            
            val response = StreakResponse(
                currentStreak = streakInfo.currentStreak,
                longestStreak = streakInfo.longestStreak
            )
            
            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get streak" }
            ResponseEntity.badRequest().body(
                ApiResponse<StreakResponse>(success = false, message = "Failed to get streak: ${e.message}")
            )
        }
    }
}


