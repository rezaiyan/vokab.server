package com.alirezaiyan.vokab.server.service

import com.alirezaiyan.vokab.server.domain.entity.User
import com.alirezaiyan.vokab.server.domain.repository.UserRepository
import com.alirezaiyan.vokab.server.presentation.dto.UserDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val userRepository: UserRepository
) {
    
    @Transactional(readOnly = true)
    fun getUserById(userId: Long): UserDto {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        return user.toDto()
    }
    
    @Transactional(readOnly = true)
    fun getUserByEmail(email: String): UserDto {
        val user = userRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("User not found") }
        return user.toDto()
    }
    
    @Transactional
    fun updateUser(userId: Long, name: String?, displayAlias: String? = null): UserDto {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }

        if (displayAlias != null) {
            validateDisplayAlias(displayAlias)
        }

        val updatedUser = user.copy(
            name = name ?: user.name,
            displayAlias = displayAlias ?: user.displayAlias
        )

        val saved = userRepository.save(updatedUser)
        logger.info { "User updated: ${saved.email}" }

        return saved.toDto()
    }

    private fun validateDisplayAlias(alias: String) {
        val aliasRegex = "^[a-zA-Z0-9 _-]{2,30}$".toRegex()
        require(aliasRegex.matches(alias)) {
            "Username must be 2-30 characters and contain only letters, numbers, spaces, underscores, or hyphens"
        }
    }
    
    @Transactional
    fun deleteUser(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val deactivatedUser = user.copy(active = false)
        userRepository.save(deactivatedUser)
        
        logger.info { "User deactivated: ${user.email}" }
    }
    
    private fun User.toDto(): UserDto {
        return UserDto(
            id = this.id!!,
            email = this.email,
            name = this.name,
            subscriptionStatus = this.subscriptionStatus,
            subscriptionExpiresAt = this.subscriptionExpiresAt?.toString(),
            currentStreak = this.currentStreak,
            displayAlias = this.displayAlias,
            profileImageUrl = this.profileImageUrl
        )
    }
}

