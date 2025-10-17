package com.alirezaiyan.vokab.server.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "words", indexes = [
    Index(name = "idx_words_user_id", columnList = "user_id"),
    Index(name = "idx_words_level", columnList = "level")
])
class Word(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(nullable = false)
    var originalWord: String = "",

    @Column(nullable = false)
    var translation: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String = "",

    @Column(nullable = false)
    var level: Int = 0,

    @Column(nullable = false)
    var easeFactor: Float = 2.5f,

    @Column(name = "review_interval", nullable = false)
    var interval: Int = 0,

    @Column(nullable = false)
    var repetitions: Int = 0,

    @Column(nullable = false)
    var lastReviewDate: Long = 0L,

    @Column(nullable = false)
    var nextReviewDate: Long = 0L,
)


