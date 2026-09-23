package com.archive.domain.manga;

import com.archive.domain.manga.chapter.Chapter;
import com.archive.domain.manga.chapter.ChapterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * TEST: MangaServiceTest
 * ============================================================================
 * WHAT IT DOES:
 * Tests chapter deduplication during ingestion and download progress recalculations.
 *
 * WHY IT IS USED:
 * Guarantees that running the scraper multiple times never creates duplicate
 * chapter records in the database.
 * ============================================================================
 */
@ExtendWith(MockitoExtension.class)
class MangaServiceTest {

    @Mock private MangaRepository mangaRepository;
    @Mock private ChapterRepository chapterRepository;

    private MangaService mangaService;

    @BeforeEach
    void setUp() {
        mangaService = new MangaService(mangaRepository, chapterRepository);
    }

    @Test
    @DisplayName("Should ingest only new chapters and skip existing chapter numbers")
    void shouldDeduplicateChapters() {
        UUID mangaId = UUID.randomUUID();
        Manga manga = new Manga();
        manga.setId(mangaId);
        manga.setTitle("Solo Leveling");

        // Existing: Chapter 1.0 already exists
        Chapter ch1 = new Chapter();
        ch1.setChapterNumber(1.0);
        ch1.setTitle("Chapter 1");

        when(mangaRepository.findById(mangaId)).thenReturn(Optional.of(manga));
        when(chapterRepository.findByMangaIdOrderByChapterNumberAsc(mangaId)).thenReturn(List.of(ch1));

        // Inbound scraped chapters: Chapter 1.0 (duplicate) and Chapter 2.0 (new)
        Chapter scrapedCh1 = new Chapter();
        scrapedCh1.setChapterNumber(1.0);

        Chapter scrapedCh2 = new Chapter();
        scrapedCh2.setChapterNumber(2.0);

        List<Chapter> newlySaved = mangaService.addDiscoveredChapters(mangaId, List.of(scrapedCh1, scrapedCh2));

        // Verification: Only Chapter 2.0 was saved
        assertThat(newlySaved).hasSize(1);
        assertThat(newlySaved.get(0).getChapterNumber()).isEqualTo(2.0);
        assertThat(manga.getTotalChapters()).isEqualTo(2);
        verify(mangaRepository).save(manga);
    }
}
