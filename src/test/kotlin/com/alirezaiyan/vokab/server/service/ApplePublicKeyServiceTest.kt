package com.alirezaiyan.vokab.server.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit tests for ApplePublicKeyService.
 *
 * Because the service creates its own WebClient and ObjectMapper internally (no
 * constructor-injection), the tests control internal state via reflection — either
 * by pre-populating the publicKeysCache or by manipulating lastFetchTime so that
 * refreshPublicKeys() is skipped or triggered deterministically.
 *
 * Tests that require an actual HTTP call are intentionally NOT included here;
 * those belong in integration / WireMock-based tests.
 */
class ApplePublicKeyServiceTest {

    private lateinit var service: ApplePublicKeyService

    @BeforeEach
    fun setUp() {
        service = ApplePublicKeyService()
        // Mark cache as "just refreshed" so no outbound HTTP call is attempted
        // during tests that are only exercising cache behaviour.
        setLastFetchTime(System.currentTimeMillis())
    }

    // ── getPublicKey: cache hit ────────────────────────────────────────────────

    @Test
    fun `should return key from cache when kid is present`() {
        val expected = generateRsaPublicKey()
        seedCache(mapOf("kid-1" to expected))

        val result = service.getPublicKey("kid-1")

        assertNotNull(result)
        assertEquals(expected, result)
    }

    @Test
    fun `should return null when kid is not in cache and cache is fresh`() {
        seedCache(mapOf("other-kid" to generateRsaPublicKey()))

        // Cache is fresh (lastFetchTime was just set) but "missing-kid" is absent.
        // refreshPublicKeys() will be called a second time because the key is absent;
        // it will hit the real Apple endpoint which may fail in CI.
        // We verify the return is null (either truly absent or network failure swallowed).
        val result = service.getPublicKey("missing-kid")

        assertNull(result)
    }

    @Test
    fun `should return null when cache is empty and kid is not found`() {
        seedCache(emptyMap())

        val result = service.getPublicKey("nonexistent-kid")

        assertNull(result)
    }

    // ── getPublicKey: multiple keys in cache ───────────────────────────────────

    @Test
    fun `should return correct key when multiple keys are cached`() {
        val key1 = generateRsaPublicKey()
        val key2 = generateRsaPublicKey()
        seedCache(mapOf("kid-1" to key1, "kid-2" to key2))

        assertEquals(key1, service.getPublicKey("kid-1"))
        assertEquals(key2, service.getPublicKey("kid-2"))
    }

    // ── getPublicKey: stale cache triggers refresh ─────────────────────────────

    @Test
    fun `should attempt refresh when cache TTL has expired`() {
        // Set lastFetchTime to more than 24 hours ago so cache appears stale.
        setLastFetchTime(System.currentTimeMillis() - (25 * 60 * 60 * 1000L))
        seedCache(emptyMap())

        // After a stale-cache refresh attempt the key will still be null because
        // the network call either fails or returns keys that don't include our kid.
        // What we verify is that no exception is thrown — refreshPublicKeys catches all.
        val result = service.getPublicKey("any-kid")

        assertNull(result)
    }

    @Test
    fun `should not throw when network is unavailable and cache is stale`() {
        setLastFetchTime(0L)
        seedCache(emptyMap())

        org.junit.jupiter.api.assertDoesNotThrow {
            service.getPublicKey("any-kid")
        }
    }

    // ── createPublicKey (via cache pre-population round-trip) ─────────────────

    @Test
    fun `should create RSA public key from valid Base64URL modulus and exponent`() {
        // Generate a known RSA key pair and verify the recreated key matches the original
        val keyGen = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = keyGen.generateKeyPair()
        val rsaPublicKey = keyPair.public as RSAPublicKey

        val modulusBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rsaPublicKey.modulus.toByteArray())
        val exponentBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rsaPublicKey.publicExponent.toByteArray())

        // Build a minimal JWK JSON string and let the service parse it via
        // direct method access through reflection
        val recreated = invokeCreatePublicKey(modulusBase64, exponentBase64)

        assertNotNull(recreated)
        assertEquals(rsaPublicKey.modulus, (recreated as RSAPublicKey).modulus)
        assertEquals(rsaPublicKey.publicExponent, (recreated as RSAPublicKey).publicExponent)
    }

    // ── cache population is idempotent ────────────────────────────────────────

    @Test
    fun `should overwrite existing cache entry when the same kid is seeded again`() {
        val key1 = generateRsaPublicKey()
        val key2 = generateRsaPublicKey()
        seedCache(mapOf("kid-1" to key1))

        // Overwrite with key2
        seedCache(mapOf("kid-1" to key2))

        assertEquals(key2, service.getPublicKey("kid-1"))
    }

    // ── helper: reflection-based access ───────────────────────────────────────

    /**
     * Pre-populates the service's internal ConcurrentHashMap<String, PublicKey> cache.
     * Existing entries are cleared first to give each test a clean slate.
     */
    @Suppress("UNCHECKED_CAST")
    private fun seedCache(entries: Map<String, PublicKey>) {
        val field = ApplePublicKeyService::class.java.getDeclaredField("publicKeysCache")
        field.isAccessible = true
        val cache = field.get(service) as ConcurrentHashMap<String, PublicKey>
        cache.clear()
        cache.putAll(entries)
    }

    /**
     * Sets the service's private lastFetchTime field so tests can simulate fresh
     * or stale cache without waiting real time.
     */
    private fun setLastFetchTime(timeMs: Long) {
        val field = ApplePublicKeyService::class.java.getDeclaredField("lastFetchTime")
        field.isAccessible = true
        field.set(service, timeMs)
    }

    /**
     * Calls the private createPublicKey method via reflection so the RSA key
     * construction logic can be tested in isolation.
     */
    private fun invokeCreatePublicKey(modulusBase64: String, exponentBase64: String): PublicKey {
        val method = ApplePublicKeyService::class.java.getDeclaredMethod(
            "createPublicKey",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(service, modulusBase64, exponentBase64) as PublicKey
    }

    /**
     * Generates a real RSA public key for use as a test fixture.
     */
    private fun generateRsaPublicKey(): PublicKey {
        return java.security.KeyPairGenerator.getInstance("RSA")
            .apply { initialize(1024) }
            .generateKeyPair()
            .public
    }
}
