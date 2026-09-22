package com.archive.scraper.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================================
 * INTERFACE: ScraperTemplateRepository
 * ============================================================================
 * WHAT IT DOES:
 * Provides automated database access operations (CRUD, queries) for ScraperTemplate entities.
 *
 * WHY IT IS USED:
 * When an ingestion job is triggered with a raw URL (e.g., "https://asurascans.com/manga/solo-leveling/"),
 * the scraper engine extracts the domain name and queries this repository to find the matching recipe.
 *
 * SYNTAX BREAKDOWN:
 * - @Repository: Tells Spring that this interface is a Data Access Object (DAO) component.
 * - extends JpaRepository<ScraperTemplate, UUID>: Inherits built-in implementations of
 *   save(), findById(), findAll(), deleteById(), and transactional support.
 * - Optional<ScraperTemplate> findByDomainName(String domainName):
 *   Spring Data dynamically builds the query based on method naming convention:
 *   "SELECT t FROM ScraperTemplate t WHERE t.domainName = :domainName"
 *   Returns an Optional to prevent NullPointerExceptions when a domain is not yet registered.
 * ============================================================================
 */
@Repository
public interface ScraperTemplateRepository extends JpaRepository<ScraperTemplate, UUID> {

    /**
     * Looks up a scraper configuration template matching the given web domain.
     *
     * @param domainName The web host (e.g., "asurascans.com")
     * @return An Optional containing the template if found, or empty if unregistered.
     */
    Optional<ScraperTemplate> findByDomainName(String domainName);

    /**
     * Checks if a template recipe already exists for a domain.
     */
    boolean existsByDomainName(String domainName);
}
