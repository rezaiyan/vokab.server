package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1")
class HealthController {
    
    @Autowired(required = false)
    private var buildProperties: BuildProperties? = null
    
    @GetMapping("/health")
    fun health(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val healthData = mapOf(
            "status" to "UP",
            "timestamp" to Instant.now().toString(),
            "version" to (buildProperties?.version ?: "development"),
            "name" to (buildProperties?.name ?: "vokab-server")
        )
        return ResponseEntity.ok(ApiResponse(success = true, data = healthData))
    }
    
    @GetMapping("/version")
    fun version(): ResponseEntity<ApiResponse<Map<String, String>>> {
        val versionData = mapOf(
            "version" to (buildProperties?.version ?: "development"),
            "name" to (buildProperties?.name ?: "vokab-server"),
            "group" to (buildProperties?.group ?: "com.alirezaiyan"),
            "time" to (buildProperties?.time?.toString() ?: Instant.now().toString())
        )
        return ResponseEntity.ok(ApiResponse(success = true, data = versionData))
    }
}

