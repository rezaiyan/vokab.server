package com.alirezaiyan.vokab.server.security

import com.alirezaiyan.vokab.server.config.AppProperties
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
    private val jwtTokenProvider: RS256JwtTokenProvider,
    private val userRepository: UserRepository,
    private val appProperties: AppProperties
) : OncePerRequestFilter() {
    
    // Paths that should skip JWT authentication
    private val excludedPaths = listOf(
        "/api/v1/auth/google",
        "/api/v1/auth/apple", 
        "/api/v1/auth/refresh",
        "/api/v1/auth/jwks",
        "/api/v1/webhooks/",
        "/api/v1/health",
        "/api/v1/version",
        "/api/v1/users/feature-flags",
        "/api/v1/onboarding/",
        "/h2-console/",
        "/actuator/health",
        "/error"
    )
    
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return excludedPaths.any { path.startsWith(it) }
    }
    
    /**
     * Get test emails from configuration
     * Returns a set of emails that should bypass the active check
     */
    private fun getTestEmails(): Set<String> {
        return appProperties.security.testEmails
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        logger.info { "🔐 JWT Filter [START]: Processing ${request.method} $path" }
        
        try {
            val jwt = getJwtFromRequest(request)
            
            if (jwt == null) {
                logger.warn { "❌ JWT Filter [NO_TOKEN]: No JWT token found for $path - returning 401" }
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.writer.write("""{"success":false,"message":"Authentication required"}""")
                response.contentType = "application/json"
                return
            } else {
                logger.info { "🔑 JWT Filter [TOKEN_FOUND]: Token length ${jwt.length} for $path" }
                
                val isValid = jwtTokenProvider.validateToken(jwt)
                logger.info { "🔍 JWT Filter [VALIDATE]: Token valid=$isValid for $path" }
                
                if (isValid) {
                    val userId = jwtTokenProvider.getUserIdFromToken(jwt)
                    logger.info { "👤 JWT Filter [USER_ID]: Extracted user ID=$userId for $path" }
                    
                    if (userId != null) {
                        val testEmails = getTestEmails()
                        val user = userRepository.findById(userId)
                        val isPresent = user.isPresent
                        val userEmail = if (isPresent) user.get().email else null
                        val isTestAccount = userEmail in testEmails
                        val isActive = if (isPresent) user.get().active else false
                        logger.info { "🗄️ JWT Filter [DB_LOOKUP]: User found=$isPresent, active=$isActive, email=$userEmail, testAccount=$isTestAccount for $path" }
                        logger.info { "📧 JWT Filter [TEST_EMAILS]: Configured test emails=$testEmails, user email=$userEmail" }
                        
                        if (isPresent && (isActive || isTestAccount)) {
                            val authentication = UsernamePasswordAuthenticationToken(
                                user.get(),
                                null,
                                emptyList()
                            )
                            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                            
                            SecurityContextHolder.getContext().authentication = authentication
                            logger.info { "✅ JWT Filter [AUTH_SUCCESS]: Set authentication for user=$userId, testAccount=$isTestAccount for $path" }
                            logger.info { "🔄 JWT Filter [FILTER_CHAIN]: Proceeding to next filter for $path" }
                        } else {
                            logger.warn { "❌ JWT Filter [AUTH_FAILED]: User not found or inactive for userId=$userId, email=$userEmail, active=$isActive, testAccount=$isTestAccount - returning 403 for $path" }
                            response.status = HttpServletResponse.SC_FORBIDDEN
                            response.writer.write("""{"success":false,"message":"User account has been deleted or deactivated"}""")
                            response.contentType = "application/json"
                            return
                        }
                    } else {
                        logger.warn { "❌ JWT Filter [NO_USER_ID]: Unable to extract user ID from token - returning 401 for $path" }
                        response.status = HttpServletResponse.SC_UNAUTHORIZED
                        response.writer.write("""{"success":false,"message":"Invalid token"}""")
                        response.contentType = "application/json"
                        return
                    }
                } else {
                    logger.warn { "❌ JWT Filter [INVALID_TOKEN]: Token validation failed - returning 401 for $path" }
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.writer.write("""{"success":false,"message":"Invalid or expired token"}""")
                    response.contentType = "application/json"
                    return
                }
            }
        } catch (e: Exception) {
            logger.error { "💥 JWT Filter [EXCEPTION]: Error during authentication for $path - ${e.message}" }
            logger.error { "Stack trace: ${e.stackTraceToString()}" }
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            response.writer.write("""{"success":false,"message":"Authentication error"}""")
            response.contentType = "application/json"
            return
        }
        
        logger.info { "🔄 JWT Filter [CONTINUE]: Calling filter chain for $path" }
        filterChain.doFilter(request, response)
        logger.info { "✅ JWT Filter [END]: Filter chain completed for $path, response status: ${response.status}" }
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

