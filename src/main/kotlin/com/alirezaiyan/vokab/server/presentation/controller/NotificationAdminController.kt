package com.alirezaiyan.vokab.server.presentation.controller

import com.alirezaiyan.vokab.server.presentation.dto.ApiResponse
import com.alirezaiyan.vokab.server.presentation.dto.NotificationAdminStatsDto
import com.alirezaiyan.vokab.server.service.NotificationEngagementService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/notifications")
class NotificationAdminController(
    private val notificationEngagementService: NotificationEngagementService
) {
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<ApiResponse<NotificationAdminStatsDto>> {
        val stats = notificationEngagementService.getAdminStats()
        return ResponseEntity.ok(ApiResponse(success = true, data = stats))
    }
}
