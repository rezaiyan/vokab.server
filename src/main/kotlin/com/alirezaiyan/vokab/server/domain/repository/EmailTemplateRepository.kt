package com.alirezaiyan.vokab.server.domain.repository

import com.alirezaiyan.vokab.server.domain.entity.EmailTemplate
import org.springframework.data.jpa.repository.JpaRepository

interface EmailTemplateRepository : JpaRepository<EmailTemplate, String> {
    fun findByIdAndActiveTrue(id: String): EmailTemplate?
    fun findByCategoryAndActiveTrue(category: String): List<EmailTemplate>
}
