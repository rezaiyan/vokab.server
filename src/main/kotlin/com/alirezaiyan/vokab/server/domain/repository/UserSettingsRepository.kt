package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.UserSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserSettingsRepository : JpaRepository<UserSettings, Long> {
    fun findByUser(user: User): UserSettings?

    @Query("SELECT s FROM UserSettings s JOIN FETCH s.user WHERE s.notificationsEnabled = true")
    fun findAllWithNotificationsEnabled(): List<UserSettings>
}


