package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.DailyActivity
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.DailyActivityRepository
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@Service
class StreakService(
    private val dailyActivityRepository: DailyActivityRepository,
    private val userRepository: UserRepository
) {
    
    /**
     * Record user activity and update streak
     * Called when user opens review section
     */
    @Transactional
    fun recordActivity(userId: Long): User {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val today = LocalDate.now()
        
        // Check if activity already recorded today
        val existingActivity = dailyActivityRepository.findByUserAndActivityDate(user, today)
        
        if (existingActivity.isPresent) {
            // Already recorded today, just increment review count
            val activity = existingActivity.get()
            activity.reviewCount++
            dailyActivityRepository.save(activity)
            logger.debug { "Updated review count for user ${user.email} on $today, count=${activity.reviewCount}" }
            
            // Return user as-is (streak already updated when first activity was recorded today)
            return user
        } else {
            // Record new activity for today
            val newActivity = DailyActivity(
                user = user,
                activityDate = today,
                reviewCount = 1
            )
            dailyActivityRepository.save(newActivity)
            logger.info { "✅ Recorded new activity for user ${user.email} on $today" }
            
            // Update streak based on last activity
            val updatedUser = updateStreakOnNewActivity(user, today)
            return updatedUser
        }
    }
    
    /**
     * Update streak when new activity is recorded
     * Simple logic: Check if last activity was yesterday
     * - If yes: increment streak
     * - If no: reset streak to 1
     */
    private fun updateStreakOnNewActivity(user: User, today: LocalDate): User {
        // Get the most recent activity BEFORE today
        val allActivities = dailyActivityRepository.findAllByUserOrderByActivityDateDesc(user)
        val previousActivities = allActivities.filter { it.activityDate < today }
        
        val yesterday = today.minusDays(1)
        
        val newCurrentStreak = if (previousActivities.isNotEmpty() && previousActivities.first().activityDate == yesterday) {
            // Last activity was yesterday, increment streak
            val incrementedStreak = user.currentStreak + 1
            logger.info { "📈 Streak continued: ${user.currentStreak} → $incrementedStreak (last activity was yesterday)" }
            incrementedStreak
        } else {
            // Last activity was more than 24 hours ago, or no previous activity
            logger.info { "🔄 Streak reset: ${user.currentStreak} → 1 (last activity older than 24 hours)" }
            1
        }
        
        // Update longest streak if current is higher
        val newLongestStreak = maxOf(user.longestStreak, newCurrentStreak)
        
        if (newLongestStreak > user.longestStreak) {
            logger.info { "🏆 New record! Longest streak: ${user.longestStreak} → $newLongestStreak" }
        }
        
        // Update user
        val updatedUser = user.copy(
            currentStreak = newCurrentStreak,
            longestStreak = newLongestStreak
        )
        
        val saved = userRepository.save(updatedUser)
        logger.info { "✅ Streak updated for ${user.email}: current=$newCurrentStreak, longest=$newLongestStreak" }
        
        return saved
    }
    
    /**
     * Get user's current streak and longest streak
     * Checks if streak has expired (no activity in last 24 hours)
     */
    @Transactional
    fun getUserStreak(userId: Long): StreakInfo {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        // Get most recent activity
        val recentActivities = dailyActivityRepository.findAllByUserOrderByActivityDateDesc(user)
        val lastActivity = recentActivities.firstOrNull()
        
        // Check if streak is still valid (activity today or yesterday)
        val currentStreak = if (lastActivity != null) {
            when {
                lastActivity.activityDate == today || lastActivity.activityDate == yesterday -> {
                    // Streak is still active (within 24 hours)
                    logger.debug { "Streak valid: last activity ${lastActivity.activityDate}" }
                    user.currentStreak
                }
                else -> {
                    // Streak expired (no activity in last 24 hours)
                    logger.info { "⏰ Streak expired for ${user.email}: last activity ${lastActivity.activityDate}, resetting to 0" }
                    
                    // Update user with expired streak
                    val updatedUser = user.copy(currentStreak = 0)
                    userRepository.save(updatedUser)
                    
                    0
                }
            }
        } else {
            // No activities at all
            0
        }
        
        return StreakInfo(
            currentStreak = currentStreak,
            longestStreak = user.longestStreak
        )
    }
}

data class StreakInfo(
    val currentStreak: Int,
    val longestStreak: Int
)


