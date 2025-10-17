package com.alirezaiyan.vokab.server.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.FileInputStream

private val logger = KotlinLogging.logger {}

@Configuration
class FirebaseConfig(
    private val appProperties: AppProperties
) {
    
    @Bean
    fun initializeFirebase(): FirebaseApp? {
        return try {
            // Check if Firebase is already initialized
            if (FirebaseApp.getApps().isNotEmpty()) {
                logger.info { "Firebase already initialized" }
                return FirebaseApp.getInstance()
            }
            
            // Try to initialize with service account file if configured
            if (appProperties.firebase.serviceAccountPath.isNotBlank()) {
                val serviceAccountFile = File(appProperties.firebase.serviceAccountPath)
                if (serviceAccountFile.exists()) {
                    val options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(FileInputStream(serviceAccountFile)))
                        .build()
                    
                    val app = FirebaseApp.initializeApp(options)
                    logger.info { "✅ Firebase initialized successfully with service account" }
                    return app
                } else {
                    logger.warn { "Firebase service account file not found: ${appProperties.firebase.serviceAccountPath}" }
                }
            }
            
            // Initialize Firebase in auth-only mode without credentials
            // Firebase ID token verification works by fetching Google's public keys over HTTPS
            // No service account or credentials needed for this functionality
            logger.info { "Initializing Firebase in auth-only mode (no credentials required)" }
            val options = FirebaseOptions.builder()
                .setProjectId("vokab-737ec") // Your Firebase project ID
                .build()
            
            val app = FirebaseApp.initializeApp(options)
            logger.info { "✅ Firebase initialized for authentication (token verification enabled, push notifications disabled)" }
            app
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to initialize Firebase" }
            logger.warn { "Authentication with Google will not work. Push notifications are disabled." }
            null
        }
    }
}

