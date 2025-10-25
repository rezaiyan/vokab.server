package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.DownloadCollectionRequest
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyCollectionDto
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyContentResponse
import com.alirezaiyan.vokab.server.service.GitHubVocabularyService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/collections")
class VocabularyCollectionController(
    private val githubVocabularyService: GitHubVocabularyService
) {
    
    /**
     * Get list of available vocabulary collections
     * Returns all collections organized by language
     */
    @GetMapping
    fun getAvailableCollections(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<List<VocabularyCollectionDto>>> {
        logger.info { "📚 Collections Controller: GET /api/v1/collections called by user ${user.email}" }
        return try {
            val result = kotlinx.coroutines.runBlocking {
                githubVocabularyService.getAvailableCollections()
            }
            
            if (result.isSuccess) {
                val collections = result.getOrNull()
                logger.info { "✅ Collections Controller: Successfully fetched ${collections?.size ?: 0} collections" }
                ResponseEntity.ok(ApiResponse(success = true, data = collections))
            } else {
                val error = result.exceptionOrNull()
                logger.error { "❌ Collections Controller: Failed to fetch collections - ${error?.message}" }
                ResponseEntity.ok(
                    ApiResponse(
                        success = false,
                        message = error?.message ?: "Failed to fetch collections"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in getAvailableCollections" }
            ResponseEntity.ok(
                ApiResponse(
                    success = false,
                    message = e.message ?: "Failed to fetch collections"
                )
            )
        }
    }

    /**
     * Download a specific vocabulary collection
     * Returns the raw content of the .txt file
     */
    @PostMapping("/download")
    fun downloadCollection(
        @AuthenticationPrincipal user: User,
        @RequestBody request: DownloadCollectionRequest
    ): ResponseEntity<ApiResponse<VocabularyContentResponse>> {
        logger.info { "📥 Collections Controller: Download collection ${request.fileName} requested by user ${user.email}" }
        return try {
            val result = kotlinx.coroutines.runBlocking {
                githubVocabularyService.downloadCollection(
                    request.targetLanguage,
                    request.originLanguage,
                    request.fileName
                )
            }
            
            if (result.isSuccess) {
                val content = result.getOrNull() ?: ""
                val wordCount = content.lines().filter { it.trim().isNotEmpty() }.size
                
                val response = VocabularyContentResponse(
                    targetLanguage = request.targetLanguage,
                    originLanguage = request.originLanguage,
                    fileName = request.fileName,
                    content = content,
                    wordCount = wordCount
                )
                
                ResponseEntity.ok(ApiResponse(success = true, data = response))
            } else {
                val error = result.exceptionOrNull()
                ResponseEntity.ok(
                    ApiResponse(
                        success = false,
                        message = error?.message ?: "Failed to download collection"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in downloadCollection" }
            ResponseEntity.ok(
                ApiResponse(
                    success = false,
                    message = e.message ?: "Failed to download collection"
                )
            )
        }
    }
}
