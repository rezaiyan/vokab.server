package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.Word
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.alirezaiyan.vokab.server.presentation.dto.WordDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WordService(
    private val wordRepository: WordRepository
) {
    fun list(user: User): List<WordDto> {
        return wordRepository.findAllByUser(user).map { it.toDto() }
    }

    @Transactional
    fun upsert(user: User, words: List<WordDto>) {
        val entities = words.map { dto ->
            val entity = Word(
                id = dto.id,
                user = user,
                originalWord = dto.originalWord,
                translation = dto.translation,
                description = dto.description,
                level = dto.level,
                easeFactor = dto.easeFactor,
                interval = dto.interval,
                repetitions = dto.repetitions,
                lastReviewDate = dto.lastReviewDate,
                nextReviewDate = dto.nextReviewDate
            )
            entity
        }
        wordRepository.saveAll(entities)
    }

    @Transactional
    fun update(user: User, id: Long, dto: WordDto) {
        val entity = wordRepository.findById(id).orElseThrow()
        require(entity.user?.id == user.id) { "Forbidden" }
        entity.originalWord = dto.originalWord
        entity.translation = dto.translation
        entity.description = dto.description
        entity.level = dto.level
        entity.easeFactor = dto.easeFactor
        entity.interval = dto.interval
        entity.repetitions = dto.repetitions
        entity.lastReviewDate = dto.lastReviewDate
        entity.nextReviewDate = dto.nextReviewDate
        wordRepository.save(entity)
    }

    @Transactional
    fun delete(user: User, id: Long) {
        val entity = wordRepository.findById(id).orElseThrow()
        require(entity.user?.id == user.id) { "Forbidden" }
        wordRepository.delete(entity)
    }
}

private fun Word.toDto(): WordDto = WordDto(
    id = id,
    originalWord = originalWord,
    translation = translation,
    description = description,
    level = level,
    easeFactor = easeFactor,
    interval = interval,
    repetitions = repetitions,
    lastReviewDate = lastReviewDate,
    nextReviewDate = nextReviewDate,
)


