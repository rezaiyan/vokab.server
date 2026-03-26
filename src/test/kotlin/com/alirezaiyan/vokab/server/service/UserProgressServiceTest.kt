package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.Word
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class UserProgressServiceTest {

    private lateinit var wordRepository: WordRepository
    private lateinit var userProgressService: UserProgressService

    @BeforeEach
    fun setUp() {
        wordRepository = mockk()
        userProgressService = UserProgressService(wordRepository)
    }

    @Test
    fun `should return all zeros for user with no words`() {
        // Arrange
        val user = createUser()
        every { wordRepository.findAllByUser(user) } returns emptyList()

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(0, result.totalWords)
        assertEquals(0, result.dueCards)
        assertEquals(0, result.level0Count)
        assertEquals(0, result.level1Count)
        assertEquals(0, result.level2Count)
        assertEquals(0, result.level3Count)
        assertEquals(0, result.level4Count)
        assertEquals(0, result.level5Count)
        assertEquals(0, result.level6Count)
        verify(exactly = 1) { wordRepository.findAllByUser(user) }
    }

    @Test
    fun `should count words by level correctly`() {
        // Arrange
        val user = createUser()
        val words = listOf(
            createWord(level = 0),
            createWord(level = 0),
            createWord(level = 1),
            createWord(level = 3),
            createWord(level = 6)
        )
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(2, result.level0Count)
        assertEquals(1, result.level1Count)
        assertEquals(0, result.level2Count)
        assertEquals(1, result.level3Count)
        assertEquals(0, result.level4Count)
        assertEquals(0, result.level5Count)
        assertEquals(1, result.level6Count)
    }

    @Test
    fun `should count due cards when nextReviewDate is in the past`() {
        // Arrange
        val user = createUser()
        val pastDate = System.currentTimeMillis() - 100_000L
        val words = listOf(
            createWord(level = 0, nextReviewDate = pastDate),
            createWord(level = 1, nextReviewDate = pastDate),
            createWord(level = 2, nextReviewDate = pastDate)
        )
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(3, result.dueCards)
    }

    @Test
    fun `should not count non-due cards when nextReviewDate is in the future`() {
        // Arrange
        val user = createUser()
        val futureDate = System.currentTimeMillis() + 100_000L
        val words = listOf(
            createWord(level = 0, nextReviewDate = futureDate),
            createWord(level = 1, nextReviewDate = futureDate)
        )
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(0, result.dueCards)
    }

    @Test
    fun `should coerce word level below 0 to level 0`() {
        // Arrange
        val user = createUser()
        val words = listOf(
            createWord(level = -1),
            createWord(level = -5)
        )
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(2, result.level0Count)
        assertEquals(0, result.level1Count)
    }

    @Test
    fun `should coerce word level above 6 to level 6`() {
        // Arrange
        val user = createUser()
        val words = listOf(
            createWord(level = 7),
            createWord(level = 99)
        )
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(2, result.level6Count)
        assertEquals(0, result.level5Count)
    }

    @Test
    fun `should count total words correctly`() {
        // Arrange
        val user = createUser()
        val words = List(10) { createWord(level = it % 7) }
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(10, result.totalWords)
    }

    @Test
    fun `should handle words at all levels`() {
        // Arrange
        val user = createUser()
        val pastDate = System.currentTimeMillis() - 100_000L
        val futureDate = System.currentTimeMillis() + 100_000L
        val words = listOf(
            createWord(level = 0, nextReviewDate = pastDate),
            createWord(level = 1, nextReviewDate = futureDate),
            createWord(level = 2, nextReviewDate = pastDate),
            createWord(level = 3, nextReviewDate = futureDate),
            createWord(level = 4, nextReviewDate = pastDate),
            createWord(level = 5, nextReviewDate = futureDate),
            createWord(level = 6, nextReviewDate = pastDate)
        )
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(7, result.totalWords)
        assertEquals(4, result.dueCards)
        assertEquals(1, result.level0Count)
        assertEquals(1, result.level1Count)
        assertEquals(1, result.level2Count)
        assertEquals(1, result.level3Count)
        assertEquals(1, result.level4Count)
        assertEquals(1, result.level5Count)
        assertEquals(1, result.level6Count)
    }

    @Test
    fun `should count due cards correctly when mixed past and future dates`() {
        // Arrange
        val user = createUser()
        val pastDate = System.currentTimeMillis() - 100_000L
        val futureDate = System.currentTimeMillis() + 100_000L
        val words = listOf(
            createWord(level = 0, nextReviewDate = pastDate),
            createWord(level = 0, nextReviewDate = futureDate),
            createWord(level = 1, nextReviewDate = pastDate)
        )
        every { wordRepository.findAllByUser(user) } returns words

        // Act
        val result = userProgressService.calculateProgressStats(user)

        // Assert
        assertEquals(2, result.dueCards)
        assertEquals(3, result.totalWords)
    }

    // --- factory functions ---

    private fun createUser(
        id: Long = 1L,
        email: String = "test@example.com"
    ): User = User(
        id = id,
        email = email,
        name = "Test User",
        currentStreak = 0,
        longestStreak = 0,
        active = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun createWord(
        id: Long = 1L,
        level: Int = 0,
        nextReviewDate: Long = System.currentTimeMillis() + 100_000L,
        user: User? = null
    ): Word {
        val word = Word(
            id = id,
            level = level,
            nextReviewDate = nextReviewDate,
            originalWord = "word",
            translation = "translation",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        word.user = user
        return word
    }
}
