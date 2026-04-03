package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.AppConfigDto
import com.alirezaiyan.vokab.server.presentation.dto.AppConfigHistoryDto
import com.alirezaiyan.vokab.server.presentation.dto.AppConfigUpdateRequest
import com.alirezaiyan.vokab.server.presentation.dto.toDto
import com.alirezaiyan.vokab.server.service.AppConfigService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/config")
class AppConfigAdminController(
    private val appConfigService: AppConfigService
) {
    @GetMapping
    fun list(): ResponseEntity<ApiResponse<List<AppConfigDto>>> =
        ResponseEntity.ok(ApiResponse(success = true, data = appConfigService.list().map { it.toDto() }))

    @GetMapping("/{namespace}/{key}")
    fun get(
        @PathVariable namespace: String,
        @PathVariable key: String
    ): ResponseEntity<ApiResponse<AppConfigDto>> {
        val config = appConfigService.find(namespace, key)
            ?: throw NoSuchElementException("Config not found: $namespace/$key")
        return ResponseEntity.ok(ApiResponse(success = true, data = config.toDto()))
    }

    @PutMapping("/{namespace}/{key}")
    fun set(
        @PathVariable namespace: String,
        @PathVariable key: String,
        @Valid @RequestBody request: AppConfigUpdateRequest,
        @RequestHeader(value = "X-Changed-By", required = false) changedBy: String?
    ): ResponseEntity<ApiResponse<AppConfigDto>> {
        val updated = appConfigService.set(namespace, key, request.value, changedBy ?: "api")
        return ResponseEntity.ok(ApiResponse(success = true, data = updated.toDto()))
    }

    @GetMapping("/{namespace}/{key}/history")
    fun history(
        @PathVariable namespace: String,
        @PathVariable key: String
    ): ResponseEntity<ApiResponse<List<AppConfigHistoryDto>>> =
        ResponseEntity.ok(
            ApiResponse(success = true, data = appConfigService.history(namespace, key).map { it.toDto() })
        )
}
