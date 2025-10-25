package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.presentation.dto.GitHubContent
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyCollectionDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.util.retry.Retry
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Service to fetch vocabulary collections from GitHub repository
 * Acts as a middleware to simplify access to vocabulary data
 */
@Service
class GitHubVocabularyService(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${app.github-vocabulary.repo-url:https://api.github.com/repos/rezaiyan/Vokab-collection}")
    private val repoUrl: String
) {
    private val webClient: WebClient = webClientBuilder
        .baseUrl(repoUrl)
        .build()

    /**
     * Get list of available vocabulary collections
     * Returns language folders with their collection files
     */
    suspend fun getAvailableCollections(): Result<List<VocabularyCollectionDto>> {
        return try {
            logger.info { "Fetching available vocabulary collections from GitHub" }
            
            val collections = withContext(Dispatchers.IO) {
                // List all language folders
                val languages = getLanguages()
                
                // Get collections from each language folder
                val allCollections = mutableListOf<VocabularyCollectionDto>()
                
                languages.forEach { language ->
                    val files = getFilesInDirectory(language)
                    files.filter { it.endsWith(".txt") }.forEach { fileName ->
                        allCollections.add(
                            VocabularyCollectionDto(
                                language = language,
                                title = fileName.removeSuffix(".txt"),
                                fileName = fileName,
                                path = "$language/$fileName"
                            )
                        )
                    }
                }
                
                allCollections
            }
            
            logger.info { "Found ${collections.size} vocabulary collections" }
            Result.success(collections)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch vocabulary collections: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Download a specific vocabulary collection
     * Returns the raw content of the .txt file
     */
    suspend fun downloadCollection(language: String, fileName: String): Result<String> {
        return try {
            logger.info { "Downloading vocabulary collection: $language/$fileName" }
            
            val content = withContext(Dispatchers.IO) {
                val downloadUrl = getFileDownloadUrl("$language/$fileName")
                webClient.get()
                    .uri(downloadUrl)
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block()!!
                .let { response ->
                    val content = response.trim()
                    if (content.isEmpty()) {
                        throw IllegalStateException("Collection file is empty")
                    }
                    content
                }
            }
            
            logger.info { "Successfully downloaded vocabulary collection: $language/$fileName (${content.length} chars)" }
            Result.success(content)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to download vocabulary collection: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Get list of language folders from the repository
     */
    private suspend fun getLanguages(): List<String> {
        return webClient.get()
            .uri("/contents")
            .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
            .retrieve()
            .bodyToFlux(GitHubContent::class.java)
            .collectList()
            .block()!!
            .filter { it.type == "dir" }
            .map { it.name }
    }

    /**
     * Get files in a specific directory
     */
    private suspend fun getFilesInDirectory(directory: String): List<String> {
        return webClient.get()
            .uri("/contents/$directory")
            .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
            .retrieve()
            .bodyToFlux(GitHubContent::class.java)
            .collectList()
            .block()!!
            .filter { it.type == "file" }
            .map { it.name }
    }

    /**
     * Get download URL for a file
     */
    private suspend fun getFileDownloadUrl(path: String): String {
        val content = webClient.get()
            .uri("/contents/$path")
            .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
            .retrieve()
            .bodyToMono(GitHubContent::class.java)
            .block()!!
        
        return content.download_url ?: throw IllegalStateException("Download URL not available for $path")
    }
}
