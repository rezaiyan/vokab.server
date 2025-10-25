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
    suspend fun getAvailableCollections(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiResponse<List<VocabularyCollectionDto>>> {
        logger.info { "📚 Collections Controller: GET /api/v1/collections called by user ${user.email}" }
        val result = githubVocabularyService.getAvailableCollections()
        
        return if (result.isSuccess) {
            logger.info { "✅ Collections Controller: Successfully fetched collections" }
            ResponseEntity.ok(ApiResponse(success = true, data = result.getOrNull()))
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
    }

    /**
     * Download a specific vocabulary collection
     * Returns the raw content of the .txt file
     */
    @PostMapping("/download")
    suspend fun downloadCollection(
        @AuthenticationPrincipal user: User,
        @RequestBody request: DownloadCollectionRequest
    ): ResponseEntity<ApiResponse<VocabularyContentResponse>> {
        logger.info { "📥 Collections Controller: Download collection ${request.fileName} requested by user ${user.email}" }
        val result = githubVocabularyService.downloadCollection(
            request.targetLanguage,
            request.originLanguage,
            request.fileName
        )
        
        return if (result.isSuccess) {
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
    }
}
