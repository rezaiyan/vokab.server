package com.alirezaiyan.vokab.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    var secret: String = "",
    var expirationMs: Long = 86400000,
    var refreshExpirationMs: Long = 7776000000
)

