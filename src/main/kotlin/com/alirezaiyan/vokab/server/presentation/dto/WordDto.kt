package com.alirezaiyan.vokab.server.presentation.dto

data class WordDto(
    val id: Long?,
    val originalWord: String,
    val translation: String,
    val description: String,
    val level: Int,
    val easeFactor: Float,
    val interval: Int,
    val repetitions: Int,
    val lastReviewDate: Long,
    val nextReviewDate: Long,
)

data class UpsertWordsRequest(
    val words: List<WordDto>
)


