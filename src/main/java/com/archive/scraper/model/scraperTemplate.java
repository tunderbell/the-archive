package com.archive.scraper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * CLASS: ScraperTemplate
 * ============================================================================
 * WHAT IT DOES:
 * Represents a persistent "site recipe" or configuration blueprint that defines
 * how The Archive interacts with a specific external domain (e.g., "asurascans.com").
 * It tells the scraping engines which CSS selectors extract titles, authors,
 * chapter links, and page images.
 *
 * WHY IT IS USED:
 * Web layouts change frequently across different manga and anime aggregator sites.
 * Hardcoding CSS selectors into Java code would require recompiling the application
 * every time a site tweaks their HTML classes. By storing templates as database entities,
 * users can add, update, or import JSON scraper profiles dynamically at runtime.
 *
 * SYNTAX BREAKDOWN:
 * - @Entity: Marks this class as a JPA entity, instructing Hibernate to map it to a table.
 * - @Table(name = "scraper_template"): Explicitly names the database table.
 * - @Id & @GeneratedValue(strategy = GenerationType.UUID): Assigns a universally unique
 *   128-bit identifier, preventing collisions when syncing templates between vaults.
 * ============================================================================
 */
@Entity
@Table(name = "scraper_template")
@Getter
@Setter
@NoArgsConstructor
public class ScraperTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    /**
     * Domain identifier used for route matching (e.g., "asurascans.com").
     * Marked unique so that only one authoritative recipe exists per host domain.
     */
    @Column(name = "domain_name", nullable = false, unique = true)
    private String domainName;

    /**
     * Human-readable label for the website (e.g., "Asura Scans").
     */
    @Column(nullable = false)
    private String name;

    // --- CSS Selectors for Discovery (Metadata & Catalog) ---

    /**
     * CSS Selector for the manga/comic title (e.g., "h1.entry-title" or ".series-title").
     * JSoup/Selenium uses this query to locate the title node in the DOM.
     */
    @Column(name = "title_selector", nullable = false)
    private String titleSelector;

    /**
     * CSS Selector for the author or studio (e.g., ".author-content a").
     */
    @Column(name = "author_selector")
    private String authorSelector;

    /**
     * CSS Selector for the synopsis / description paragraph.
     */
    @Column(name = "description_selector", columnDefinition = "TEXT")
    private String descriptionSelector;

    /**
     * CSS Selector for the series cover thumbnail image tag.
     */
    @Column(name = "cover_image_selector")
    private String coverImageSelector;

    /**
     * CSS Selector locating the list of chapter link tags (e.g., "#chapterlist li a").
     */
    @Column(name = "chapter_list_selector", nullable = false)
    private String chapterListSelector;

    /**
     * Optional sub-selector inside each chapter element to parse the title text.
     */
    @Column(name = "chapter_title_selector")
    private String chapterTitleSelector;

    // --- CSS Selectors for Content Harvesting (Reader Pages) ---

    /**
     * CSS Selector targeting individual page images in the chapter reader
     * (e.g., "#readerarea img", ".page-break img").
     */
    @Column(name = "image_selector", nullable = false)
    private String imageSelector;

    /**
     * Optional selector to verify if a series is flagged as adult / 18+ content.
     */
    @Column(name = "adult_selector")
    private String adultSelector;

    // --- Crawler Rate Limiting & Execution Settings ---

    /**
     * Minimum wait time in milliseconds between successive HTTP requests to this domain.
     * Prevents IP blacklisting and reduces aggressive request spikes on target servers.
     */
    @Column(name = "rate_limit_ms")
    private long rateLimitMs = 1500;

    /**
     * When true, tells ScraperService that this domain renders content using JavaScript
     * or employs Cloudflare Turnstile, necessitating the Selenium Harvester rather than JSoup.
     */
    @Column(name = "requires_js")
    private boolean requiresJs = false;

    /**
     * Custom User-Agent string. If null, the engine uses The Archive's default browser profile.
     */
    @Column(name = "custom_user_agent")
    private String customUserAgent;

    @CreationTimestamp
    @Column(name = "date_added", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateAdded;

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastUpdated;
}
