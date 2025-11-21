package com.alirezaiyan.vokab.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    var jwt: JwtConfig = JwtConfig(),
    var firebase: FirebaseAppConfig = FirebaseAppConfig(),
    var openrouter: OpenRouterConfig = OpenRouterConfig(),
    var revenuecat: RevenueCatConfig = RevenueCatConfig(),
    var cors: CorsConfig = CorsConfig(),
    var features: FeatureFlagsConfig = FeatureFlagsConfig(),
    var security: SecurityConfig = SecurityConfig(),
    var logging: LoggingConfig = LoggingConfig()
)

data class JwtConfig(
    var secret: String = "",
    var expirationMs: Long = 86400000,
    var refreshExpirationMs: Long = 604800000,
    var privateKeyPath: String = "",
    var publicKeyPath: String = "",
    var issuer: String = "vokab-server",
    var audience: String = "vokab-client"
)

data class FirebaseAppConfig(
    var serviceAccountPath: String = ""
)

data class OpenRouterConfig(
    var apiKey: String = "",
    var baseUrl: String = "https://openrouter.ai/api/v1"
)

data class RevenueCatConfig(
    var webhookSecret: String = "",
    var apiKey: String = ""
)

data class CorsConfig(
    var allowedOrigins: String = "http://localhost:3000"
)

data class FeatureFlagsConfig(
    var premiumFeaturesEnabled: Boolean = true,
    var aiImageExtractionEnabled: Boolean = true,
    var aiDailyInsightEnabled: Boolean = true,
    var pushNotificationsEnabled: Boolean = true,
    var subscriptionsEnabled: Boolean = true,
    var freeAiExtractionLimit: Int = 10  // Free tier gets 10 AI image extractions
)

data class SecurityConfig(
    var testEmails: String = ""  // Comma-separated list of test emails that bypass active check
)

data class LoggingConfig(
    var enabled: Boolean = true,
    var maxBodySize: Int = 50000,
    var excludePatterns: String = "/actuator/health,/h2-console/**",
    var logLevel: String = "INFO"
)

