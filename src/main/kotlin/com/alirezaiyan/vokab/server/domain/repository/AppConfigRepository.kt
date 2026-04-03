package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.AppConfig
import com.alirezaiyan.vokab.server.domain.entity.AppConfigHistory
import org.springframework.data.jpa.repository.JpaRepository

interface AppConfigRepository : JpaRepository<AppConfig, Long> {
    fun findByNamespaceAndKey(namespace: String, key: String): AppConfig?
    fun findByNamespaceAndKeyAndEnabledTrue(namespace: String, key: String): AppConfig?
    fun findAllByOrderByNamespaceAscKeyAsc(): List<AppConfig>
}

interface AppConfigHistoryRepository : JpaRepository<AppConfigHistory, Long> {
    fun findByNamespaceAndKeyOrderByChangedAtDesc(namespace: String, key: String): List<AppConfigHistory>
}
