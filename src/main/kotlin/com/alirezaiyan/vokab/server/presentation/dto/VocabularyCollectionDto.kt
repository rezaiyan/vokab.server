package com.alirezaiyan.vokab.server.presentation.dto

/**
 * Represents a vocabulary collection from GitHub
 */
data class VocabularyCollectionDto(
    val language: String,
    val title: String,
    val fileName: String,
    val path: String
)

/**
 * Request DTO for downloading a collection
 */
data class DownloadCollectionRequest(
    val language: String,
    val fileName: String
)

/**
 * Response DTO for downloaded collection content
 */
data class VocabularyContentResponse(
    val language: String,
    val fileName: String,
    val content: String,
    val wordCount: Int
)

/**
 * GitHub API content model
 */
data class GitHubContent(
    val name: String,
    val type: String,
    val download_url: String? = null
)
