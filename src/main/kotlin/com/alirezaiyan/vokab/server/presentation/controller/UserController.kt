package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.UserDto
import com.alirezaiyan.vokab.server.service.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val featureAccessService: com.alirezaiyan.vokab.server.service.FeatureAccessService
) {
    
    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<UserDto>> {
        return try {
            val userDto = userService.getUserById(user.id!!)
            ResponseEntity.ok(ApiResponse(success = true, data = userDto))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get current user" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to get user: ${e.message}"))
        }
    }
    
    @PatchMapping("/me")
    fun updateCurrentUser(
        @AuthenticationPrincipal user: User,
        @RequestParam(required = false) name: String?
    ): ResponseEntity<ApiResponse<UserDto>> {
        return try {
            val updated = userService.updateUser(user.id!!, name)
            ResponseEntity.ok(ApiResponse(success = true, data = updated))
        } catch (e: Exception) {
            logger.error(e) { "Failed to update user" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to update user: ${e.message}"))
        }
    }
    
    @DeleteMapping("/me")
    fun deleteCurrentUser(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            userService.deleteUser(user.id!!)
            ResponseEntity.ok(ApiResponse(success = true, message = "User deleted successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete user" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to delete user: ${e.message}"))
        }
    }
    
    /**
     * Get feature flags and user's feature access
     * Returns both global feature flags and user-specific access permissions
     */
    @GetMapping("/feature-access")
    fun getFeatureAccess(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<com.alirezaiyan.vokab.server.presentation.dto.FeatureAccessResponse>> {
        return try {
            val featureFlags = featureAccessService.getClientFeatureFlags()
            val userAccess = featureAccessService.getUserFeatureAccess(user)
            
            val response = com.alirezaiyan.vokab.server.presentation.dto.FeatureAccessResponse(
                featureFlags = featureFlags,
                userAccess = userAccess
            )
            
            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get feature access" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to get feature access: ${e.message}"))
        }
    }
    
    /**
     * Get global feature flags (public endpoint - no auth required)
     */
    @GetMapping("/feature-flags")
    fun getFeatureFlags(): ResponseEntity<ApiResponse<com.alirezaiyan.vokab.server.service.ClientFeatureFlags>> {
        return try {
            val featureFlags = featureAccessService.getClientFeatureFlags()
            ResponseEntity.ok(ApiResponse(success = true, data = featureFlags))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get feature flags" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Failed to get feature flags: ${e.message}"))
        }
    }
}

