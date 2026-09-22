package com.archive.domain.manga;

import com.archive.domain.manga.chapter.Chapter;
import com.archive.domain.manga.chapter.ChapterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class MangaService {

    private final MangaRepository mangaRepository;
    private final ChapterRepository chapterRepository;

    public MangaService(MangaRepository mangaRepository, ChapterRepository chapterRepository) {
        this.mangaRepository = mangaRepository;
        this.chapterRepository = chapterRepository;
    }

    // --- Series Operations ---

    public Manga createManga(Manga manga) {
        return mangaRepository.save(manga);
    }

    @Transactional(readOnly = true)
    public Optional<Manga> getMangaById(UUID id) {
        return mangaRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Manga> getAllManga() {
        return mangaRepository.findAll();
    }

    public void deleteManga(UUID id) {
        mangaRepository.deleteById(id);
    }

    // --- Chapter Ingestion & Deduplication (For JSoup Scout) ---

    /**
     * Ingests a list of chapters found by the scraper.
     * Prevents duplicate chapter numbers if the scraper is re-run.
     */
    public List<Chapter> addDiscoveredChapters(UUID mangaId, List<Chapter> discoveredChapters) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new IllegalArgumentException("Manga not found with ID: " + mangaId));

        List<Chapter> existingChapters = chapterRepository.findByMangaIdOrderByChapterNumberAsc(mangaId);
        Set<Double> existingNumbers = new HashSet<>();
        for (Chapter ch : existingChapters) {
            existingNumbers.add(ch.getChapterNumber());
        }

        List<Chapter> newlySaved = new ArrayList<>();
        for (Chapter chapter : discoveredChapters) {
            if (!existingNumbers.contains(chapter.getChapterNumber())) {
                manga.addChapter(chapter);
                newlySaved.add(chapter);
                existingNumbers.add(chapter.getChapterNumber());
            }
        }

        manga.setTotalChapters(existingNumbers.size());
        manga.setLastChecked(OffsetDateTime.now());
        mangaRepository.save(manga);

        return newlySaved;
    }

    // --- Chapter Download Progression (For Selenium Harvester) ---

    /**
     * Marks a chapter as downloaded and updates the parent Manga's download count.
     */
    public Chapter markChapterDownloaded(UUID chapterId, String storagePath, int pageCount) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        chapter.setDownloaded(true);
        chapter.setStoragePath(storagePath);
        chapter.setPageCount(pageCount);
        Chapter savedChapter = chapterRepository.save(chapter);

        // Recalculate downloaded count on the parent Manga
        Manga manga = chapter.getManga();
        if (manga != null) {
            long count = manga.getChapters().stream().filter(Chapter::isDownloaded).count();
            manga.setDownloadedChapters((int) count);
            mangaRepository.save(manga);
        }

        return savedChapter;
    }

    @Transactional(readOnly = true)
    public List<Chapter> getChaptersForManga(UUID mangaId) {
        return chapterRepository.findByMangaIdOrderByChapterNumberAsc(mangaId);
    }

    // --- Scraper Configuration Overrides (Area 4: User-Defined Scrapers) ---

    public Manga updateSelectors(UUID mangaId, String imageSelector, String chapterSelector) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new IllegalArgumentException("Manga not found with ID: " + mangaId));

        manga.setCustomImageSelector(imageSelector);
        manga.setCustomChapterSelector(chapterSelector);
        return mangaRepository.save(manga);
    }
}
