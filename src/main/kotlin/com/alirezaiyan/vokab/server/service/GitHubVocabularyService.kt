package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.presentation.dto.GitHubContent
import com.alirezaiyan.vokab.server.presentation.dto.GitHubTree
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyCollectionDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Service to fetch vocabulary collections from GitHub repository
 * Acts as a middleware to simplify access to vocabulary data
 */
@Service
class GitHubVocabularyService(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${app.github-vocabulary.repo-url:https://api.github.com/repos/rezaiyan/Vokab-collection}")
    private val repoUrl: String,
    @Value("\${app.github-vocabulary.token:}")
    private val gitHubToken: String?
) {
    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val CACHE_KEY_COLLECTIONS = "collections"
        private const val CACHE_KEY_COMMIT_SHA = "commit_sha"
        private const val CACHE_KEY_TREE_SHA = "tree_sha"
    }
    
    /**
     * In-memory cache with TTL
     * Key: cache key, Value: Pair of (data, timestamp)
     */
    private val cache = ConcurrentHashMap<String, Pair<Any, Long>>()
    private val webClient: WebClient = run {
        val builder = webClientBuilder.baseUrl(repoUrl)
        // Add Authorization header if token is provided
        // For GitHub API, both "token" and "Bearer" work, but "token" is preferred for classic tokens
        if (!gitHubToken.isNullOrBlank()) {
            builder.defaultHeader("Authorization", "token $gitHubToken")
            logger.info { "GitHub token configured - rate limit: 5000/hour" }
        } else {
            logger.warn { "No GitHub token configured - rate limit: 60/hour (unauthenticated)" }
            logger.warn { "GitHub repository is private - token is required to access content" }
        }
        builder.build()
    }

    /**
     * Get list of available vocabulary collections
     * Structure: TargetLanguage/OriginLanguage/fileName.txt
     * Optimized with in-memory caching (5min TTL) and single recursive Git Trees API call
     */
    suspend fun getAvailableCollections(): Result<List<VocabularyCollectionDto>> {
        return try {
            // Check cache first
            val cachedResult = getCached<List<VocabularyCollectionDto>>(CACHE_KEY_COLLECTIONS)
            if (cachedResult != null) {
                logger.debug { "Returning cached vocabulary collections (${cachedResult.size} items)" }
                return Result.success(cachedResult)
            }
            
            logger.info { "Fetching available vocabulary collections from GitHub using recursive tree API" }
            
            // Get HEAD commit SHA (with caching)
            val commitSha = getHeadCommitShaCached()
            
            // Get tree SHA (with caching)
            val treeSha = getTreeShaCached(commitSha)
            
            // Fetch entire repository tree recursively
            val tree = webClient.get()
                .uri("/git/trees/$treeSha?recursive=1")
                .retrieve()
                .bodyToMono(GitHubTree::class.java)
                .awaitSingle()
            
            if (tree.truncated) {
                logger.warn { "Repository tree was truncated. Some files may be missing." }
            }
            
            // Filter for .txt files and extract metadata
            val allCollections = tree.tree
                .filter { it.type == "blob" && it.path.endsWith(".txt") }
                .mapNotNull { entry ->
                    // Parse path: TargetLanguage/OriginLanguage/fileName.txt
                    val pathParts = entry.path.split("/")
                    if (pathParts.size == 3) {
                        val targetLanguage = pathParts[0]
                        val originLanguage = pathParts[1]
                        val fileName = pathParts[2]
                        
                        val title = fileName.removeSuffix(".txt")
                            .replace("_", " ")
                            .replace("-", " - ")
                        
                        VocabularyCollectionDto(
                            targetLanguage = targetLanguage,
                            originLanguage = originLanguage,
                            title = title,
                            fileName = fileName,
                            path = entry.path
                        )
                    } else {
                        logger.debug { "Skipping file with unexpected path structure: ${entry.path}" }
                        null
                    }
                }
            
            // Cache the result
            setCache(CACHE_KEY_COLLECTIONS, allCollections)
            
            logger.info { "Found ${allCollections.size} vocabulary collections and cached for 5 minutes" }
            Result.success(allCollections)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch vocabulary collections: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Download a specific vocabulary collection
     * Returns the raw content of the .txt file
     * Optimized: Uses GitHub API content endpoint with base64 decoding instead of download_url
     */
    suspend fun downloadCollection(targetLanguage: String, originLanguage: String, fileName: String): Result<String> {
        return try {
            val path = "$targetLanguage/$originLanguage/$fileName"
            logger.info { "Downloading vocabulary collection: $path" }
            
            // Use GitHub API content endpoint directly, which returns base64 encoded content
            // This avoids the extra API call to get download_url
            val content = getFileContent(path)
            
            if (content.isEmpty()) {
                throw IllegalStateException("Collection file is empty")
            }
            
            logger.info { "Successfully downloaded vocabulary collection: $path (${content.length} chars)" }
            Result.success(content)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to download vocabulary collection: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Get HEAD commit SHA with caching
     */
    private suspend fun getHeadCommitShaCached(): String {
        val cached = getCached<String>(CACHE_KEY_COMMIT_SHA)
        if (cached != null) {
            logger.debug { "Returning cached HEAD commit SHA: $cached" }
            return cached
        }
        
        val sha = getHeadCommitSha()
        setCache(CACHE_KEY_COMMIT_SHA, sha)
        return sha
    }
    
    /**
     * Get tree SHA from commit SHA with caching
     */
    private suspend fun getTreeShaCached(commitSha: String): String {
        val cacheKey = "$CACHE_KEY_TREE_SHA:$commitSha"
        val cached = getCached<String>(cacheKey)
        if (cached != null) {
            logger.debug { "Returning cached tree SHA: $cached" }
            return cached
        }
        
        val commitInfo = webClient.get()
            .uri("/commits/$commitSha")
            .retrieve()
            .bodyToMono(Map::class.java)
            .awaitSingle()
        
        val commit = commitInfo["commit"] as? Map<*, *>
        val treeInfo = commit?.get("tree") as? Map<*, *>
        val treeSha = treeInfo?.get("sha") as? String
            ?: throw IllegalStateException("Failed to get tree SHA from commit")
        
        setCache(cacheKey, treeSha)
        return treeSha
    }
    
    /**
     * Get HEAD commit SHA directly (optimized to skip default branch lookup)
     */
    private suspend fun getHeadCommitSha(): String {
        return try {
            // Use HEAD branch which points to the default branch's latest commit
            val branchInfo = webClient.get()
                .uri("/branches/HEAD")
                .retrieve()
                .bodyToMono(Map::class.java)
                .awaitSingle()
            
            val commit = branchInfo["commit"] as? Map<*, *>
            val sha = commit?.get("sha") as? String
                ?: throw IllegalStateException("Failed to get HEAD commit SHA")
            
            sha
        } catch (e: WebClientResponseException) {
            val statusCode = e.statusCode.value()
            val errorBody = e.responseBodyAsString
            
            // Fallback: try getting default branch if HEAD doesn't work
            if (statusCode == 404) {
                logger.debug { "HEAD branch not found, trying default branch" }
                return getDefaultBranchSha()
            }
            
            logger.error(e) { "Failed to get HEAD commit SHA ($statusCode): $errorBody" }
            when {
                statusCode == 401 -> throw IllegalStateException("GitHub authentication failed. Check if GITHUB_TOKEN environment variable is set and has 'repo' scope for private repositories.")
                statusCode == 403 -> throw IllegalStateException("GitHub API access forbidden. Token may not have required permissions ('repo' scope) or rate limit exceeded.")
                else -> throw IllegalStateException("GitHub API error ($statusCode): $errorBody")
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get HEAD commit SHA: ${e.message}" }
            throw e
        }
    }
    
    /**
     * Fallback: Get commit SHA from default branch
     */
    private suspend fun getDefaultBranchSha(): String {
        return try {
            val repo = webClient.get()
                .uri("")
                .retrieve()
                .bodyToMono(Map::class.java)
                .awaitSingle()
            
            val defaultBranch = repo["default_branch"] as? String ?: "main"
            
            val branchInfo = webClient.get()
                .uri("/branches/$defaultBranch")
                .retrieve()
                .bodyToMono(Map::class.java)
                .awaitSingle()
            
            val commit = branchInfo["commit"] as? Map<*, *>
            val sha = commit?.get("sha") as? String
                ?: throw IllegalStateException("Failed to get commit SHA for default branch: $defaultBranch")
            
            sha
        } catch (e: WebClientResponseException) {
            val statusCode = e.statusCode.value()
            val errorBody = e.responseBodyAsString
            logger.error(e) { "Failed to get default branch SHA ($statusCode): $errorBody" }
            when {
                statusCode == 401 -> throw IllegalStateException("GitHub authentication failed. Check if GITHUB_TOKEN environment variable is set and has 'repo' scope for private repositories.")
                statusCode == 403 -> throw IllegalStateException("GitHub API access forbidden. Token may not have required permissions ('repo' scope) or rate limit exceeded.")
                statusCode == 404 -> throw IllegalStateException("GitHub repository not found. Ensure the repository exists and the token has access.")
                else -> throw IllegalStateException("GitHub API error ($statusCode): $errorBody")
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get default branch SHA: ${e.message}" }
            throw e
        }
    }

    /**
     * Get file content directly from GitHub API
     * Uses base64 decoding to avoid extra API call for download_url
     */
    private suspend fun getFileContent(path: String): String {
        return try {
            val content = webClient.get()
                .uri("/contents/$path")
                .retrieve()
                .bodyToMono(GitHubContent::class.java)
                .awaitSingle()
            
            // GitHub API returns base64 encoded content for files (with newlines removed)
            if (content.content != null && content.encoding == "base64") {
                val base64Content = content.content.replace("\n", "").replace("\r", "")
                java.util.Base64.getDecoder().decode(base64Content).toString(Charsets.UTF_8).trim()
            } else if (content.download_url != null) {
                // Fallback to download_url if content is not base64 encoded
                webClient.get()
                    .uri(content.download_url)
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .awaitSingle()
            } else {
                throw IllegalStateException("File content not available for $path")
            }
        } catch (e: WebClientResponseException) {
            logger.error(e) { "Failed to get file content for '$path': ${e.statusCode} - ${e.responseBodyAsString}" }
            when {
                e.statusCode.value() == 401 -> throw IllegalStateException("GitHub authentication failed for file access. Check if GITHUB_TOKEN is set and has 'repo' scope.")
                e.statusCode.value() == 404 -> throw IllegalStateException("File not found or access denied: $path")
                else -> throw e
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get file content for '$path': ${e.message}" }
            throw e
        }
    }
    
    /**
     * Get cached value if not expired
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String): T? {
        val cached = cache[key] ?: return null
        val (data, timestamp) = cached
        val currentTime = System.currentTimeMillis()
        
        return if (currentTime - timestamp < CACHE_TTL_MS) {
            data as? T
        } else {
            // Remove expired entry
            cache.remove(key)
            null
        }
    }
    
    /**
     * Set cache value with current timestamp
     */
    private fun <T> setCache(key: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        cache[key] = Pair(value as Any, System.currentTimeMillis())
    }
    
    /**
     * Clear all cache entries (useful for testing or manual invalidation)
     */
    fun clearCache() {
        cache.clear()
        logger.info { "Cache cleared" }
    }
    
    /**
     * Get cache statistics (for monitoring/debugging)
     */
    fun getCacheStats(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val entries = cache.mapNotNull { (key, value) ->
            val (_, timestamp) = value
            val age = currentTime - timestamp
            val isExpired = age >= CACHE_TTL_MS
            if (!isExpired) {
                key to mapOf(
                    "age_ms" to age,
                    "remaining_ttl_ms" to (CACHE_TTL_MS - age)
                )
            } else null
        }.toMap()
        
        return mapOf(
            "total_entries" to cache.size,
            "active_entries" to entries.size,
            "ttl_ms" to CACHE_TTL_MS
        )
    }
}
