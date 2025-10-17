package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.Subscription
import com.alirezaiyan.vokab.server.domain.entity.SubscriptionStatus
import com.alirezaiyan.vokab.server.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    fun findByRevenueCatSubscriptionId(revenueCatSubscriptionId: String): Optional<Subscription>
    fun findByUser(user: User): List<Subscription>
    fun findByUserAndStatus(user: User, status: SubscriptionStatus): List<Subscription>
    
    @Query("SELECT s FROM Subscription s WHERE s.user = :user AND s.status = 'ACTIVE' ORDER BY s.expiresAt DESC")
    fun findActiveSubscription(user: User): Optional<Subscription>
}

