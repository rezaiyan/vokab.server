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
    val wordCount: Int,
    val aiExtractionUsageCount: Int,
    val aiExtractionUsageLimit: Int,
    val remainingAiExtractions: Int
)

data class InsightResponse(
    val insight: String,
    val generatedAt: String
)

