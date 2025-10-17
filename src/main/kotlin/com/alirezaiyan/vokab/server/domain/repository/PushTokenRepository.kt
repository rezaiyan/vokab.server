package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.PushToken
import com.alirezaiyan.vokab.server.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PushTokenRepository : JpaRepository<PushToken, Long> {
    fun findByToken(token: String): Optional<PushToken>
    fun findByUserAndActiveTrue(user: User): List<PushToken>
    fun findByUser(user: User): List<PushToken>
    
    @Modifying
    @Query("UPDATE PushToken p SET p.active = false WHERE p.token = :token")
    fun deactivateByToken(token: String): Int
    
    @Modifying
    @Query("UPDATE PushToken p SET p.active = false WHERE p.user = :user")
    fun deactivateAllByUser(user: User): Int
}

