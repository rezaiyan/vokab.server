package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.BatchDeleteRequest
import com.alirezaiyan.vokab.server.presentation.dto.BatchDeleteResponse
import com.alirezaiyan.vokab.server.presentation.dto.BatchUpdateLanguagesRequest
import com.alirezaiyan.vokab.server.presentation.dto.BatchUpdateLanguagesResponse
import com.alirezaiyan.vokab.server.presentation.dto.UpdateWordRequest
import com.alirezaiyan.vokab.server.presentation.dto.UpdateWordTagsRequest
import com.alirezaiyan.vokab.server.presentation.dto.UpsertWordsRequest
import com.alirezaiyan.vokab.server.presentation.dto.WordDto
import com.alirezaiyan.vokab.server.service.WordService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/words")
class WordController(
    private val wordService: WordService,
) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: User): ResponseEntity<ApiResponse<List<WordDto>>> {
        val words = wordService.list(user)
        return ResponseEntity.ok(ApiResponse(success = true, data = words))
    }

    @PostMapping
    fun upsert(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody req: UpsertWordsRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        wordService.upsert(user, req.words)
        return ResponseEntity.ok(ApiResponse(success = true, message = "Upserted"))
    }

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateWordRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        wordService.update(user, id, request)
        return ResponseEntity.ok(ApiResponse(success = true, message = "Updated"))
    }

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        wordService.delete(user, id)
        return ResponseEntity.ok(ApiResponse(success = true, message = "Deleted"))
    }

    @PostMapping("/batch-delete")
    fun batchDelete(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: BatchDeleteRequest,
    ): ResponseEntity<ApiResponse<BatchDeleteResponse>> {
        val deletedCount = wordService.batchDelete(user, request.ids)
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Deleted $deletedCount words",
                data = BatchDeleteResponse(deletedCount = deletedCount),
            )
        )
    }

    @PutMapping("/{id}/tags")
    fun updateTags(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateWordTagsRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        wordService.updateWordTags(user, id, request.tagIds)
        return ResponseEntity.ok(ApiResponse(success = true, message = "Tags updated"))
    }

    @PostMapping("/batch-update")
    fun batchUpdate(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: BatchUpdateLanguagesRequest,
    ): ResponseEntity<ApiResponse<BatchUpdateLanguagesResponse>> {
        val updatedCount = wordService.batchUpdateLanguages(
            user, request.ids, request.sourceLanguage, request.targetLanguage,
        )
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Updated $updatedCount words",
                data = BatchUpdateLanguagesResponse(updatedCount = updatedCount),
            )
        )
    }
}
