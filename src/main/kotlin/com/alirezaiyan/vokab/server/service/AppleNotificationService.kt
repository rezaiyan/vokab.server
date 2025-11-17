package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.NotificationCategory
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.*
import com.alirezaiyan.vokab.server.service.push.PushNotificationService
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service to handle Apple Server-to-Server notifications
 * These notifications inform us of changes to user Apple accounts
 * 
 * Reference: https://developer.apple.com/documentation/sign_in_with_apple/processing_changes_for_sign_in_with_apple_accounts
 */
@Service
class AppleNotificationService(
    private val userRepository: UserRepository,
    private val applePublicKeyService: ApplePublicKeyService,
    private val objectMapper: ObjectMapper,
    private val pushNotificationService: PushNotificationService
) {
    
    /**
     * Process incoming Apple server notification
     * The notification is a JWT signed by Apple that must be verified
     */
    @Transactional
    fun processNotification(notificationPayload: String): Boolean {
        try {
            logger.info { "Processing Apple server-to-server notification" }
            
            // Verify and decode the JWT payload
            val claims = verifyAppleNotificationToken(notificationPayload)
            if (claims == null) {
                logger.error { "Failed to verify Apple notification token" }
                return false
            }
            
            // Parse the events
            val eventsJson = objectMapper.writeValueAsString(claims["events"])
            val events = objectMapper.readValue(eventsJson, AppleNotificationEvents::class.java)
            
            val appleUserId = events.sub
            logger.info { "Processing event type: ${events.type} for user: $appleUserId" }
            
            // Find user by Apple ID
            val userOptional = userRepository.findByAppleId(appleUserId)
            
            if (userOptional.isEmpty) {
                logger.warn { "Received notification for unknown Apple user: $appleUserId" }
                return true // Still return success to Apple
            }
            
            val user = userOptional.get()
            
            // Handle different event types
            when (events.type) {
                AppleNotificationEventType.EMAIL_DISABLED -> handleEmailDisabled(user, events.emailDisabled)
                AppleNotificationEventType.EMAIL_ENABLED -> handleEmailEnabled(user, events.emailEnabled)
                AppleNotificationEventType.CONSENT_REVOKED -> handleConsentRevoked(user, events.consentRevoked)
                AppleNotificationEventType.ACCOUNT_DELETE -> handleAccountDelete(user, events.accountDelete)
            }
            
            return true
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to process Apple notification: ${e.message}" }
            return false
        }
    }
    
    /**
     * Verify Apple notification JWT using Apple's public keys
     */
    private fun verifyAppleNotificationToken(tokenString: String): Map<String, Any>? {
        return try {
            // Parse header to get kid
            val parts = tokenString.split(".")
            if (parts.size != 3) {
                logger.error { "Invalid JWT format" }
                return null
            }
            
            val headerJson = String(java.util.Base64.getUrlDecoder().decode(parts[0]))
            val header = objectMapper.readValue(headerJson, Map::class.java) as Map<String, Any>
            
            val kid = header["kid"] as? String ?: return null
            
            // Get public key
            val publicKey = applePublicKeyService.getPublicKey(kid)
            if (publicKey == null) {
                logger.error { "Could not get public key for kid: $kid" }
                return null
            }
            
            // Verify signature
            val jwtParser = io.jsonwebtoken.Jwts.parser()
                .verifyWith(publicKey as java.security.interfaces.RSAPublicKey)
                .build()
            
            val jwt = jwtParser.parseSignedClaims(tokenString)
            val claims = jwt.payload
            
            // Validate issuer
            if (claims.issuer != "https://appleid.apple.com") {
                logger.error { "Invalid issuer: ${claims.issuer}" }
                return null
            }
            
            // Convert to map
            val claimsMap = mutableMapOf<String, Any>()
            claims.forEach { (key, value) ->
                claimsMap[key] = value
            }
            
            logger.info { "✅ Apple notification token verified" }
            claimsMap
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to verify Apple notification token" }
            null
        }
    }
    
    /**
     * Handle email-disabled event
     * User stopped sharing their email or switched to private relay
     */
    private fun handleEmailDisabled(user: com.alirezaiyan.vokab.server.domain.entity.User, event: EmailDisabledEvent?) {
        logger.info { "Email disabled for user: ${user.email}" }
        
        if (event != null) {
            logger.info { "  Previous email: ${event.email}" }
            logger.info { "  Was private relay: ${event.is_private_email}" }
        }
        
        // Update user record to note the change
        val updatedUser = user.copy(
            updatedAt = Instant.now()
            // Note: We keep the original email for account continuity
            // Apple will provide the new relay email on next sign-in
        )
        userRepository.save(updatedUser)
        
        logger.info { "✅ Processed email-disabled event for user: ${user.id}" }
    }
    
    /**
     * Handle email-enabled event
     * User started sharing their real email
     */
    private fun handleEmailEnabled(user: com.alirezaiyan.vokab.server.domain.entity.User, event: EmailEnabledEvent?) {
        logger.info { "Email enabled for user: ${user.email}" }
        
        if (event != null) {
            logger.info { "  New email: ${event.email}" }
            logger.info { "  Is private relay: ${event.is_private_email}" }
            
            // Update user with the new email
            val updatedUser = user.copy(
                email = event.email,
                updatedAt = Instant.now()
            )
            userRepository.save(updatedUser)
            logger.info { "✅ Updated user email to: ${event.email}" }
        }
    }
    
    /**
     * Handle consent-revoked event
     * User revoked app's access - we should deactivate their account
     */
    private fun handleConsentRevoked(user: com.alirezaiyan.vokab.server.domain.entity.User, event: ConsentRevokedEvent?) {
        logger.info { "Consent revoked for user: ${user.email}" }
        logger.info { "  Reason: ${event?.reason ?: "Not provided"}" }
        
        // CRITICAL FIX: Send push notification to all devices before deactivating account
        // This ensures all devices are notified that the account access has been revoked
        try {
            val notificationResults = pushNotificationService.sendNotificationToUser(
                userId = user.id!!,
                title = "Account Access Revoked",
                body = "Your account access has been revoked. Please sign in again.",
                data = mapOf(
                    "type" to "sign_out",
                    "action" to "clear_local_data",
                    "reason" to "consent_revoked"
                ),
                category = NotificationCategory.SYSTEM
            )
            logger.info { "Sent consent revocation notification to ${notificationResults.size} devices" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send consent revocation notification, continuing with deactivation" }
        }
        
        // Mark user as inactive but don't delete data
        val updatedUser = user.copy(
            active = false,
            updatedAt = Instant.now()
        )
        userRepository.save(updatedUser)
        
        logger.info { "✅ Deactivated user account: ${user.id}" }
    }
    
    /**
     * Handle account-delete event
     * User permanently deleted their Apple ID
     * According to Apple's guidelines, we should delete user data
     */
    private fun handleAccountDelete(user: com.alirezaiyan.vokab.server.domain.entity.User, event: AccountDeleteEvent?) {
        logger.info { "Account deletion requested for user: ${user.email}" }
        logger.info { "  Reason: ${event?.reason ?: "Not provided"}" }
        
        // CRITICAL FIX: Send push notification to all devices before deleting account
        // This ensures all devices are notified that the account is being deleted
        try {
            val notificationResults = pushNotificationService.sendNotificationToUser(
                userId = user.id!!,
                title = "Account Deleted",
                body = "Your account has been permanently deleted. Please restart the app.",
                data = mapOf(
                    "type" to "account_deleted",
                    "action" to "clear_local_data",
                    "clear_daily_insights" to "true"
                ),
                category = NotificationCategory.SYSTEM
            )
            logger.info { "Sent Apple account deletion notification to ${notificationResults.size} devices" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send Apple account deletion notification, continuing with deletion" }
        }
        
        // Option 1: Soft delete - mark as deleted but keep data for compliance
        val deletedUser = user.copy(
            active = false,
            email = "deleted_${user.id}@apple.deleted",
            name = "Deleted User",
            appleId = null,
            updatedAt = Instant.now()
        )
        userRepository.save(deletedUser)
        
        // Option 2: Hard delete (if required by your privacy policy)
        // userRepository.delete(user)
        
        logger.info { "✅ Processed account deletion for user: ${user.id}" }
        logger.warn { "⚠️  Consider hard delete if required by your privacy policy" }
    }
}