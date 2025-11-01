package com.alirezaiyan.vokab.server.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Service to fetch and cache Apple's public keys for JWT verification
 * Apple rotates keys periodically, so we cache them with TTL
 */
@Service
class ApplePublicKeyService {
    
    private val webClient = WebClient.create()
    private val objectMapper = ObjectMapper()
    private val publicKeysCache = ConcurrentHashMap<String, PublicKey>()
    private var lastFetchTime = 0L
    
    companion object {
        private const val APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Get Apple public key by kid (Key ID)
     * Fetches from Apple if not cached or cache is stale
     */
    fun getPublicKey(kid: String): PublicKey? {
        // Check if cache is stale
        if (System.currentTimeMillis() - lastFetchTime > CACHE_TTL_MS) {
            logger.info { "Apple public key cache is stale, refreshing..." }
            refreshPublicKeys()
        }
        
        // Try to get from cache
        var publicKey = publicKeysCache[kid]
        
        // If not in cache, try to fetch fresh keys
        if (publicKey == null) {
            logger.info { "Public key with kid=$kid not in cache, fetching fresh keys..." }
            refreshPublicKeys()
            publicKey = publicKeysCache[kid]
        }
        
        if (publicKey == null) {
            logger.error { "Public key with kid=$kid not found even after refresh" }
        }
        
        return publicKey
    }
    
    /**
     * Fetch public keys from Apple and update cache
     */
    @Synchronized
    private fun refreshPublicKeys() {
        try {
            logger.info { "Fetching Apple public keys from $APPLE_PUBLIC_KEYS_URL" }
            
            val response = webClient.get()
                .uri(APPLE_PUBLIC_KEYS_URL)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
            
            if (response == null) {
                logger.error { "Failed to fetch Apple public keys - empty response" }
                return
            }
            
            // Parse JSON response
            val jsonNode = objectMapper.readTree(response)
            val keysArray = jsonNode.get("keys")
            
            if (keysArray == null || !keysArray.isArray) {
                logger.error { "Invalid response format from Apple - missing 'keys' array" }
                return
            }
            
            // Clear old cache
            publicKeysCache.clear()
            
            // Parse each key
            keysArray.forEach { keyNode ->
                try {
                    val kid = keyNode.get("kid")?.asText()
                    val n = keyNode.get("n")?.asText() // Modulus
                    val e = keyNode.get("e")?.asText() // Exponent
                    val kty = keyNode.get("kty")?.asText()
                    val alg = keyNode.get("alg")?.asText()
                    
                    if (kid != null && n != null && e != null && kty == "RSA") {
                        val publicKey = createPublicKey(n, e)
                        publicKeysCache[kid] = publicKey
                        logger.debug { "Cached public key: kid=$kid, alg=$alg" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to parse key: ${keyNode}" }
                }
            }
            
            lastFetchTime = System.currentTimeMillis()
            logger.info { "✅ Cached ${publicKeysCache.size} Apple public keys" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch Apple public keys" }
        }
    }
    
    /**
     * Create RSA PublicKey from Base64URL encoded modulus and exponent
     */
    private fun createPublicKey(modulusBase64: String, exponentBase64: String): PublicKey {
        val decoder = Base64.getUrlDecoder()
        
        val modulusBytes = decoder.decode(modulusBase64)
        val exponentBytes = decoder.decode(exponentBase64)
        
        val modulus = BigInteger(1, modulusBytes)
        val exponent = BigInteger(1, exponentBytes)
        
        val spec = RSAPublicKeySpec(modulus, exponent)
        val keyFactory = KeyFactory.getInstance("RSA")
        
        return keyFactory.generatePublic(spec)
    }
    
    /**
     * Force refresh of public keys (useful for testing or manual refresh)
     */
    fun forceRefresh() {
        logger.info { "Force refreshing Apple public keys" }
        refreshPublicKeys()
    }
}











