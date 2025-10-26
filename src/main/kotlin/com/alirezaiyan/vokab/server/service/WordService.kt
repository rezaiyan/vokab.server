package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.Word
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.alirezaiyan.vokab.server.presentation.dto.WordDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WordService(
    private val wordRepository: WordRepository,
    private val wordUpsertPreparer: WordUpsertPreparer
) {
    fun list(user: User): List<WordDto> {
        return wordRepository.findAllByUser(user).map { it.toDto() }
    }

    @Transactional
    fun upsert(user: User, words: List<WordDto>) {
        if (words.isEmpty()) return

        val entities = wordUpsertPreparer.prepareUpsertEntities(user, words)
        wordRepository.saveAll(entities)
    }

    @Transactional
    fun update(user: User, id: Long, dto: WordDto) {
        val entity = wordRepository.findById(id).orElseThrow()
        require(entity.user?.id == user.id) { "Forbidden" }
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
        wordRepository.save(entity)
    }

    @Transactional
    fun delete(user: User, id: Long) {
        val entity = wordRepository.findById(id).orElseThrow()
        require(entity.user?.id == user.id) { "Forbidden" }
        wordRepository.delete(entity)
    }
    
    @Transactional
    fun batchDelete(user: User, ids: List<Long>) {
        if (ids.isEmpty()) return
        
        // Fetch all words to delete and verify ownership
        val wordsToDelete = wordRepository.findAllById(ids)
        
        // Verify all words belong to the user
        wordsToDelete.forEach { word ->
            require(word.user?.id == user.id) { "Forbidden: Cannot delete word ${word.id}" }
        }
        
        // Delete all in batch
        wordRepository.deleteAll(wordsToDelete)
    }
}

private fun Word.toDto(): WordDto = WordDto(
    id = id,
    originalWord = originalWord,
    translation = translation,
    description = description,
    sourceLanguage = sourceLanguage,
    targetLanguage = targetLanguage,
    level = level,
    easeFactor = easeFactor,
    interval = interval,
    repetitions = repetitions,
    lastReviewDate = lastReviewDate,
    nextReviewDate = nextReviewDate,
)


