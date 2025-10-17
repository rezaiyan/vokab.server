package com.alirezaiyan.vokab.server.security

import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private val logger = KotlinLogging.logger {}

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {
    
    // Paths that should skip JWT authentication
    private val excludedPaths = listOf(
        "/api/v1/auth/",
        "/api/v1/webhooks/",
        "/api/v1/health",
        "/api/v1/version",
        "/api/v1/users/feature-flags",
        "/h2-console/",
        "/actuator/health",
        "/error"
    )
    
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return excludedPaths.any { path.startsWith(it) }
    }
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        logger.info { "🔐 JWT Filter: Processing ${request.method} $path" }
        
        try {
            val jwt = getJwtFromRequest(request)
            
            if (jwt == null) {
                logger.warn { "❌ JWT Filter: No JWT token found in Authorization header for $path" }
            } else {
                logger.info { "🔑 JWT Filter: Found JWT token (length: ${jwt.length})" }
                
                val isValid = jwtTokenProvider.validateToken(jwt)
                logger.info { "🔍 JWT Filter: Token validation result: $isValid" }
                
                if (isValid) {
                    val userId = jwtTokenProvider.getUserIdFromToken(jwt)
                    logger.info { "👤 JWT Filter: Extracted user ID: $userId" }
                    
                    userId?.let {
                        val user = userRepository.findById(it)
                        logger.info { "🗄️ JWT Filter: User lookup result: present=${user.isPresent}, active=${user.map { u -> u.active }.orElse(false)}" }
                        
                        if (user.isPresent && user.get().active) {
                            val authentication = UsernamePasswordAuthenticationToken(
                                user.get(),
                                null,
                                emptyList()
                            )
                            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                            
                            SecurityContextHolder.getContext().authentication = authentication
                            logger.info { "✅ JWT Filter: Authentication set successfully for user ID $it" }
                        } else {
                            logger.warn { "❌ JWT Filter: User not found or inactive for ID $it" }
                        }
                    }
                } else {
                    logger.warn { "❌ JWT Filter: Token validation failed" }
                }
            }
        } catch (e: Exception) {
            logger.error { "💥 JWT Filter: Exception during authentication: ${e.message}" }
            logger.error { "Stack trace: ${e.stackTraceToString()}" }
        }
        
        filterChain.doFilter(request, response)
    }
    
    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}

