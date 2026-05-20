plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "2.1.0"
}

group = "com.archive"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Core Engine
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    
    // Database (Local-First)
    runtimeOnly("org.xerial:sqlite-jdbc:3.45.1.0")
    runtimeOnly("org.hibernate.orm:hibernate-community-dialects:7.0.0.Final")

    // Database (Dual-Setup)
    runtimeOnly("org.postgresql:postgresql")        // Shared Workspace

    // The Harvesters (Area 4)
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.seleniumhq.selenium:selenium-java:4.18.1")

    // Utilities
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")


}

tasks.named<Test>("test") {
    useJUnitPlatform()
}