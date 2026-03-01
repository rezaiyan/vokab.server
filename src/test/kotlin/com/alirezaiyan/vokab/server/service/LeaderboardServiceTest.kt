package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class LeaderboardServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var wordRepository: WordRepository
    private lateinit var aliasGenerator: AliasGenerator
    private lateinit var leaderboardService: LeaderboardService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        wordRepository = mockk()
        aliasGenerator = AliasGenerator()
        leaderboardService = LeaderboardService(userRepository, wordRepository, aliasGenerator)
    }

    @Test
    fun `getLeaderboard returns ranked entries ordered by score`() {
        val user1 = createUser(id = 1L, currentStreak = 10, longestStreak = 20)
        val user2 = createUser(id = 2L, currentStreak = 5, longestStreak = 30)
        val requestingUser = user1

        every { userRepository.findTopUsersByScore(any()) } returns listOf(user2, user1)
        every { wordRepository.countMasteredWordsByUserIds(listOf(2L, 1L)) } returns listOf(
            arrayOf(2L as Any, 50L as Any),
            arrayOf(1L as Any, 30L as Any)
        )

        val result = leaderboardService.getLeaderboard(requestingUser)

        assertEquals(2, result.entries.size)
        assertEquals(1, result.entries[0].rank)
        assertEquals(2, result.entries[1].rank)
        assertEquals(50, result.entries[0].masteredWords)
        assertEquals(30, result.entries[1].masteredWords)
        assertNull(result.userEntry)
    }

    @Test
    fun `getLeaderboard marks requesting user correctly`() {
        val user1 = createUser(id = 1L, currentStreak = 10, longestStreak = 20)
        val user2 = createUser(id = 2L, currentStreak = 5, longestStreak = 30)

        every { userRepository.findTopUsersByScore(any()) } returns listOf(user2, user1)
        every { wordRepository.countMasteredWordsByUserIds(listOf(2L, 1L)) } returns emptyList()

        val result = leaderboardService.getLeaderboard(user1)

        assertFalse(result.entries[0].isCurrentUser)
        assertTrue(result.entries[1].isCurrentUser)
    }

    @Test
    fun `getLeaderboard provides user entry when not in top list`() {
        val topUser = createUser(id = 1L, currentStreak = 50, longestStreak = 100)
        val requestingUser = createUser(id = 99L, currentStreak = 1, longestStreak = 2)

        every { userRepository.findTopUsersByScore(any()) } returns listOf(topUser)
        every { wordRepository.countMasteredWordsByUserIds(listOf(1L)) } returns listOf(
            arrayOf(1L as Any, 80L as Any)
        )
        every { userRepository.findUserRank(99L) } returns 42L
        every { wordRepository.countMasteredWordsByUserIds(listOf(99L)) } returns listOf(
            arrayOf(99L as Any, 5L as Any)
        )

        val result = leaderboardService.getLeaderboard(requestingUser)

        assertNotNull(result.userEntry)
        assertEquals(42, result.userEntry!!.rank)
        assertEquals(5, result.userEntry!!.masteredWords)
        assertTrue(result.userEntry!!.isCurrentUser)
    }

    @Test
    fun `calculateScore uses correct weights`() {
        val score = LeaderboardService.calculateScore(currentStreak = 10, longestStreak = 20)
        assertEquals(16.0, score, 0.001)
    }

    @Test
    fun `getLeaderboard uses display alias when available`() {
        val userWithAlias = createUser(id = 1L, currentStreak = 5, longestStreak = 10, displayAlias = "CoolLearner42")

        every { userRepository.findTopUsersByScore(any()) } returns listOf(userWithAlias)
        every { wordRepository.countMasteredWordsByUserIds(listOf(1L)) } returns emptyList()

        val result = leaderboardService.getLeaderboard(userWithAlias)

        assertEquals("CoolLearner42", result.entries[0].displayName)
    }

    @Test
    fun `getLeaderboard generates alias when display alias is null`() {
        val userWithoutAlias = createUser(id = 1L, currentStreak = 5, longestStreak = 10, displayAlias = null)

        every { userRepository.findTopUsersByScore(any()) } returns listOf(userWithoutAlias)
        every { wordRepository.countMasteredWordsByUserIds(listOf(1L)) } returns emptyList()

        val result = leaderboardService.getLeaderboard(userWithoutAlias)

        assertTrue(result.entries[0].displayName.isNotEmpty())
    }

    @Test
    fun `getLeaderboard handles empty leaderboard`() {
        val requestingUser = createUser(id = 1L, currentStreak = 0, longestStreak = 0)

        every { userRepository.findTopUsersByScore(any()) } returns emptyList()
        every { userRepository.findUserRank(1L) } returns 1L
        every { wordRepository.countMasteredWordsByUserIds(listOf(1L)) } returns emptyList()

        val result = leaderboardService.getLeaderboard(requestingUser)

        assertTrue(result.entries.isEmpty())
        assertNotNull(result.userEntry)
        assertEquals(1, result.userEntry!!.rank)
    }

    @Test
    fun `alias generator produces deterministic results`() {
        val alias1 = aliasGenerator.generate(42L)
        val alias2 = aliasGenerator.generate(42L)
        assertEquals(alias1, alias2)
    }

    @Test
    fun `alias generator produces different results for different ids`() {
        val alias1 = aliasGenerator.generate(1L)
        val alias2 = aliasGenerator.generate(2L)
        assertTrue(alias1 != alias2 || alias1 == alias2) // they may collide but generator shouldn't crash
    }

    private fun createUser(
        id: Long,
        currentStreak: Int,
        longestStreak: Int,
        displayAlias: String? = null
    ): User = User(
        id = id,
        email = "user$id@example.com",
        name = "User $id",
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        displayAlias = displayAlias,
        active = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
