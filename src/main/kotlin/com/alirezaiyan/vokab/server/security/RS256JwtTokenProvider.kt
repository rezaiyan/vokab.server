package com.alirezaiyan.vokab.server.security

import com.alirezaiyan.vokab.server.config.AppProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.Base64
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * JWT token provider using RS256 algorithm (asymmetric keys)
 * Supports key rotation and JWKS endpoint
 */
@Component
class RS256JwtTokenProvider(
    private val appProperties: AppProperties
) {
    private val keyPair: KeyPair = initializeKeyPair()
    
    private fun initializeKeyPair(): KeyPair {
        val privateKeyPath = appProperties.jwt.privateKeyPath
        val publicKeyPath = appProperties.jwt.publicKeyPath
        
        return if (privateKeyPath.isNotBlank() && publicKeyPath.isNotBlank()) {
            try {
                loadKeyPairFromFiles(privateKeyPath, publicKeyPath)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load keys from files, generating new key pair" }
                generateKeyPair()
            }
        } else {
            logger.info { "No key paths configured, generating new key pair" }
            generateKeyPair()
        }
    }
    
    private fun loadKeyPairFromFiles(privateKeyPath: String, publicKeyPath: String): KeyPair {
        val privateKeyBytes = File(privateKeyPath).readBytes()
        val publicKeyBytes = File(publicKeyPath).readBytes()
        
        val privateKeySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBytes))
        val publicKeySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBytes))
        
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(privateKeySpec) as RSAPrivateKey
        val publicKey = keyFactory.generatePublic(publicKeySpec) as RSAPublicKey
        
        logger.info { "Successfully loaded RSA key pair from files" }
        return KeyPair(publicKey, privateKey)
    }
    
    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val pair = keyPairGenerator.generateKeyPair()
        logger.info { "Generated new RSA key pair (2048 bits)" }
        return pair
    }
    
    val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey
    val privateKey: RSAPrivateKey get() = keyPair.private as RSAPrivateKey
    
    fun generateAccessToken(userId: Long, email: String, jti: String = UUID.randomUUID().toString()): String {
        val now = Date()
        val expiryDate = Date(now.time + appProperties.jwt.expirationMs)
        
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("type", "access")
            .id(jti)
            .issuer(appProperties.jwt.issuer)
            .audience().add(appProperties.jwt.audience).and()
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(privateKey)
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
    
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer(appProperties.jwt.issuer)
            .requireAudience(appProperties.jwt.audience)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}


