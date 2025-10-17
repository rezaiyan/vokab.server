package com.alirezaiyan.vokab.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EntityScan(basePackages = ["com.alirezaiyan.vokab.server.domain.entity"])
@EnableJpaRepositories(basePackages = ["com.alirezaiyan.vokab.server.domain.repository"])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
