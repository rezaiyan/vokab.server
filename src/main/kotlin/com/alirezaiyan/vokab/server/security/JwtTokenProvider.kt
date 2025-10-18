package com.alirezaiyan.vokab.server.security

import com.alirezaiyan.vokab.server.config.AppProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

private val logger = KotlinLogging.logger {}

@Component
class JwtTokenProvider(
    private val appProperties: AppProperties
) {
    private val secretKey: SecretKey = run {
        val rawSecret = appProperties.jwt.secret
        val secretBytes = if (rawSecret.startsWith("base64:")) {
            // Allow base64-encoded secrets when prefixed with 'base64:'
            Base64.getDecoder().decode(rawSecret.removePrefix("base64:"))
        } else {
            rawSecret.toByteArray()
        }

        require(secretBytes.size >= 32) {
            "JWT_SECRET must be at least 256 bits (32 bytes). Provide a longer secret or a base64-encoded key prefixed with 'base64:'."
        }

        Keys.hmacShaKeyFor(secretBytes)
    }
    
    fun generateAccessToken(userId: Long, email: String): String {
        val now = Date()
        val expiryDate = Date(now.time + appProperties.jwt.expirationMs)
        
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }
    
    fun generateRefreshToken(userId: Long): String {
        val now = Date()
        val expiryDate = Date(now.time + appProperties.jwt.refreshExpirationMs)
        
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }
    
    fun getUserIdFromToken(token: String): Long? {
        return try {
            val claims = getClaims(token)
            claims.subject.toLong()
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract user ID from token" }
            null
        }
    }
    
    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (e: Exception) {
            logger.debug(e) { "Token validation failed" }
            false
        }
    }
    
    fun getExpirationTime(): Long = appProperties.jwt.expirationMs
    
    fun getRefreshExpirationTime(): Long = appProperties.jwt.refreshExpirationMs
    
    fun getRefreshTokenExpiryDate(): Instant {
        return Instant.now().plusMillis(appProperties.jwt.refreshExpirationMs)
    }
    
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}

