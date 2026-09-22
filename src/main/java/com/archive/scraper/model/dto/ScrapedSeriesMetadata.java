package com.archive.scraper.model.dto;

import java.util.List;

/**
 * ============================================================================
 * RECORD: ScrapedSeriesMetadata
 * ============================================================================
 * WHAT IT DOES:
 * An immutable data transfer object (DTO) that holds discovered metadata for a series
 * (title, author, synopsis, cover art, adult tag, and list of chapter links)
 * after being parsed from HTML by JsoupScraper or SeleniumHarvester.
 *
 * WHY IT IS USED:
 * Separates raw web scraping payloads from the persistent database domain model (Manga).
 * By decoupling scraper output from the database entity, changes to website HTML
 * do not directly mutate database state until validated and processed.
 *
 * SYNTAX BREAKDOWN:
 * - 'public record': A concise, immutable class introduced in Java 14 and standardized
 *   in Java 16. The compiler automatically produces private final fields, accessor methods
 *   (e.g., title()), equals(), hashCode(), and toString().
 * - 'List<ScrapedChapter> chapters': An ordered list of raw chapter links discovered on the catalog page.
 * ============================================================================
 */
public record ScrapedSeriesMetadata(
        String title,
        String author,
        String description,
        String coverImageUrl,
        boolean isAdult,
        List<ScrapedChapter> chapters
) {}
