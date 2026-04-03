package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.AppConfig
import com.alirezaiyan.vokab.server.domain.entity.AppConfigHistory
import com.alirezaiyan.vokab.server.domain.repository.AppConfigHistoryRepository
import com.alirezaiyan.vokab.server.domain.repository.AppConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

private const val CACHE_TTL_SECONDS = 30L

@Service
class AppConfigService(
    private val appConfigRepository: AppConfigRepository,
    private val appConfigHistoryRepository: AppConfigHistoryRepository
) {
    private data class CacheEntry(val value: String?, val expiresAt: Instant)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Returns the current value for the given key, or null if not found / disabled.
     * Results are cached for [CACHE_TTL_SECONDS] seconds so callers on hot paths
     * (e.g. JWT filter) don't hit the DB on every request.
     */
    @Transactional(readOnly = true)
    fun get(namespace: String, key: String): String? {
        val cacheKey = "$namespace:$key"
        val entry = cache[cacheKey]
        if (entry != null && entry.expiresAt.isAfter(Instant.now())) {
            return entry.value
        }
        val value = appConfigRepository.findByNamespaceAndKeyAndEnabledTrue(namespace, key)?.value
        cache[cacheKey] = CacheEntry(value, Instant.now().plusSeconds(CACHE_TTL_SECONDS))
        return value
    }

    @Transactional(readOnly = true)
    fun find(namespace: String, key: String): AppConfig? =
        appConfigRepository.findByNamespaceAndKey(namespace, key)

    @Transactional(readOnly = true)
    fun list(): List<AppConfig> = appConfigRepository.findAllByOrderByNamespaceAscKeyAsc()

    @Transactional(readOnly = true)
    fun history(namespace: String, key: String): List<AppConfigHistory> =
        appConfigHistoryRepository.findByNamespaceAndKeyOrderByChangedAtDesc(namespace, key)

    @Transactional
    fun set(namespace: String, key: String, value: String, changedBy: String?): AppConfig {
        val config = appConfigRepository.findByNamespaceAndKey(namespace, key)
            ?: throw NoSuchElementException("Config not found: $namespace/$key")
        val oldValue = config.value
        val updated = appConfigRepository.save(config.copy(value = value, updatedAt = Instant.now()))
        appConfigHistoryRepository.save(
            AppConfigHistory(
                namespace = namespace,
                key = key,
                oldValue = oldValue,
                newValue = value,
                changedBy = changedBy
            )
        )
        cache.remove("$namespace:$key")
        logger.info { "Config updated: $namespace/$key by ${changedBy ?: "unknown"}" }
        return updated
    }

    // ── Convenience accessors ─────────────────────────────────────────────────

    fun getTestEmails(): Set<String> =
        get("testing", "test_emails")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
}
