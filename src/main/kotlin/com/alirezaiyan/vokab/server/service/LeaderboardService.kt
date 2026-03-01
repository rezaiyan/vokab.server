package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.alirezaiyan.vokab.server.presentation.dto.LeaderboardEntryDto
import com.alirezaiyan.vokab.server.presentation.dto.LeaderboardResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class LeaderboardService(
    private val userRepository: UserRepository,
    private val wordRepository: WordRepository,
    private val aliasGenerator: AliasGenerator
) {

    @Transactional(readOnly = true)
    fun getLeaderboard(requestingUser: User, limit: Int = 20): LeaderboardResponse {
        val topUsers = userRepository.findTopUsersByMasteredWords(PageRequest.of(0, limit))
        val userIds = topUsers.mapNotNull { it.id }

        val masteredCounts = if (userIds.isNotEmpty()) {
            wordRepository.countMasteredWordsByUserIds(userIds)
                .associate { row -> (row[0] as Long) to (row[1] as Long).toInt() }
        } else {
            emptyMap()
        }

        val entries = topUsers.mapIndexed { index, user ->
            toEntryDto(
                user = user,
                rank = index + 1,
                masteredWords = masteredCounts[user.id] ?: 0,
                isCurrentUser = user.id == requestingUser.id
            )
        }

        val userInTop = entries.any { it.isCurrentUser }
        val userEntry = if (!userInTop) {
            val userMastered = wordRepository.countMasteredWordsByUserId(requestingUser.id!!)
            val userRank = userRepository.findUserRankByMasteredWords(userMastered).toInt()
            toEntryDto(
                user = requestingUser,
                rank = userRank,
                masteredWords = userMastered.toInt(),
                isCurrentUser = true
            )
        } else {
            null
        }

        logger.info { "Leaderboard fetched: ${entries.size} entries, user rank=${userEntry?.rank ?: "in top"}" }

        return LeaderboardResponse(entries = entries, userEntry = userEntry)
    }

    private fun toEntryDto(user: User, rank: Int, masteredWords: Int, isCurrentUser: Boolean): LeaderboardEntryDto {
        val displayName = user.displayAlias ?: aliasGenerator.generate(user.id!!)
        return LeaderboardEntryDto(
            rank = rank,
            displayName = displayName,
            currentStreak = user.currentStreak,
            longestStreak = user.longestStreak,
            masteredWords = masteredWords,
            isCurrentUser = isCurrentUser
        )
    }
}
