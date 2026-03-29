package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class UserProgressService(
    private val wordRepository: WordRepository
) {

    @Transactional(readOnly = true)
    fun calculateProgressStats(user: User): ProgressStatsDto {
        logger.info { "Calculating progress stats for userId=${user.id}" }

        val nowMs = System.currentTimeMillis()
        val rows = wordRepository.findProgressRowsByUserId(user.id!!, nowMs)

        val levelCounts = IntArray(7)
        var dueCards = 0
        var totalWords = 0

        for (row in rows) {
            val level = row.getLevel().coerceIn(0, 6)
            val count = row.getWordCount().toInt()
            levelCounts[level] += count
            dueCards += row.getDueCount().toInt()
            totalWords += count
        }

        logger.info { "Progress stats for userId=${user.id}: totalWords=$totalWords, dueCards=$dueCards" }

        return ProgressStatsDto(
            totalWords = totalWords,
            dueCards = dueCards,
            level0Count = levelCounts[0],
            level1Count = levelCounts[1],
            level2Count = levelCounts[2],
            level3Count = levelCounts[3],
            level4Count = levelCounts[4],
            level5Count = levelCounts[5],
            level6Count = levelCounts[6],
        )
    }
}
