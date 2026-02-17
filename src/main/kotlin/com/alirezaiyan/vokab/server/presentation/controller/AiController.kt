package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.config.RateLimitConfig
import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.domain.repository.WordRepository
import com.alirezaiyan.vokab.server.presentation.dto.*
import com.alirezaiyan.vokab.server.service.DailyInsightService
import com.alirezaiyan.vokab.server.service.FeatureAccessService
import com.alirezaiyan.vokab.server.service.OpenRouterService
import com.alirezaiyan.vokab.server.service.UserProgressService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/ai")
class AiController(
    private val openRouterService: OpenRouterService,
    private val rateLimitConfig: RateLimitConfig,
    private val featureAccessService: FeatureAccessService,
    private val userRepository: UserRepository,
    private val appProperties: AppProperties,
    private val userProgressService: UserProgressService,
    private val dailyInsightService: DailyInsightService
) {
    
    @PostMapping("/extract-vocabulary")
    fun extractVocabulary(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: ExtractVocabularyRequest
    ): ResponseEntity<ApiResponse<VocabularyExtractionResponse>> {
        logger.info { "🎯 [AI Controller] User ${user.email} requesting vocabulary extraction" }
        logger.info { "👤 [AI Controller] User status: subscriptionStatus=${user.subscriptionStatus}" }

        // Check if user has premium access
        val hasPremiumAccess = featureAccessService.hasActivePremiumAccess(user)
        logger.info { "🔐 [AI Controller] Premium access check: hasPremiumAccess=$hasPremiumAccess" }

        if (!hasPremiumAccess) {
            logger.warn { "❌ [AI Controller] User ${user.email} DENIED access - Premium required" }
            logger.warn { "📤 [Response] Sending HTTP 403 Forbidden to client" }
            return ResponseEntity.status(403)
                .body(ApiResponse(
                    success = false,
                    message = "Premium subscription required to use AI image extraction"
                ))
        }

        logger.info { "✅ [AI Controller] User ${user.email} has premium access, proceeding with extraction" }

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

            val wordCount = extractedText.split(";").size

            val response = VocabularyExtractionResponse(
                extractedText = extractedText,
                wordCount = wordCount
            )

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

        // Check if user has premium access
        if (!featureAccessService.hasActivePremiumAccess(user)) {
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
    
    @PostMapping("/translate-text")
    fun translateText(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: TranslateTextRequest
    ): ResponseEntity<ApiResponse<TranslateTextResponse>> {
        logger.info { "🎯 [AI Controller] User ${user.email} requesting text translation" }
        
        val text = request.text.trim()
        
        if (text.isEmpty() || text.isBlank()) {
            logger.warn { "❌ [AI Controller] Empty or whitespace-only text provided" }
            return ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Text cannot be empty"))
        }
        
        if (text.length > 200) {
            logger.warn { "❌ [AI Controller] Text too long: ${text.length} characters" }
            return ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Text cannot exceed 200 characters"))
        }
        
        val lineCount = text.split("\n").size
        if (lineCount > 2) {
            logger.warn { "❌ [AI Controller] Text has too many lines: $lineCount" }
            return ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = "Text cannot exceed 2 lines"))
        }
        
        val bucket = rateLimitConfig.getAiBucket(user.id.toString())
        if (!bucket.tryConsume(1)) {
            logger.warn { "Rate limit exceeded for user ${user.email} on text translation" }
            return ResponseEntity.status(429)
                .body(ApiResponse(success = false, message = "Rate limit exceeded. Please try again later."))
        }
        
        return try {
            logger.info { "🔄 [AI Controller] Translating text synchronously" }
            
            val translation = openRouterService.translateText(
                text = text,
                targetLanguage = request.targetLanguage
            ).block() ?: throw RuntimeException("Failed to translate text")
            
            logger.info { "✅ [AI Controller] Translation successful for user ${user.email}" }
            
            val response = TranslateTextResponse(
                originalText = text,
                translation = translation
            )
            
            ResponseEntity.ok(ApiResponse(success = true, data = response))
            
        } catch (error: Exception) {
            logger.error(error) { "❌ [Error] Failed to translate text for user ${user.email}" }
            logger.error { "❌ [Error] Error type: ${error.javaClass.simpleName}" }
            logger.error { "❌ [Error] Error message: ${error.message}" }
            
            ResponseEntity.badRequest()
                .body(ApiResponse(
                    success = false, 
                    message = error.message ?: "Failed to translate text"
                ))
        }
    }
    
    @Autowired
    lateinit var wordRepository: WordRepository

    @PostMapping("/suggest-vocabulary")
    fun suggestVocabulary(
        @AuthenticationPrincipal user: User,
        @Valid @RequestBody request: SuggestVocabularyRequest
    ): ResponseEntity<ApiResponse<SuggestVocabularyResponse>> {
        logger.info { "User ${user.email} requesting suggested vocabulary: target=${request.targetLanguage}, level=${request.currentLevel}, native=${request.nativeLanguage}" }

        val bucket = rateLimitConfig.getAiBucket(user.id.toString())
        if (!bucket.tryConsume(1)) {
            logger.warn { "Rate limit exceeded for user ${user.email} on suggest-vocabulary" }
            return ResponseEntity.status(429)
                .body(ApiResponse(success = false, message = "Rate limit exceeded. Please try again later."))
        }
        
        return try {
            val targetLanguage = request.targetLanguage.trim()
            val nativeLanguage = request.nativeLanguage.trim()
            val currentLevel = request.currentLevel.trim()

            // Existing vocabulary for this user in the same target language.
            // NOTE: In the current data model, Word.originalWord is typically in the user's
            // native language, and Word.translation is in the target language.
            // Our AI suggestions, however, use:
            //   - originalWord  = target language
            //   - translation  = native language
            // So for deduplication we must compare suggestion.originalWord (target)
            // against existing.translation (target).
            val existingWords = wordRepository.findAllByUser(user)
                .filter { it.targetLanguage.equals(targetLanguage, ignoreCase = true) }
            val existingKeys = existingWords
                .mapNotNull { existing ->
                    existing.translation.trim()
                        .takeIf { it.isNotEmpty() }
                        ?.lowercase()
                }
                .toSet()

            // Raw AI-generated suggestions (may contain duplicates and overlaps)
            val rawItems = openRouterService.generateVocabularyFromPreferences(
                targetLanguage = targetLanguage,
                currentLevel = currentLevel,
                nativeLanguage = nativeLanguage,
                interests = emptyList()
            ).block() ?: emptyList()

            val baseCount = appProperties.vocabulary.suggestionCount

            // Filter out:
            // 1) Words the user already has for this target language
            // 2) Duplicates inside the AI response itself (by normalized originalWord)
            val seenNew = mutableSetOf<String>()
            val uniqueNewItems = rawItems.filter { item ->
                val key = item.originalWord.trim().lowercase()
                if (key.isBlank()) return@filter false
                if (existingKeys.contains(key)) return@filter false
                seenNew.add(key)
            }.take(baseCount)

            logger.info {
                "Suggest-vocabulary dedup for user ${user.email}: " +
                    "existing=${existingKeys.size}, raw=${rawItems.size}, " +
                    "uniqueNew=${uniqueNewItems.size}, targetCount=$baseCount"
            }

            val response = SuggestVocabularyResponse(
                targetLanguage = targetLanguage,
                nativeLanguage = nativeLanguage,
                currentLevel = currentLevel,
                items = uniqueNewItems
            )
            ResponseEntity.ok(ApiResponse(success = true, data = response))
        } catch (error: Exception) {
            logger.error(error) { "Failed to suggest vocabulary for user ${user.email}" }
            ResponseEntity.badRequest()
                .body(ApiResponse(success = false, message = error.message ?: "Failed to generate vocabulary"))
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

