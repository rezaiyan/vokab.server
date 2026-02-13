package com.alirezaiyan.vokab.server.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ExtractVocabularyRequest(
    @field:NotBlank(message = "Image data is required")
    val imageBase64: String,
    
    @field:NotBlank(message = "Target language is required")
    val targetLanguage: String,
    
    val extractWords: Boolean = true,
    val extractSentences: Boolean = false
)

data class GenerateInsightRequest(
    @field:NotNull(message = "Progress stats are required")
    val stats: ProgressStatsDto
)

data class ProgressStatsDto(
    val totalWords: Int,
    val dueCards: Int,
    val level0Count: Int,
    val level1Count: Int,
    val level2Count: Int,
    val level3Count: Int,
    val level4Count: Int,
    val level5Count: Int,
    val level6Count: Int
)

data class VocabularyExtractionResponse(
    val extractedText: String,
    val wordCount: Int
)

data class InsightResponse(
    val insight: String,
    val generatedAt: String
)

data class TranslateTextRequest(
    @field:NotBlank(message = "Text is required")
    val text: String,
    
    @field:NotBlank(message = "Target language is required")
    val targetLanguage: String
)

data class TranslateTextResponse(
    val originalText: String,
    val translation: String
)

// --- Suggest vocabulary by language preferences ---

data class SuggestVocabularyRequest(
    @field:NotBlank(message = "Target language (language to learn) is required")
    val targetLanguage: String,

    @field:NotBlank(message = "Current level in the target language is required")
    val currentLevel: String,

    @field:NotBlank(message = "Native or current language is required")
    val nativeLanguage: String
)

/**
 * Single vocabulary item for display and optional import.
 * originalWord is in the target language; translation is in the user's native language.
 */
data class SuggestVocabularyItemResponse(
    val originalWord: String,
    val translation: String,
    val description: String = ""
)

data class SuggestVocabularyResponse(
    val targetLanguage: String,
    val nativeLanguage: String,
    val currentLevel: String,
    val items: List<SuggestVocabularyItemResponse>
)

