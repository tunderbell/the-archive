package com.archive.scraper.model.dto;

/**
 * ============================================================================
 * RECORD: ScrapedChapter
 * ============================================================================
 * WHAT IT DOES:
 * An immutable data transfer object (DTO) representing an individual chapter link
 * discovered while scanning a series catalog page.
 *
 * WHY IT IS USED:
 * Holds the parsed numerical value (e.g. 104.5), raw display title (e.g. "Chapter 104.5 - Epilogue"),
 * and source URL before creating and persisting the domain Chapter entity.
 *
 * SYNTAX BREAKDOWN:
 * - Double chapterNumber: Floating-point number to accurately support decimal chapters
 *   (e.g., Chapter 0.5, Chapter 78.1) common in manga/comic publications.
 * - String title: Raw display title extracted from the link text.
 * - String chapterUrl: Absolute URL targeting the web reader page for this chapter.
 * ============================================================================
 */
public record ScrapedChapter(
        Double chapterNumber,
        String title,
        String chapterUrl
) {}
