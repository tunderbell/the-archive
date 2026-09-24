package com.archive.domain.manga.chapter.dto;

import java.util.List;
import java.util.UUID;

/**
 * ============================================================================
 * RECORD: ChapterPagesDto
 * ============================================================================
 * WHAT IT DOES:
 * Data Transfer Object delivering chapter metadata and ordered image filenames
 * to the in-app Manga/Webtoon Reader Buffer.
 *
 * WHY IT IS USED:
 * Provides a clean, detached representation of chapter image files without
 * Hibernate proxy overhead or circular references.
 * ============================================================================
 */
public record ChapterPagesDto(
        UUID chapterId,
        UUID mangaId,
        String seriesTitle,
        Double chapterNumber,
        String chapterTitle,
        boolean downloaded,
        int pageCount,
        List<String> pageFiles
) {}
