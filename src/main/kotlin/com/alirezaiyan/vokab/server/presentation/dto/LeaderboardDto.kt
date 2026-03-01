package com.alirezaiyan.vokab.server.presentation.dto

data class LeaderboardEntryDto(
    val rank: Int,
    val displayName: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val masteredWords: Int,
    val isCurrentUser: Boolean
)

data class LeaderboardResponse(
    val entries: List<LeaderboardEntryDto>,
    val userEntry: LeaderboardEntryDto?
)
