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
    fun getLeaderboard(requestingUser: User, limit: Int = 50): LeaderboardResponse {
        val topUserData = wordRepository.findTopUserIdsByMasteredWords(PageRequest.of(0, limit))
        val topUserIds = topUserData.map { it[0] as Long }
        val masteredCounts = topUserData.associate { (it[0] as Long) to (it[1] as Long).toInt() }

        val users = if (topUserIds.isNotEmpty()) {
            userRepository.findAllById(topUserIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        val entries = topUserIds.mapIndexedNotNull { index, userId ->
            val user = users[userId] ?: return@mapIndexedNotNull null
            toEntryDto(
                user = user,
                rank = index + 1,
                masteredWords = masteredCounts[userId] ?: 0,
                isCurrentUser = userId == requestingUser.id
            )
        }

        val userInTop = entries.any { it.isCurrentUser }
        val userEntry = if (!userInTop) {
            val userMastered = wordRepository.countMasteredWordsByUserId(requestingUser.id!!).toInt()
            val userRank = masteredCounts.values.count { it > userMastered } + 1
            toEntryDto(
                user = requestingUser,
                rank = userRank,
                masteredWords = userMastered,
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
