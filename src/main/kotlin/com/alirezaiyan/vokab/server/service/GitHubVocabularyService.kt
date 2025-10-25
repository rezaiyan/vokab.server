package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.presentation.dto.GitHubContent
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyCollectionDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

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
     * Structure: TargetLanguage/OriginLanguage/fileName.txt
     */
    suspend fun getAvailableCollections(): Result<List<VocabularyCollectionDto>> {
        return try {
            logger.info { "Fetching available vocabulary collections from GitHub" }
            
            val allCollections = mutableListOf<VocabularyCollectionDto>()
            
            // Get all target language folders (e.g., "English", "Deutsch")
            val targetLanguages = getLanguages()
            
            // For each target language, get origin language folders
            targetLanguages.forEach { targetLang ->
                val originLanguages = getLanguagesInDirectory(targetLang)
                
                // For each origin language, get the files
                originLanguages.forEach { originLang ->
                    val files = getFilesInDirectory("$targetLang/$originLang")
                    files.filter { it.endsWith(".txt") }.forEach { fileName ->
                        val title = fileName.removeSuffix(".txt")
                            .replace("_", " ")
                            .replace("-", " - ")
                        
                        allCollections.add(
                            VocabularyCollectionDto(
                                targetLanguage = targetLang,
                                originLanguage = originLang,
                                title = title,
                                fileName = fileName,
                                path = "$targetLang/$originLang/$fileName"
                            )
                        )
                    }
                }
            }
            
            logger.info { "Found ${allCollections.size} vocabulary collections" }
            Result.success(allCollections)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch vocabulary collections: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Download a specific vocabulary collection
     * Returns the raw content of the .txt file
     */
    suspend fun downloadCollection(targetLanguage: String, originLanguage: String, fileName: String): Result<String> {
        return try {
            val path = "$targetLanguage/$originLanguage/$fileName"
            logger.info { "Downloading vocabulary collection: $path" }
            
            val downloadUrl = getFileDownloadUrl(path)
            val response = webClient.get()
                .uri(downloadUrl)
                .retrieve()
                .bodyToMono(String::class.java)
                .awaitSingle()
            
            val content = response.trim()
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
     * Get list of target language folders from the repository root
     */
    private suspend fun getLanguages(): List<String> {
        return webClient.get()
            .uri("/contents")
            .retrieve()
            .bodyToFlux(GitHubContent::class.java)
            .collectList()
            .awaitSingle()
            .filter { it.type == "dir" }
            .map { it.name }
    }

    /**
     * Get origin language folders within a target language folder
     */
    private suspend fun getLanguagesInDirectory(directory: String): List<String> {
        return webClient.get()
            .uri("/contents/$directory")
            .retrieve()
            .bodyToFlux(GitHubContent::class.java)
            .collectList()
            .awaitSingle()
            .filter { it.type == "dir" }
            .map { it.name }
    }

    /**
     * Get files in a specific directory
     */
    private suspend fun getFilesInDirectory(directory: String): List<String> {
        return webClient.get()
            .uri("/contents/$directory")
            .retrieve()
            .bodyToFlux(GitHubContent::class.java)
            .collectList()
            .awaitSingle()
            .filter { it.type == "file" }
            .map { it.name }
    }

    /**
     * Get download URL for a file
     */
    private suspend fun getFileDownloadUrl(path: String): String {
        val content = webClient.get()
            .uri("/contents/$path")
            .retrieve()
            .bodyToMono(GitHubContent::class.java)
            .awaitSingle()
        
        return content.download_url ?: throw IllegalStateException("Download URL not available for $path")
    }
}
