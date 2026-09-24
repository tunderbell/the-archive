package com.archive.domain.manga;

import com.archive.domain.manga.chapter.Chapter;
import com.archive.domain.manga.chapter.ChapterRepository;
import com.archive.domain.manga.chapter.dto.ChapterPagesDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class MangaService {

    private static final Logger log = LoggerFactory.getLogger(MangaService.class);

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

    // --- In-App Reader & Media Serving ---

    @Transactional(readOnly = true)
    public ChapterPagesDto getChapterPages(UUID chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        Manga manga = chapter.getManga();
        String seriesTitle = manga != null ? manga.getTitle() : "Unknown Series";
        UUID mangaId = manga != null ? manga.getId() : null;

        if (!chapter.isDownloaded() || chapter.getStoragePath() == null) {
            return new ChapterPagesDto(
                    chapterId, mangaId, seriesTitle, chapter.getChapterNumber(),
                    chapter.getTitle(), false, 0, Collections.emptyList()
            );
        }

        Path dir = Paths.get(chapter.getStoragePath());
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return new ChapterPagesDto(
                    chapterId, mangaId, seriesTitle, chapter.getChapterNumber(),
                    chapter.getTitle(), false, 0, Collections.emptyList()
            );
        }

        try (var stream = Files.list(dir)) {
            List<String> imageFiles = stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> {
                        String lower = name.toLowerCase();
                        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                                || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif");
                    })
                    .sorted(Comparator.naturalOrder())
                    .toList();

            return new ChapterPagesDto(
                    chapterId, mangaId, seriesTitle, chapter.getChapterNumber(),
                    chapter.getTitle(), true, imageFiles.size(), imageFiles
            );
        } catch (IOException e) {
            log.error("Failed to read chapter directory [{}]: {}", dir, e.getMessage());
            return new ChapterPagesDto(
                    chapterId, mangaId, seriesTitle, chapter.getChapterNumber(),
                    chapter.getTitle(), true, 0, Collections.emptyList()
            );
        }
    }

    @Transactional(readOnly = true)
    public Resource getChapterPageResource(UUID chapterId, String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename security violation: " + filename);
        }

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        if (chapter.getStoragePath() == null) {
            throw new IllegalArgumentException("Chapter has not been downloaded to storage yet.");
        }

        Path basePath = Paths.get(chapter.getStoragePath()).normalize();
        Path filePath = basePath.resolve(filename).normalize();

        if (!filePath.startsWith(basePath) || !Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + filename);
        }

        return new FileSystemResource(filePath);
    }

    public void openInExternalViewer(UUID chapterId) throws IOException {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        if (chapter.getStoragePath() == null) {
            throw new IllegalArgumentException("Chapter is not stored locally.");
        }

        Path path = Paths.get(chapter.getStoragePath()).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Directory does not exist: " + path);
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            new ProcessBuilder("explorer.exe", path.toString()).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", path.toString()).start();
        } else {
            new ProcessBuilder("xdg-open", path.toString()).start();
        }
    }
}
