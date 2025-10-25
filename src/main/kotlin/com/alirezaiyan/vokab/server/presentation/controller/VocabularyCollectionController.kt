package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.DownloadCollectionRequest
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyCollectionDto
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyContentResponse
import com.alirezaiyan.vokab.server.service.GitHubVocabularyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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
    suspend fun getAvailableCollections(): ResponseEntity<ApiResponse<List<VocabularyCollectionDto>>> {
        val result = githubVocabularyService.getAvailableCollections()
        
        return if (result.isSuccess) {
            ResponseEntity.ok(ApiResponse(success = true, data = result.getOrNull()))
        } else {
            val error = result.exceptionOrNull()
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
        @RequestBody request: DownloadCollectionRequest
    ): ResponseEntity<ApiResponse<VocabularyContentResponse>> {
        val result = githubVocabularyService.downloadCollection(request.language, request.fileName)
        
        return if (result.isSuccess) {
            val content = result.getOrNull() ?: ""
            val wordCount = content.lines().filter { it.trim().isNotEmpty() }.size
            
            val response = VocabularyContentResponse(
                language = request.language,
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
