package com.alirezaiyan.vokab.server.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Configuration
class RateLimitConfig {
    
    private val cache: MutableMap<String, Bucket> = ConcurrentHashMap()
    
    /**
     * Get rate limit bucket for AI endpoints
     * Limit: 10 requests per minute per user
     */
    fun getAiBucket(userId: String): Bucket {
        return cache.computeIfAbsent(userId) {
            val limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)))
            Bucket.builder()
                .addLimit(limit)
                .build()
        }
    }
    
    /**
     * Get rate limit bucket for image processing
     * Limit: 5 images per minute per user (more restrictive due to higher cost)
     */
    fun getImageProcessingBucket(userId: String): Bucket {
        val key = "image_$userId"
        return cache.computeIfAbsent(key) {
            val limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)))
            Bucket.builder()
                .addLimit(limit)
                .build()
        }
    }
}

