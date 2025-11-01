package com.alirezaiyan.vokab.server.presentation.dto

/**
 * Represents a vocabulary collection from GitHub
 * Structure: TargetLanguage/OriginLanguage/fileName.txt
 */
data class VocabularyCollectionDto(
    val targetLanguage: String,    // What user wants to learn (e.g., "English", "Deutsch")
    val originLanguage: String,     // User's native language (e.g., "Farsi", "English")
    val title: String,              // Collection title (from file name)
    val fileName: String,           // Original file name
    val path: String                // Full path like "English/Farsi/file.txt"
)

/**
 * Request DTO for downloading a collection
 */
data class DownloadCollectionRequest(
    val targetLanguage: String,
    val originLanguage: String,
    val fileName: String
)

/**
 * Response DTO for downloaded collection content
 */
data class VocabularyContentResponse(
    val targetLanguage: String,
    val originLanguage: String,
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
    val path: String? = null,
    val content: String? = null,
    val encoding: String? = null,
    val download_url: String? = null
)

/**
 * GitHub API Git Tree response model
 */
data class GitHubTree(
    val sha: String,
    val url: String,
    val tree: List<GitHubTreeEntry>,
    val truncated: Boolean = false
)

/**
 * GitHub API Git Tree entry model
 */
data class GitHubTreeEntry(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Int? = null,
    val url: String? = null
)
