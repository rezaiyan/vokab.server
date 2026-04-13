package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.entity.UserPlatform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserPlatformRepository : JpaRepository<UserPlatform, Long> {
    fun findByUser(user: User): List<UserPlatform>

    @Modifying
    @Query(
        """
        INSERT INTO user_platforms (user_id, platform, first_seen_at, last_seen_at, app_version)
        VALUES (:userId, :platform, NOW(), NOW(), :appVersion)
        ON CONFLICT ON CONSTRAINT uq_user_platform DO UPDATE SET
            last_seen_at = NOW(),
            app_version = :appVersion
        """,
        nativeQuery = true
    )
    fun upsertPlatform(
        @Param("userId") userId: Long,
        @Param("platform") platform: String,
        @Param("appVersion") appVersion: String?
    ): Int
}
