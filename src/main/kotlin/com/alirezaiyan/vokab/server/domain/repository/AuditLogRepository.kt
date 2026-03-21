package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.AuditLog
import com.alirezaiyan.vokab.server.domain.entity.AuditEventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<AuditLog>
    fun findByEventTypeOrderByCreatedAtDesc(eventType: AuditEventType): List<AuditLog>
}
