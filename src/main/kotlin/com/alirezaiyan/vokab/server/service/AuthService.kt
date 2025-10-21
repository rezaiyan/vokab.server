package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.RefreshToken
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.RefreshTokenRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.AuthResponse
import com.alirezaiyan.vokab.server.presentation.dto.UserDto
import com.alirezaiyan.vokab.server.security.JwtTokenProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date

private val logger = KotlinLogging.logger {}

/**
 * Service responsible for user authentication and token management.
 * Uses Firebase ID tokens for Google Sign-In authentication.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val applePublicKeyService: ApplePublicKeyService
) {
    
    /**
     * Authenticates a user using a Firebase ID token from Google Sign-In.
     * Creates a new user if one doesn't exist, or updates existing user's last login time.
     * 
     * @param idToken Firebase ID token from client
     * @return AuthResponse containing JWT tokens and user data
     * @throws IllegalArgumentException if token is invalid or email is missing
     */
    @Transactional
    fun authenticateWithGoogle(idToken: String): AuthResponse {
        val firebaseToken = verifyFirebaseToken(idToken)
            ?: throw IllegalArgumentException("Invalid Firebase ID token")
        
        val email = firebaseToken.email 
            ?: throw IllegalArgumentException("Email not found in Firebase token")
        
        logger.info { "Authenticating user: $email" }
        
        val user = findOrCreateUser(firebaseToken)
        val savedUser = userRepository.save(user)
        
        val tokens = generateTokenPair(savedUser)
        saveRefreshToken(tokens.refreshToken, savedUser)
        
        logger.info { "✅ User authenticated: ${savedUser.email}" }
        
        return tokens
    }
    
    /**
     * Authenticates a user using an Apple ID token from Sign in with Apple.
     * Creates a new user if one doesn't exist, or updates existing user's last login time.
     * 
     * @param idToken Apple ID token (JWT) from client
     * @param fullName Optional full name from first-time Apple Sign In
     * @return AuthResponse containing JWT tokens and user data
     * @throws IllegalArgumentException if token is invalid or missing required claims
     */
    @Transactional
    fun authenticateWithApple(idToken: String, fullName: String?): AuthResponse {
        val appleToken = verifyAppleToken(idToken)
            ?: throw IllegalArgumentException("Invalid Apple ID token")
        
        val appleId = appleToken["sub"] as? String
            ?: throw IllegalArgumentException("Apple ID (sub) not found in token")
        
        val email = appleToken["email"] as? String
            ?: throw IllegalArgumentException("Email not found in Apple token")
        
        logger.info { "Authenticating user with Apple: $email" }
        
        val user = findOrCreateAppleUser(appleId, email, fullName)
        val savedUser = userRepository.save(user)
        
        val tokens = generateTokenPair(savedUser)
        saveRefreshToken(tokens.refreshToken, savedUser)
        
        logger.info { "✅ User authenticated with Apple: ${savedUser.email}" }
        
        return tokens
    }
    
    /**
     * Finds an existing user or creates a new one from Firebase token data.
     */
    private fun findOrCreateUser(firebaseToken: FirebaseToken): User {
        val googleId = firebaseToken.uid
        val email = firebaseToken.email!!
        val name = firebaseToken.name ?: email
        val profileImageUrl = firebaseToken.picture
        
        return userRepository.findByGoogleId(googleId)
            .map { existingUser ->
                // Update last login time
                existingUser.copy(
                    lastLoginAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            }
            .orElseGet {
                // Check if user exists with this email (account linking)
                userRepository.findByEmail(email)
                    .map { existingUser ->
                        logger.info { "Linking Google account to existing user: $email" }
                        existingUser.copy(
                            googleId = googleId,
                            name = name,
                            profileImageUrl = profileImageUrl ?: existingUser.profileImageUrl,
                            lastLoginAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    }
                    .orElseGet {
                        // Create new user
                        logger.info { "Creating new user: $email" }
                        User(
                            email = email,
                            name = name,
                            googleId = googleId,
                            profileImageUrl = profileImageUrl,
                            lastLoginAt = Instant.now()
                        )
                    }
            }
    }
    
    /**
     * Finds an existing user or creates a new one from Apple Sign In data.
     */
    private fun findOrCreateAppleUser(appleId: String, email: String, fullName: String?): User {
        val name = fullName ?: email.substringBefore("@")
        
        return userRepository.findByAppleId(appleId)
            .map { existingUser ->
                // Update last login time
                existingUser.copy(
                    lastLoginAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            }
            .orElseGet {
                // Check if user exists with this email (account linking)
                userRepository.findByEmail(email)
                    .map { existingUser ->
                        logger.info { "Linking Apple account to existing user: $email" }
                        existingUser.copy(
                            appleId = appleId,
                            name = if (fullName != null) fullName else existingUser.name,
                            lastLoginAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    }
                    .orElseGet {
                        // Create new user
                        logger.info { "Creating new user with Apple: $email" }
                        User(
                            email = email,
                            name = name,
                            appleId = appleId,
                            lastLoginAt = Instant.now()
                        )
                    }
            }
    }
    
    /**
     * Generates access and refresh JWT tokens for a user.
     */
    private fun generateTokenPair(user: User): AuthResponse {
        val accessToken = jwtTokenProvider.generateAccessToken(user.id!!, user.email)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
        
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtTokenProvider.getExpirationTime(),
            user = user.toDto()
        )
    }
    
    /**
     * Saves a refresh token to the database.
     */
    private fun saveRefreshToken(token: String, user: User) {
        val refreshTokenEntity = RefreshToken(
            token = token,
            user = user,
            expiresAt = jwtTokenProvider.getRefreshTokenExpiryDate()
        )
        refreshTokenRepository.save(refreshTokenEntity)
    }
    
    /**
     * Refreshes an access token using a valid refresh token.
     * 
     * @param refreshToken The refresh token to use
     * @return AuthResponse with new access token and same refresh token
     * @throws IllegalArgumentException if refresh token is invalid, revoked, or expired
     */
    @Transactional
    fun refreshAccessToken(refreshToken: String): AuthResponse {
        validateRefreshToken(refreshToken)
        
        val tokenEntity = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow { IllegalArgumentException("Refresh token not found") }
        
        validateTokenEntity(tokenEntity)
        
        val user = tokenEntity.user
        val newAccessToken = jwtTokenProvider.generateAccessToken(user.id!!, user.email)
        
        logger.info { "✅ Access token refreshed: ${user.email}" }
        
        return AuthResponse(
            accessToken = newAccessToken,
            refreshToken = refreshToken,
            expiresIn = jwtTokenProvider.getExpirationTime(),
            user = user.toDto()
        )
    }
    
    /**
     * Validates the JWT signature and format of a refresh token.
     */
    private fun validateRefreshToken(token: String) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw IllegalArgumentException("Invalid refresh token")
        }
    }
    
    /**
     * Validates that a refresh token entity is not revoked or expired.
     */
    private fun validateTokenEntity(tokenEntity: RefreshToken) {
        if (tokenEntity.revoked) {
            throw IllegalArgumentException("Refresh token has been revoked")
        }
        
        if (tokenEntity.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Refresh token has expired")
        }
    }
    
    /**
     * Logs out a user by revoking their refresh token.
     * 
     * @param userId The ID of the user logging out
     * @param refreshToken The refresh token to revoke
     */
    @Transactional
    fun logout(userId: Long, refreshToken: String) {
        logger.info { "Logging out user: $userId" }
        refreshTokenRepository.revokeByToken(refreshToken)
        logger.debug { "✅ Refresh token revoked for user: $userId" }
    }
    
    /**
     * Logs out a user from all devices by revoking all their refresh tokens.
     * 
     * @param userId The ID of the user to log out from all sessions
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    fun logoutAll(userId: Long) {
        logger.info { "Logging out all sessions for user: $userId" }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        refreshTokenRepository.revokeAllByUser(user)
        logger.debug { "✅ All refresh tokens revoked for user: $userId" }
    }
    
    /**
     * Deletes a user account permanently.
     * This includes:
     * - Revoking all refresh tokens
     * - Deleting all user data
     * - Removing the user from the database
     * 
     * @param userId The ID of the user to delete
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    fun deleteAccount(userId: Long) {
        logger.info { "Deleting account for user: $userId" }
        
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        logger.info { "Deleting account for email: ${user.email}" }
        
        // First revoke all refresh tokens
        refreshTokenRepository.revokeAllByUser(user)
        logger.debug { "✅ All refresh tokens revoked for user: $userId" }
        
        // Delete the user (cascade will handle related entities)
        userRepository.delete(user)
        logger.info { "✅ Account deleted successfully for user: $userId (${user.email})" }
    }
    
    /**
     * Verifies a Firebase ID token using Firebase Admin SDK.
     * The token is issued by Firebase Authentication and signed by Google's servers.
     * 
     * @param idTokenString The Firebase ID token to verify
     * @return Decoded FirebaseToken if valid, null if invalid
     */
    private fun verifyFirebaseToken(idTokenString: String): FirebaseToken? {
        return try {
            logger.debug { "Verifying Firebase ID token" }
            
            val decodedToken = FirebaseAuth.getInstance().verifyIdToken(idTokenString)
            
            logger.debug { "✅ Token verified - UID: ${decodedToken.uid}, Email: ${decodedToken.email}" }
            decodedToken
            
        } catch (e: Exception) {
            logger.error(e) { "❌ Firebase token verification failed: ${e.message}" }
            null
        }
    }
    
    /**
     * Verifies an Apple ID token (JWT) with full signature verification.
     * Apple ID tokens are JWTs signed by Apple using RS256.
     * This implementation verifies the signature using Apple's public keys.
     * 
     * @param idTokenString The Apple ID token (JWT) to verify
     * @return Map of token claims if valid, null if invalid
     */
    private fun verifyAppleToken(idTokenString: String): Map<String, Any>? {
        return try {
            logger.debug { "Verifying Apple ID token with signature verification" }
            
            // Parse JWT header to get the key ID (kid)
            val parts = idTokenString.split(".")
            if (parts.size != 3) {
                logger.error { "Invalid JWT format - expected 3 parts, got ${parts.size}" }
                return null
            }
            
            // Decode header to get kid
            val headerJson = String(java.util.Base64.getUrlDecoder().decode(parts[0]))
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val header = objectMapper.readValue(headerJson, Map::class.java) as Map<String, Any>
            
            val kid = header["kid"] as? String
            if (kid == null) {
                logger.error { "Missing 'kid' in JWT header" }
                return null
            }
            
            logger.debug { "Apple token kid: $kid" }
            
            // Get the public key for this kid
            val publicKey = applePublicKeyService.getPublicKey(kid)
            if (publicKey == null) {
                logger.error { "Could not get public key for kid: $kid" }
                return null
            }
            
            // Verify the JWT signature using JJWT library
            val jwtParser = io.jsonwebtoken.Jwts.parser()
                .verifyWith(publicKey as java.security.interfaces.RSAPublicKey)
                .build()
            
            val jwt = jwtParser.parseSignedClaims(idTokenString)
            val claims = jwt.payload
            
            // Validate issuer
            val iss = claims.issuer
            if (iss != "https://appleid.apple.com") {
                logger.error { "Invalid issuer: $iss (expected https://appleid.apple.com)" }
                return null
            }
            
            // Validate expiration (JJWT already checks this, but we log it)
            val exp = claims.expiration
            if (exp.before(Date())) {
                logger.error { "Token expired at: $exp" }
                return null
            }
            
            // Convert claims to map
            val claimsMap = mutableMapOf<String, Any>()
            claimsMap["sub"] = claims.subject ?: ""
            claimsMap["email"] = claims["email"] ?: ""
            claimsMap["email_verified"] = claims["email_verified"] ?: false
            claimsMap["is_private_email"] = claims["is_private_email"] ?: false
            claimsMap["iss"] = claims.issuer
            claimsMap["aud"] = claims.audience.firstOrNull() ?: ""
            claimsMap["exp"] = claims.expiration.time / 1000
            claimsMap["iat"] = claims.issuedAt?.time?.div(1000) ?: 0
            
            logger.info { "✅ Apple token signature verified - Sub: ${claims.subject}, Email: ${claims["email"]}" }
            
            claimsMap
            
        } catch (e: io.jsonwebtoken.security.SignatureException) {
            logger.error(e) { "❌ Apple token signature verification failed - invalid signature" }
            null
        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            logger.error(e) { "❌ Apple token expired" }
            null
        } catch (e: io.jsonwebtoken.JwtException) {
            logger.error(e) { "❌ Apple token verification failed - JWT error: ${e.message}" }
            null
        } catch (e: Exception) {
            logger.error(e) { "❌ Apple token verification failed: ${e.message}" }
            null
        }
    }
    
    /**
     * Converts a User entity to a UserDto for API responses.
     */
    private fun User.toDto(): UserDto = UserDto(
        id = this.id!!,
        email = this.email,
        name = this.name,
        profileImageUrl = this.profileImageUrl,
        subscriptionStatus = this.subscriptionStatus,
        subscriptionExpiresAt = this.subscriptionExpiresAt?.toString(),
        currentStreak = this.currentStreak,
        longestStreak = this.longestStreak
    )
}

