package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.Word
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.alirezaiyan.vokab.server.presentation.dto.WordDto
import org.springframework.stereotype.Component

@Component
class WordUpsertPreparer(
    private val wordRepository: WordRepository
) {
    fun prepareUpsertEntities(user: User, words: List<WordDto>): Collection<Word> {
        if (words.isEmpty()) return emptyList()

        val existingByKey = loadExistingByKey(user)
        return buildEntitiesToSave(user, words, existingByKey).values
    }

    private fun loadExistingByKey(user: User): Map<WordKey, Word> =
        wordRepository
            .findAllByUser(user)
            .associateBy(
                keySelector = { WordKey(it.originalWord, it.translation) },
                valueTransform = { it }
            )

    private fun buildEntitiesToSave(
        user: User,
        words: List<WordDto>,
        existingByKey: Map<WordKey, Word>
    ): LinkedHashMap<WordKey, Word> {
        val result = LinkedHashMap<WordKey, Word>()
        for (dto in words) {
            val key = WordKey(dto.originalWord, dto.translation)
            val entity = existingByKey[key]?.let { updateEntityFromDto(it, dto) }
                ?: createEntityFromDto(user, dto)
            // last write wins within the same request
            result[key] = entity
        }
        return result
    }

    private fun updateEntityFromDto(entity: Word, dto: WordDto): Word {
        entity.originalWord = dto.originalWord
        entity.translation = dto.translation
        entity.description = dto.description
        entity.sourceLanguage = dto.sourceLanguage
        entity.targetLanguage = dto.targetLanguage
        entity.level = dto.level
        entity.easeFactor = dto.easeFactor
        entity.interval = dto.interval
        entity.repetitions = dto.repetitions
        entity.lastReviewDate = dto.lastReviewDate
        entity.nextReviewDate = dto.nextReviewDate
        return entity
    }

    private fun createEntityFromDto(user: User, dto: WordDto): Word = Word(
        id = dto.id,
        user = user,
        originalWord = dto.originalWord,
        translation = dto.translation,
        description = dto.description,
        sourceLanguage = dto.sourceLanguage,
        targetLanguage = dto.targetLanguage,
        level = dto.level,
        easeFactor = dto.easeFactor,
        interval = dto.interval,
        repetitions = dto.repetitions,
        lastReviewDate = dto.lastReviewDate,
        nextReviewDate = dto.nextReviewDate
    )

    private data class WordKey(val originalWord: String, val translation: String)
}



