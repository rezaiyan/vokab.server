package com.alirezaiyan.vokab.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthCheckTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint returns 200`() {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `actuator health endpoint returns 200`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }
}
