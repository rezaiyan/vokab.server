package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.DailyActivity
import com.alirezaiyan.vokab.server.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface DailyActivityRepository : JpaRepository<DailyActivity, Long> {
    
    fun findByUserAndActivityDate(user: User, activityDate: LocalDate): Optional<DailyActivity>
    
    @Query("""
        SELECT da FROM DailyActivity da 
        WHERE da.user = :user 
        AND da.activityDate >= :startDate 
        ORDER BY da.activityDate DESC
    """)
    fun findRecentActivities(user: User, startDate: LocalDate): List<DailyActivity>
    
    @Query("""
        SELECT da FROM DailyActivity da 
        WHERE da.user = :user 
        ORDER BY da.activityDate DESC
    """)
    fun findAllByUserOrderByActivityDateDesc(user: User): List<DailyActivity>
}


