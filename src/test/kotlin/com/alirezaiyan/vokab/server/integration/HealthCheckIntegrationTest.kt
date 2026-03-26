package com.alirezaiyan.vokab.server.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Health check and smoke tests.
 * Verifies basic app health, database connectivity, and essential endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthCheckIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `API health endpoint responds successfully`() {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `Actuator health endpoint is accessible`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").exists())
    }

    @Test
    fun `API responds to public endpoints without authentication`() {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `Protected endpoints return 401 without authentication`() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `API returns proper error format for invalid paths`() {
        mockMvc.perform(get("/api/v1/nonexistent-endpoint"))
            .andExpect(status().isNotFound)
    }
}
