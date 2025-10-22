package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.Word
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class UserProgressService(
    private val wordRepository: WordRepository
) {
    
    /**
     * Calculate user's progress statistics based on their vocabulary data
     */
    fun calculateProgressStats(user: User): ProgressStatsDto {
        logger.info { "Calculating progress stats for user ${user.email}" }
        
        val words = wordRepository.findAllByUser(user)
        val currentTime = System.currentTimeMillis()
        
        // Count words by level (0-6)
        val levelCounts = IntArray(7) // levels 0-6
        var dueCards = 0
        
        words.forEach { word ->
            // Count by level
            val level = word.level.coerceIn(0, 6)
            levelCounts[level]++
            
            // Count due cards (next review date is in the past)
            if (word.nextReviewDate <= currentTime) {
                dueCards++
            }
        }
        
        val totalWords = words.size
        
        logger.info { "Progress stats for ${user.email}: totalWords=$totalWords, dueCards=$dueCards, levels=${levelCounts.joinToString()}" }
        
        return ProgressStatsDto(
            totalWords = totalWords,
            dueCards = dueCards,
            level0Count = levelCounts[0],
            level1Count = levelCounts[1],
            level2Count = levelCounts[2],
            level3Count = levelCounts[3],
            level4Count = levelCounts[4],
            level5Count = levelCounts[5],
            level6Count = levelCounts[6]
        )
    }
}
