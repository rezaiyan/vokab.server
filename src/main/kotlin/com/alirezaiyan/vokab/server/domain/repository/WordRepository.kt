package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.Word
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface WordRepository : JpaRepository<Word, Long> {
    fun findAllByUser(user: User): List<Word>

    @Query(
        "SELECT w.user.id, COUNT(w) FROM Word w " +
            "WHERE w.level = 6 AND w.user.id IN :userIds GROUP BY w.user.id"
    )
    fun countMasteredWordsByUserIds(userIds: List<Long>): List<Array<Any>>

    @Query("SELECT COUNT(w) FROM Word w WHERE w.level = 6 AND w.user.id = :userId")
    fun countMasteredWordsByUserId(userId: Long): Long

    @Query(
        "SELECT w.sourceLanguage, w.targetLanguage, COUNT(w) " +
            "FROM Word w WHERE w.user = :user " +
            "GROUP BY w.sourceLanguage, w.targetLanguage"
    )
    fun findLanguagePairsWithCount(user: User): List<Array<Any>>

    @Modifying
    @Query("DELETE FROM Word w WHERE w.id IN :ids AND w.user.id = :userId")
    fun deleteAllByIdInAndUserId(ids: List<Long>, userId: Long): Int

    @Modifying
    @Query(
        "UPDATE Word w SET w.sourceLanguage = :sourceLanguage, w.targetLanguage = :targetLanguage, " +
            "w.updatedAt = CURRENT_TIMESTAMP WHERE w.id IN :ids AND w.user.id = :userId"
    )
    fun updateLanguagesByIdInAndUserId(
        ids: List<Long>,
        userId: Long,
        sourceLanguage: String,
        targetLanguage: String
    ): Int

    @Modifying
    @Query(
        "UPDATE Word w SET w.sourceLanguage = :sourceLanguage, " +
            "w.updatedAt = CURRENT_TIMESTAMP WHERE w.id IN :ids AND w.user.id = :userId"
    )
    fun updateSourceLanguageByIdInAndUserId(
        ids: List<Long>,
        userId: Long,
        sourceLanguage: String
    ): Int

    @Modifying
    @Query(
        "UPDATE Word w SET w.targetLanguage = :targetLanguage, " +
            "w.updatedAt = CURRENT_TIMESTAMP WHERE w.id IN :ids AND w.user.id = :userId"
    )
    fun updateTargetLanguageByIdInAndUserId(
        ids: List<Long>,
        userId: Long,
        targetLanguage: String
    ): Int
}


