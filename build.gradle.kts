plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	kotlin("plugin.jpa") version "1.9.25"
	id("org.springframework.boot") version "3.5.6"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}

group = "com.alirezaiyan"
version = "0.0.1-SNAPSHOT"
description = "Lexicon Server Application"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot Starters
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	
	// Database
	implementation("org.postgresql:postgresql")
	implementation("com.h2database:h2")
	implementation("org.flywaydb:flyway-core:11.8.0")
	implementation("org.flywaydb:flyway-database-postgresql:11.8.0")
	
	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
	
	// Firebase Admin SDK for push notifications
	implementation("com.google.firebase:firebase-admin:9.2.0")
	
	// Google API Client for OAuth
	implementation("com.google.api-client:google-api-client:2.2.0")
	
	// HTTP Client for external API calls
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	
	// Rate Limiting
	implementation("com.bucket4j:bucket4j-core:8.10.1")
	
	// Password Hashing (for refresh tokens)
	implementation("org.springframework.security:spring-security-crypto")
	
	// Argon2 for refresh token hashing
	implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
	
	// Logging
	implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
	
	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.13.8")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo configuration
tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
		csv.required = false
	}
	
	// Print coverage after report is generated
	doLast {
		val report = file("$buildDir/reports/jacoco/test/jacocoTestReport.xml")
		
		if (report.exists()) {
			val content = report.readText()
			
			// Parse line coverage from XML
			val lineRegex = """<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""".toRegex()
			val match = lineRegex.find(content)
			
			if (match != null) {
				val missed = match.groupValues[1].toInt()
				val covered = match.groupValues[2].toInt()
				val total = missed + covered
				val percentage = if (total > 0) (covered * 100) / total else 0
				
				println("")
				println("============================================")
				println("Code Coverage Report (Line Coverage)")
				println("============================================")
				println("Covered:    $covered lines")
				println("Missed:     $missed lines")
				println("Total:      $total lines")
				println("Coverage:   $percentage%")
				println("Threshold:  70%")
				println("============================================")
				println("")
				
				if (percentage < 70) {
					throw GradleException("Code coverage is below 70% threshold (${percentage}%)")
				}
			}
		}
	}
}

// Run report after tests
tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}
