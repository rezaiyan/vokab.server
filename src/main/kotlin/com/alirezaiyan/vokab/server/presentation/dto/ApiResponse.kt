package com.alirezaiyan.vokab.server.presentation.dto

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)

data class ErrorResponse(
    val success: Boolean = false,
    val message: String,
    val details: String? = null
)

