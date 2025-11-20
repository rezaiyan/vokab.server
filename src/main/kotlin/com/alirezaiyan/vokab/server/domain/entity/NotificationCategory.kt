package com.alirezaiyan.vokab.server.domain.entity

enum class NotificationCategory(val value: String) {
    USER("user"),
    SYSTEM("system");
    
    companion object {
        fun fromString(value: String?): NotificationCategory {
            return values().find { it.value == value } ?: SYSTEM
        }
    }
}




