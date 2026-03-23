package com.alirezaiyan.vokab.server.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TagDto(
    val id: Long,
    val name: String,
    val wordCount: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CreateTagRequest(
    @field:NotBlank(message = "Tag name must not be blank")
    @field:Size(max = 100, message = "Tag name must be 100 characters or fewer")
    val name: String,
)

data class RenameTagRequest(
    @field:NotBlank(message = "Tag name must not be blank")
    @field:Size(max = 100, message = "Tag name must be 100 characters or fewer")
    val name: String,
)

data class UpdateWordTagsRequest(
    @field:Size(max = 50, message = "Cannot assign more than 50 tags at once")
    val tagIds: List<Long>,
)
