package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.CreateTagRequest
import com.alirezaiyan.vokab.server.presentation.dto.RenameTagRequest
import com.alirezaiyan.vokab.server.presentation.dto.TagDto
import com.alirezaiyan.vokab.server.service.TagService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/tags")
class TagController(private val tagService: TagService) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: User,
    ): ResponseEntity<ApiResponse<List<TagDto>>> {
        val tags = tagService.list(user)
        return ResponseEntity.ok(ApiResponse(success = true, data = tags))
    }

    @PostMapping
    fun create(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: CreateTagRequest,
    ): ResponseEntity<ApiResponse<TagDto>> {
        val tag = tagService.create(user, request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(success = true, data = tag))
    }

    @PutMapping("/{id}")
    fun rename(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long,
        @Valid @RequestBody request: RenameTagRequest,
    ): ResponseEntity<ApiResponse<TagDto>> {
        val tag = tagService.rename(user, id, request.name)
        return ResponseEntity.ok(ApiResponse(success = true, data = tag))
    }

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long,
    ): ResponseEntity<Unit> {
        tagService.delete(user, id)
        return ResponseEntity.noContent().build()
    }
}
