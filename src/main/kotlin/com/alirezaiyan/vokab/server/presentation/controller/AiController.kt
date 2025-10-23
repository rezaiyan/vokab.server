package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.presentation.dto.*
import com.alirezaiyan.vokab.server.service.OpenRouterService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/ai")
class AiController(
    private val openRouterService: OpenRouterService,
    private val rateLimitConfig: com.alirezaiyan.vokab.server.config.RateLimitConfig,
    private val featureAccessService: com.alirezaiyan.vokab.server.service.FeatureAccessService,
    private val userRepository: com.alirezaiyan.vokab.server.domain.repository.UserRepository,
    private val appProperties: AppProperties,
    private val userProgressService: com.alirezaiyan.vokab.server.service.UserProgressService,
    private val dailyInsightService: com.alirezaiyan.vokab.server.service.DailyInsightService
) {
    
    @PostMapping("/extract-vocabulary")
    fun extractVocabulary(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: ExtractVocabularyRequest
    ): ResponseEntity<ApiResponse<VocabularyExtractionResponse>> {
        logger.info { "🎯 [AI Controller] User ${user.email} requesting vocabulary extraction" }
        logger.info { "👤 [AI Controller] User status: subscriptionStatus=${user.subscriptionStatus}, usageCount=${user.aiExtractionUsageCount}" }
        
        // Check if feature is enabled and user has access (including free tier limit)
        val canUse = featureAccessService.canUseAiImageExtraction(user)
        logger.info { "🔐 [AI Controller] Feature access check: canUse=$canUse" }
        
        if (!canUse) {
            val remaining = featureAccessService.getRemainingAiExtractionUsages(user)
            val limit = appProperties.features.freeAiExtractionLimit
            
            val message = if (remaining == 0) {
                "You've used all $limit free AI extractions. Upgrade to Premium for unlimited access."
            } else {
                "Premium subscription required to use AI image extraction"
            }
            
            logger.warn { "❌ [AI Controller] User ${user.email} DENIED access. Remaining: $remaining/$limit" }
            logger.warn { "📤 [Response] Sending HTTP 403 Forbidden to client" }
            return ResponseEntity.status(403)
                .body(ApiResponse(
                    success = false, 
                    message = message
                ))
        }
        
        logger.info { "✅ [AI Controller] User ${user.email} has access, proceeding with extraction" }
        
        // Check rate limit
        val bucket = rateLimitConfig.getImageProcessingBucket(user.id.toString())
        if (!bucket.tryConsume(1)) {
            logger.warn { "Rate limit exceeded for user ${user.email} on image processing" }
            return ResponseEntity.status(429)
                .body(ApiResponse(success = false, message = "Rate limit exceeded. Please try again later."))
        }
        
        // Process synchronously to preserve SecurityContext throughout the entire request
        return try {
            logger.info { "🔄 [AI Controller] Executing AI extraction synchronously to preserve SecurityContext" }
            
            val extractedText = openRouterService.extractVocabularyFromImage(
                imageBase64 = request.imageBase64,
                targetLanguage = request.targetLanguage,
                extractWords = request.extractWords,
                extractSentences = request.extractSentences
            ).block() ?: throw RuntimeException("Failed to extract vocabulary")
            
            // Increment usage count for free users
            featureAccessService.incrementAiExtractionUsage(user)
            userRepository.save(user)
            
            logger.info { "AI extraction successful for ${user.email}. Usage: ${user.aiExtractionUsageCount}/${appProperties.features.freeAiExtractionLimit}" }
            
            val wordCount = extractedText.split(";").size
            val remaining = featureAccessService.getRemainingAiExtractionUsages(user)
            
            val response = VocabularyExtractionResponse(
                extractedText = extractedText,
                wordCount = wordCount,
                aiExtractionUsageCount = user.aiExtractionUsageCount,
                aiExtractionUsageLimit = appProperties.features.freeAiExtractionLimit,
                remainingAiExtractions = remaining
            )
            
            logger.info { "📊 [Response] Usage stats: ${user.aiExtractionUsageCount}/${appProperties.features.freeAiExtractionLimit}, remaining: $remaining" }
            
            logger.info { "📤 [Response] Sending HTTP 200 OK to client with $wordCount words" }
            logger.info { "📤 [Response] Success: true, Data: ${extractedText.take(50)}..." }
            logger.info { "✅ [Response] Returning synchronous response to client NOW" }
            
            ResponseEntity.ok(ApiResponse(success = true, data = response))
            
        } catch (error: Exception) {
            logger.error(error) { "❌ [Error] Failed to extract vocabulary for user ${user.email}" }
            logger.error { "❌ [Error] Error type: ${error.javaClass.simpleName}" }
            logger.error { "❌ [Error] Error message: ${error.message}" }
            logger.error { "📤 [Response] Sending HTTP 400 Bad Request to client" }
            
            ResponseEntity.badRequest()
                .body(ApiResponse<VocabularyExtractionResponse>(
                    success = false, 
                    message = error.message ?: "Failed to extract vocabulary"
                ))
        }
    }
    
    @GetMapping("/generate-insight")
    fun generateInsight(
        @AuthenticationPrincipal user: User
    ): Mono<ResponseEntity<ApiResponse<InsightResponse>>> {
        logger.info { "User ${user.email} requesting daily insight (fallback)" }
        
        // Check if feature is enabled and user has access
        if (!featureAccessService.canUseAiDailyInsight(user)) {
            logger.warn { "User ${user.email} attempted to use AI daily insight without premium access" }
            return Mono.just(
                ResponseEntity.status(403)
                    .body(ApiResponse(
                        success = false,
                        message = "Premium subscription required to use AI insights"
                    ))
            )
        }
        
        // Check rate limit
        val bucket = rateLimitConfig.getAiBucket(user.id.toString())
        if (!bucket.tryConsume(1)) {
            logger.warn { "Rate limit exceeded for user ${user.email} on AI service" }
            return Mono.just(
                ResponseEntity.status(429)
                    .body(ApiResponse(success = false, message = "Rate limit exceeded. Please try again later."))
            )
        }
        
        // Try to get today's insight first (if it was already generated)
        val todaysInsight = dailyInsightService.getTodaysInsightForUser(user)
        if (todaysInsight != null) {
            logger.info { "Returning existing daily insight for user ${user.email}" }
            return Mono.just(
                ResponseEntity.ok(
                    ApiResponse(
                        success = true,
                        data = InsightResponse(
                            insight = todaysInsight.insightText,
                            generatedAt = todaysInsight.generatedAt.toString()
                        )
                    )
                )
            )
        }
        
        // Generate new insight if none exists for today
        logger.info { "Generating new daily insight for user ${user.email}" }
        
        // Calculate actual progress stats from user's vocabulary data
        val progressStats = userProgressService.calculateProgressStats(user)
        
        return openRouterService.generateDailyInsight(progressStats)
            .map { insight ->
                val response = InsightResponse(
                    insight = insight,
                    generatedAt = Instant.now().toString()
                )
                ResponseEntity.ok(ApiResponse(success = true, data = response))
            }
            .onErrorResume { error ->
                logger.error(error) { "Failed to generate insight for user ${user.email}" }
                Mono.just(
                    ResponseEntity.badRequest()
                        .body(ApiResponse(success = false, message = error.message ?: "Failed to generate insight"))
                )
            }
    }
    
    @GetMapping("/health")
    fun healthCheck(@AuthenticationPrincipal user: User): ResponseEntity<ApiResponse<Map<String, String>>> {
        return ResponseEntity.ok(
            ApiResponse(
                success = true, 
                data = mapOf(
                    "service" to "AI Service",
                    "status" to "operational",
                    "model" to "anthropic/claude-3.5-sonnet"
                )
            )
        )
    }
}

