package com.archive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============================================================================
 * CLASS: ArchiveApplication
 * ============================================================================
 * WHAT IT DOES:
 * The primary Spring Boot entry point for The Archive.
 *
 * SYNTAX BREAKDOWN:
 * - @SpringBootApplication: Meta-annotation that combines:
 *   1. @Configuration: Tags the class as a source of bean definitions.
 *   2. @EnableAutoConfiguration: Tells Spring Boot to configure beans based on classpath.
 *   3. @ComponentScan: Scans com.archive package for @Service, @Component, @RestController.
 * ============================================================================
 */
@SpringBootApplication
public class ArchiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchiveApplication.class, args);
    }
}
