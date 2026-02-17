package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.config.AppProperties
import com.alirezaiyan.vokab.server.presentation.dto.ProgressStatsDto
import com.alirezaiyan.vokab.server.presentation.dto.SuggestVocabularyItemResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Service
class OpenRouterService(
    private val appProperties: AppProperties
) {
    
    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(appProperties.openrouter.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${appProperties.openrouter.apiKey}")
            .defaultHeader("HTTP-Referer", "https://vokab.app")
            .defaultHeader("X-Title", "Vokab")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
    
    data class OpenRouterRequest(
        val model: String = "anthropic/claude-3.5-sonnet",
        val messages: List<Message>
    )
    
    data class Message(
        val role: String,
        val content: List<Content>
    )
    
    data class Content(
        val type: String,
        val text: String? = null,
        val image_url: ImageUrl? = null
    )
    
    data class ImageUrl(
        val url: String
    )
    
    data class OpenRouterResponse(
        val choices: List<Choice>?,
        val error: ErrorDetail?
    )
    
    data class Choice(
        val message: MessageContent
    )
    
    data class MessageContent(
        val content: String
    )
    
    data class ErrorDetail(
        val message: String
    )
    
    fun extractVocabularyFromImage(
        imageBase64: String,
        targetLanguage: String,
        extractWords: Boolean = true,
        extractSentences: Boolean = false
    ): Mono<String> {
        logger.info { "Extracting vocabulary from image, target language: $targetLanguage" }
        
        // Validate image size (base64 encoded, so roughly 1.33x original size)
        val estimatedSizeBytes = (imageBase64.length * 0.75).toInt()
        val maxSizeBytes = 5 * 1024 * 1024 // 5MB
        if (estimatedSizeBytes > maxSizeBytes) {
            return Mono.error(IllegalArgumentException("Image too large. Maximum size is 5MB."))
        }
        
        val dataUrl = "data:image/jpeg;base64,$imageBase64"
        
        val extractionType = when {
            extractWords && extractSentences -> "both individual vocabulary words AND example sentences"
            extractWords -> "individual vocabulary words only"
            extractSentences -> "example sentences only"
            else -> "individual vocabulary words only"
        }
        
        val prompt = buildString {
            appendLine("You are a vocabulary extraction specialist. Extract $extractionType from this image.")
            appendLine()
            appendLine("CRITICAL: Verify the image contains vocabulary or text before proceeding.")
            appendLine("If the image does NOT contain any vocabulary, text, or words, respond with exactly: ERROR: No vocabulary found")
            appendLine()
            appendLine("Format Requirements:")
            appendLine("1. Each entry MUST follow: originalWord,translation")
            appendLine("2. originalWord = text in source language from image")
            appendLine("3. translation = $targetLanguage translation")
            appendLine("4. Separate entries with semicolon (;)")
            appendLine("5. Optional third field for description/context")
            appendLine("6. NO markdown, NO code blocks, NO explanations - ONLY the formatted data")
            appendLine("7. Remove any special characters that might break parsing (keep only letters, numbers, spaces, commas, semicolons)")
            appendLine()
            if (extractWords && !extractSentences) {
                appendLine("Extraction Rule: ONLY individual words or short phrases (2-3 words max)")
                appendLine("SKIP: Full sentences, long phrases, paragraphs")
            }
            if (extractSentences && !extractWords) {
                appendLine("Extraction Rule: ONLY phrases")
                appendLine("SKIP: Individual words")
            }
            if (extractWords && extractSentences) {
                appendLine("Extraction Rule: Extract BOTH individual words AND phrases")
            }
            appendLine()
            appendLine("Translation Rule:")
            appendLine("- If image already shows translations to $targetLanguage, use them exactly")
            appendLine("- Otherwise, provide accurate $targetLanguage translations")
            appendLine("- Keep translations concise and natural")
            appendLine()
            appendLine("Quality Checks:")
            appendLine("- Each entry must have at least 2 fields (word,translation)")
            appendLine("- No empty fields before commas")
            appendLine("- NO duplicate entries - verify uniqueness")
            appendLine("- Minimum 1 entry")
            appendLine()
            appendLine("Valid Example:")
            appendLine("Hallo,hello;Guten Morgen,good morning;danke,thanks,thank you very much")
        }
        
        val request = OpenRouterRequest(
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        Content(
                            type = "image_url",
                            image_url = ImageUrl(url = dataUrl)
                        ),
                        Content(
                            type = "text",
                            text = prompt
                        )
                    )
                )
            )
        )
        
        // Log request details (without full image data)
        val apiKeyMasked = appProperties.openrouter.apiKey.take(15) + "..." + appProperties.openrouter.apiKey.takeLast(4)
        logger.info { "🚀 [OpenRouter] Sending request to ${appProperties.openrouter.baseUrl}/chat/completions" }
        logger.info { "🔑 [OpenRouter] API Key (masked): $apiKeyMasked" }
        logger.info { "📦 [OpenRouter] Model: ${request.model}" }
        logger.info { "📝 [OpenRouter] Image size: ~${estimatedSizeBytes / 1024}KB" }
        logger.info { "🌐 [OpenRouter] Target language: $targetLanguage" }
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .onStatus({ status -> status.isError }) { response ->
                response.bodyToMono<String>().flatMap { errorBody ->
                    logger.error { "❌ [OpenRouter] HTTP ${response.statusCode()}: $errorBody" }
                    logger.error { "❌ [OpenRouter] Response headers: ${response.headers().asHttpHeaders()}" }
                    Mono.error(RuntimeException("OpenRouter API error: ${response.statusCode()} - $errorBody"))
                }
            }
            .bodyToMono<OpenRouterResponse>()
            .doOnSubscribe {
                logger.info { "⏳ [OpenRouter] Request sent, waiting for response..." }
            }
            .doOnSuccess { response ->
                logger.info { "✅ [OpenRouter] Received response" }
                logger.debug { "📄 [OpenRouter] Response: $response" }
            }
            .map { response ->
                if (response.error != null) {
                    logger.error { "❌ [OpenRouter] API returned error: ${response.error.message}" }
                    throw RuntimeException("OpenRouter error: ${response.error.message}")
                }
                
                val extractedText = response.choices?.firstOrNull()?.message?.content?.trim() ?: ""
                
                if (extractedText.isEmpty()) {
                    logger.warn { "⚠️ [OpenRouter] Empty response from AI" }
                    throw RuntimeException("No response from AI. Please try again.")
                }
                
                if (extractedText.contains("ERROR:", ignoreCase = true) || 
                    extractedText.contains("No vocabulary found", ignoreCase = true)) {
                    logger.warn { "⚠️ [OpenRouter] AI could not find vocabulary in image" }
                    throw RuntimeException("No vocabulary found in the image. Please use an image with visible text.")
                }
                
                if (!isValidVocabularyFormat(extractedText)) {
                    logger.warn { "⚠️ [OpenRouter] Invalid vocabulary format: $extractedText" }
                    throw RuntimeException("Failed to extract valid vocabulary format. Please try a clearer image.")
                }
                
                logger.info { "✅ [OpenRouter] Successfully extracted vocabulary: ${extractedText.take(100)}..." }
                extractedText
            }
            .doOnError { error ->
                logger.error(error) { "💥 [OpenRouter] Failed to extract vocabulary from image" }
                logger.error { "💥 [OpenRouter] Error type: ${error.javaClass.simpleName}" }
                logger.error { "💥 [OpenRouter] Error message: ${error.message}" }
            }
    }
    
    fun generateDailyInsight(stats: ProgressStatsDto): Mono<String> {
        logger.info { "Generating daily insight for ${stats.totalWords} words" }
        
        val prompt = buildString {
            appendLine("You are a supportive vocabulary learning coach with an encouraging, motivational personality.")
            appendLine("Based on the user's mastery progress, generate ONE brief, uplifting message (max 2 sentences).")
            appendLine()
            appendLine("User's Mastery Journey (7-Level System):")
            appendLine("- Total vocabulary: ${stats.totalWords} words")
            appendLine("- Due for review today: ${stats.dueCards} cards")
            appendLine("- Level 0 (First Encounter): ${stats.level0Count} words")
            appendLine("- Level 1 (Getting Familiar): ${stats.level1Count} words")
            appendLine("- Level 2 (Starting to Stick): ${stats.level2Count} words")
            appendLine("- Level 3 (Building Confidence): ${stats.level3Count} words")
            appendLine("- Level 4 (Nearly Mastered): ${stats.level4Count} words")
            appendLine("- Level 5 (Well Mastered): ${stats.level5Count} words")
            appendLine("- Level 6 (Fully Mastered): ${stats.level6Count} words")
            appendLine()
            appendLine("Theme: Celebrating the journey from first encounter to full mastery!")
            appendLine()
            appendLine("Requirements:")
            appendLine("1. Use mastery/learning language naturally (discovering, practicing, mastering, building confidence)")
            appendLine("2. Be warm and encouraging, acknowledging their progress")
            appendLine("3. Highlight specific milestones when meaningful (e.g., words reaching higher levels)")
            appendLine("4. Include 1-2 emojis that fit the learning journey")
            appendLine("5. Keep it under 2 sentences, conversational and motivating")
            appendLine()
            appendLine("Return ONLY the motivational message, no quotes or extra formatting.")
        }
        
        val request = OpenRouterRequest(
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        Content(
                            type = "text",
                            text = prompt
                        )
                    )
                )
            )
        )
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<OpenRouterResponse>()
            .map { response ->
                if (response.error != null) {
                    throw RuntimeException("OpenRouter error: ${response.error.message}")
                }
                
                val insight = response.choices?.firstOrNull()?.message?.content?.trim() 
                    ?: "Keep grinding! Every word you learn levels you up! 🎮✨"
                
                logger.info { "Successfully generated daily insight" }
                insight
            }
            .doOnError { error ->
                logger.error(error) { "Failed to generate daily insight" }
            }
    }
    
    fun generateStreakResetWarning(
        currentStreak: Int,
        progressStats: ProgressStatsDto,
        userName: String
    ): Mono<String> {
        logger.info { "Generating streak reset warning for $userName, streak: $currentStreak" }
        
        val prompt = buildString {
            appendLine("You are an enthusiastic vocabulary learning coach with a friendly, motivational personality.")
            appendLine("Generate ONE brief, personalized message to motivate the user to log in and maintain their streak.")
            appendLine()
            appendLine("User Context:")
            appendLine("- Name: $userName")
            appendLine("- Current streak: $currentStreak days")
            appendLine("- Total vocabulary: ${progressStats.totalWords} words")
            appendLine("- Due for review today: ${progressStats.dueCards} cards")
            appendLine("- Words mastered: ${progressStats.level5Count + progressStats.level6Count} words")
            appendLine()
            appendLine("Message Requirements:")
            appendLine("1. Be urgent but encouraging - remind them their streak is at risk")
            appendLine("2. Mention their current streak number prominently")
            appendLine("3. Celebrate their achievement so far")
            appendLine("4. keep it short in 1 sentence")
            appendLine("6. Make it personal and cool - reference their progress if relevant")
            appendLine("7. Create a sense of urgency but stay positive")
            appendLine()
            appendLine("Return ONLY the motivational message, no quotes or extra formatting.")
        }
        
        val request = OpenRouterRequest(
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        Content(
                            type = "text",
                            text = prompt
                        )
                    )
                )
            )
        )
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<OpenRouterResponse>()
            .map { response ->
                if (response.error != null) {
                    throw RuntimeException("OpenRouter error: ${response.error.message}")
                }
                
                val message = response.choices?.firstOrNull()?.message?.content?.trim()
                    ?: "Don't lose your $currentStreak-day streak! 🔥 Log in now to keep it going!"
                
                logger.info { "Successfully generated streak reset warning" }
                message
            }
            .doOnError { error ->
                logger.error(error) { "Failed to generate streak reset warning" }
            }
    }
    
    fun generateStreakReminderMessage(
        currentStreak: Int,
        userName: String,
        progressStats: ProgressStatsDto? = null
    ): Mono<String> {
        logger.info { "Generating personalized streak reminder message for $userName, streak: $currentStreak" }
        
        val prompt = buildString {
            appendLine("You are a supportive vocabulary learning coach with an encouraging, friendly personality.")
            appendLine("Generate ONE brief, personalized push notification message to remind the user to complete their review today to maintain their streak.")
            appendLine()
            appendLine("User Context:")
            appendLine("- Name: $userName")
            appendLine("- Current streak: $currentStreak days")
            if (progressStats != null) {
                appendLine("- Total vocabulary: ${progressStats.totalWords} words")
                appendLine("- Due for review today: ${progressStats.dueCards} cards")
                appendLine("- Words mastered: ${progressStats.level5Count + progressStats.level6Count} words")
            }
            appendLine()
            appendLine("Message Requirements:")
            appendLine("1. Create urgency but stay positive and encouraging")
            appendLine("2. Mention the streak number prominently (${currentStreak} days)")
            appendLine("3. Keep it concise - ideal for push notification (max 1-2 sentences)")
            appendLine("4. Make it personal and motivating")
            appendLine("5. Include 1-2 relevant emojis")
            appendLine("6. Focus on maintaining the streak achievement")
            if (progressStats != null && progressStats.dueCards > 0) {
                appendLine("7. Optionally mention they have ${progressStats.dueCards} cards waiting for review")
            }
            appendLine()
            appendLine("Return ONLY the notification message, no quotes or extra formatting.")
        }
        
        val request = OpenRouterRequest(
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        Content(
                            type = "text",
                            text = prompt
                        )
                    )
                )
            )
        )
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<OpenRouterResponse>()
            .map { response ->
                if (response.error != null) {
                    throw RuntimeException("OpenRouter error: ${response.error.message}")
                }
                
                val message = response.choices?.firstOrNull()?.message?.content?.trim()
                    ?: "You have a $currentStreak-day streak! 🔥 Complete your review today to keep it going!"
                
                logger.info { "Successfully generated personalized streak reminder message" }
                message
            }
            .doOnError { error ->
                logger.error(error) { "Failed to generate streak reminder message" }
            }
    }
    
    fun translateText(text: String, targetLanguage: String): Mono<String> {
        logger.info { "Translating text to $targetLanguage" }
        
        val prompt = "Translate the following text to $targetLanguage. Return only the translation, no explanations, no quotes, no additional text. Just the translation:\n\n$text"
        
        val request = OpenRouterRequest(
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        Content(
                            type = "text",
                            text = prompt
                        )
                    )
                )
            )
        )
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<OpenRouterResponse>()
            .map { response ->
                if (response.error != null) {
                    logger.error { "❌ [OpenRouter] API returned error: ${response.error.message}" }
                    throw RuntimeException("OpenRouter error: ${response.error.message}")
                }
                
                val translation = response.choices?.firstOrNull()?.message?.content?.trim() ?: ""
                
                if (translation.isEmpty()) {
                    logger.warn { "⚠️ [OpenRouter] Empty translation response" }
                    throw RuntimeException("Translation failed. Please try again.")
                }
                
                logger.info { "✅ [OpenRouter] Successfully translated text: ${translation.take(50)}..." }
                translation
            }
            .doOnError { error ->
                logger.error(error) { "💥 [OpenRouter] Failed to translate text" }
                logger.error { "💥 [OpenRouter] Error type: ${error.javaClass.simpleName}" }
                logger.error { "💥 [OpenRouter] Error message: ${error.message}" }
            }
    }
    
    /**
     * Generate a configurable number of vocabulary items based on user's language preferences.
     * Words are in target language with translations in native language, appropriate for currentLevel.
     * The number of items is controlled by app.vocabulary.suggestionCount (default 100).
     */
    fun generateVocabularyFromPreferences(
        targetLanguage: String,
        currentLevel: String,
        nativeLanguage: String,
        interests: List<String> = emptyList()
    ): Mono<List<SuggestVocabularyItemResponse>> {
        val itemCount = appProperties.vocabulary.suggestionCount
        val requestedCount = itemCount + 10
        logger.info {
            val interestsSummary = if (interests.isEmpty()) {
                "none"
            } else {
                interests.joinToString(limit = 5)
            }
            "Generating vocabulary: target=$targetLanguage, level=$currentLevel, " +
                "native=$nativeLanguage, itemsRequested=$requestedCount (base=$itemCount), interests=$interestsSummary"
        }

        val prompt = buildString {
            appendLine("You are an expert language teacher and curriculum designer.")
            appendLine()
            appendLine("Task: Generate exactly $requestedCount vocabulary items for a learner with the following profile:")
            appendLine("- Language they want to learn (target): $targetLanguage")
            appendLine("- Their current level in $targetLanguage: $currentLevel")
            appendLine("- Their native or primary language (for translations): $nativeLanguage")
            if (interests.isNotEmpty()) {
                appendLine("- Their interests / focus areas: ${interests.joinToString()}")
            }
            appendLine()
            appendLine("Requirements:")
            appendLine("1. Choose words and short phrases that are appropriate for level \"$currentLevel\" (e.g. beginner = A1, elementary; intermediate = B1-B2; advanced = C1-C2).")
            appendLine("2. Each item must be useful for real-world use: everyday vocabulary, common verbs, nouns, adjectives, and essential phrases.")
            appendLine("3. Cover a balanced mix: greetings, numbers, time, family, food, travel, work, emotions, actions, places, and common expressions.")
            if (interests.isNotEmpty()) {
                appendLine("4. Prioritize vocabulary that is especially relevant to these interests: ${interests.joinToString()}.")
                appendLine("5. Still include some general everyday vocabulary so the set feels balanced for an onboarding experience.")
            } else {
                appendLine("4. Keep the set balanced for an onboarding experience, covering everyday situations a new learner is likely to face.")
            }
            appendLine("Provide exactly $requestedCount items. No fewer, no more.")
            appendLine()
            appendLine("Output format (strict):")
            appendLine("- One entry per line.")
            appendLine("- Each line: originalWord,translation,optionalShortDescription")
            appendLine("- originalWord = word or short phrase in $targetLanguage")
            appendLine("- translation = meaning or translation in $nativeLanguage")
            appendLine("- optionalShortDescription = brief context or example (optional; can be empty after the second comma)")
            appendLine("- Use comma as separator. If a field contains a comma, do not use it or escape the content.")
            appendLine("- No numbering, no markdown, no code blocks, no extra text before or after the list.")
            appendLine()
            appendLine("Example (for German, level beginner, native English):")
            appendLine("Hallo,hello,a greeting")
            appendLine("Guten Morgen,good morning,formal morning greeting")
            appendLine("danke,thank you,")
            appendLine("Brot,bread,common noun")
            appendLine()
            appendLine("Output exactly $requestedCount lines in the format above, nothing else.")
        }

        val request = OpenRouterRequest(
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        Content(type = "text", text = prompt)
                    )
                )
            )
        )

        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .onStatus({ it.isError }) { response ->
                response.bodyToMono<String>().flatMap { errorBody ->
                    logger.error { "OpenRouter suggest-vocabulary HTTP ${response.statusCode()}: $errorBody" }
                    Mono.error(RuntimeException("OpenRouter API error: ${response.statusCode()} - $errorBody"))
                }
            }
            .bodyToMono<OpenRouterResponse>()
            .map { response ->
                if (response.error != null) {
                    throw RuntimeException("OpenRouter error: ${response.error.message}")
                }
                val content = response.choices?.firstOrNull()?.message?.content?.trim() ?: ""
                if (content.isBlank()) {
                    throw RuntimeException("No vocabulary generated. Please try again.")
                }
                val items = parseSuggestVocabularyResponse(content)
                val minExpected = maxOf(20, requestedCount / 2)
                if (items.size < minExpected) {
                    logger.warn { "AI returned only ${items.size} items; expected around $requestedCount" }
                }
                logger.info { "Generated ${items.size} vocabulary items for $targetLanguage ($currentLevel); returning up to $requestedCount" }
                items.take(requestedCount)
            }
            .doOnError { error ->
                logger.error(error) { "Failed to generate vocabulary from preferences" }
            }
    }

    private fun parseSuggestVocabularyResponse(raw: String): List<SuggestVocabularyItemResponse> {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val result = mutableListOf<SuggestVocabularyItemResponse>()
        for (line in lines) {
            val parts = line.split(",", limit = 3)
            if (parts.size >= 2) {
                val originalWord = parts[0].trim().takeIf { it.isNotBlank() } ?: continue
                val translation = parts[1].trim().takeIf { it.isNotBlank() } ?: continue
                val description = parts.getOrNull(2)?.trim() ?: ""
                result.add(SuggestVocabularyItemResponse(originalWord = originalWord, translation = translation, description = description))
            }
        }
        return result
    }

    private fun isValidVocabularyFormat(text: String): Boolean {
        if (text.isBlank()) return false
        if (!text.contains(",")) return false
        
        val entries = text.split(";")
        if (entries.isEmpty()) return false
        
        val entryPattern = Regex("^[^,]+,[^,]+(?:,[^,]*)?$")
        val validEntries = entries.count { entry ->
            entry.trim().isNotEmpty() && entryPattern.matches(entry.trim())
        }
        
        return validEntries > 0 && (validEntries.toFloat() / entries.size) >= 0.5f
    }
}

