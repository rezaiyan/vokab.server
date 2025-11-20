package com.alirezaiyan.vokab.server.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for audit logging of authentication events
 * Logs login, refresh, logout, revocation, and account deletion events
 */
@Service
class AuditLogService {
    
    fun logLogin(userId: Long, email: String, provider: String, ipAddress: String?, userAgent: String?) {
        logger.info {
            "AUDIT: Login event - userId=$userId, email=$email, provider=$provider, " +
            "ipAddress=$ipAddress, userAgent=$userAgent, timestamp=${Instant.now()}"
        }
    }
    
    fun logRefresh(userId: Long, email: String, familyId: String, ipAddress: String?, userAgent: String?) {
        logger.info {
            "AUDIT: Token refresh event - userId=$userId, email=$email, familyId=$familyId, " +
            "ipAddress=$ipAddress, userAgent=$userAgent, timestamp=${Instant.now()}"
        }
    }
    
    fun logLogout(userId: Long, email: String, refreshToken: String, ipAddress: String?) {
        logger.info {
            "AUDIT: Logout event - userId=$userId, email=$email, refreshToken=${refreshToken.take(8)}..., " +
            "ipAddress=$ipAddress, timestamp=${Instant.now()}"
        }
    }
    
    fun logLogoutAll(userId: Long, email: String, ipAddress: String?) {
        logger.info {
            "AUDIT: Logout all sessions event - userId=$userId, email=$email, " +
            "ipAddress=$ipAddress, timestamp=${Instant.now()}"
        }
    }
    
    fun logTokenRevocation(userId: Long, familyId: String, reason: String, ipAddress: String?) {
        logger.warn {
            "AUDIT: Token revocation event - userId=$userId, familyId=$familyId, reason=$reason, " +
            "ipAddress=$ipAddress, timestamp=${Instant.now()}"
        }
    }
    
    fun logAccountDeletion(userId: Long, email: String, ipAddress: String?) {
        logger.warn {
            "AUDIT: Account deletion event - userId=$userId, email=$email, " +
            "ipAddress=$ipAddress, timestamp=${Instant.now()}"
        }
    }
    
    fun logTokenReuse(userId: Long, familyId: String, ipAddress: String?) {
        logger.error {
            "AUDIT: Token reuse detected (security violation) - userId=$userId, familyId=$familyId, " +
            "ipAddress=$ipAddress, timestamp=${Instant.now()}"
        }
    }
}


