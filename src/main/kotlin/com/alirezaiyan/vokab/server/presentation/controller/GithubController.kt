package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.UpsertWordsRequest
import com.alirezaiyan.vokab.server.presentation.dto.VocabularyCollectionDto
import com.alirezaiyan.vokab.server.service.GitHubVocabularyService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/github")
class GithubController(
    private val githubVocabularyService: GitHubVocabularyService
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: User
        ): ResponseEntity<ApiResponse<List<VocabularyCollectionDto>>> {
            val result = kotlinx.coroutines.runBlocking {
                githubVocabularyService.getAvailableCollections()
            }
            
            return if (result.isSuccess) {
                val collections = result.getOrNull()
                ResponseEntity.ok(ApiResponse(success = true, data = collections))
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

}
