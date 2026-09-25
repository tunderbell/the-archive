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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

        boolean isRead = chapter.isRead();
        int lastReadPage = chapter.getLastReadPage() != null ? chapter.getLastReadPage() : 0;
        String cbzPath = chapter.getCbzPath();

        if (!chapter.isDownloaded() || chapter.getStoragePath() == null) {
            return new ChapterPagesDto(
                    chapterId, mangaId, seriesTitle, chapter.getChapterNumber(),
                    chapter.getTitle(), false, 0, Collections.emptyList(),
                    isRead, lastReadPage, cbzPath
            );
        }

        Path dir = Paths.get(chapter.getStoragePath());
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return new ChapterPagesDto(
                    chapterId, mangaId, seriesTitle, chapter.getChapterNumber(),
                    chapter.getTitle(), false, 0, Collections.emptyList(),
                    isRead, lastReadPage, cbzPath
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
                    chapter.getTitle(), true, imageFiles.size(), imageFiles,
                    isRead, lastReadPage, cbzPath
            );
        } catch (IOException e) {
            log.error("Failed to read chapter directory [{}]: {}", dir, e.getMessage());
            return new ChapterPagesDto(
                    chapterId, mangaId, seriesTitle, chapter.getChapterNumber(),
                    chapter.getTitle(), true, 0, Collections.emptyList(),
                    isRead, lastReadPage, cbzPath
            );
        }
    }

    // --- Reading Progress & Bookmarks ---

    public Chapter updateReadingProgress(UUID chapterId, int page, Boolean isRead) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        chapter.setLastReadPage(page);
        if (isRead != null) {
            chapter.setRead(isRead);
        } else if (chapter.getPageCount() != null && chapter.getPageCount() > 0 && page >= chapter.getPageCount() - 1) {
            chapter.setRead(true);
        }
        Chapter savedChapter = chapterRepository.save(chapter);

        Manga manga = chapter.getManga();
        if (manga != null) {
            manga.setLastReadChapter(chapter.getChapterNumber());
            manga.setLastReadPage(page);
            manga.setLastReadTimestamp(OffsetDateTime.now());
            if ("PLAN_TO_READ".equals(manga.getReadingStatus()) || manga.getReadingStatus() == null) {
                manga.setReadingStatus("READING");
            }
            mangaRepository.save(manga);
        }

        return savedChapter;
    }

    public Chapter toggleChapterRead(UUID chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));
        chapter.setRead(!chapter.isRead());
        return chapterRepository.save(chapter);
    }

    public Manga updateReadingStatus(UUID mangaId, String status) {
        Manga manga = mangaRepository.findById(mangaId)
                .orElseThrow(() -> new IllegalArgumentException("Manga not found with ID: " + mangaId));
        manga.setReadingStatus(status);
        return mangaRepository.save(manga);
    }

    // --- CBZ Archival Packaging ---

    public Path packageChapterToCbz(UUID chapterId) throws IOException {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        if (!chapter.isDownloaded() || chapter.getStoragePath() == null) {
            throw new IllegalStateException("Chapter is not stored locally.");
        }

        Path sourceDir = Paths.get(chapter.getStoragePath()).normalize();
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalStateException("Chapter directory does not exist on disk: " + sourceDir);
        }

        Manga manga = chapter.getManga();
        String safeTitle = (manga != null ? manga.getTitle() : "Manga")
                .replaceAll("[^a-zA-Z0-9.-]", "_");
        double chNum = chapter.getChapterNumber() != null ? chapter.getChapterNumber() : 0.0;
        String chDisplay = (chNum % 1 == 0) ? String.valueOf((int) chNum) : String.valueOf(chNum);
        String cbzFilename = String.format("%s_Ch_%s.cbz", safeTitle, chDisplay);

        Path cbzFile = sourceDir.resolveSibling(cbzFilename);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzFile));
             var stream = Files.list(sourceDir)) {
            List<Path> imageFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".jpg") || name.endsWith(".jpeg")
                                || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (Path imgPath : imageFiles) {
                ZipEntry entry = new ZipEntry(imgPath.getFileName().toString());
                zos.putNextEntry(entry);
                Files.copy(imgPath, zos);
                zos.closeEntry();
            }
        }

        chapter.setCbzPath(cbzFile.toString());
        chapterRepository.save(chapter);
        log.info("Successfully packaged CBZ archive for [{} Ch. {}] at [{}]", safeTitle, chDisplay, cbzFile);

        return cbzFile;
    }

    @Transactional(readOnly = true)
    public Resource getCbzResource(UUID chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        if (chapter.getCbzPath() == null) {
            throw new IllegalArgumentException("CBZ archive has not been created for this chapter yet.");
        }

        Path cbzPath = Paths.get(chapter.getCbzPath()).normalize();
        if (!Files.exists(cbzPath)) {
            throw new IllegalArgumentException("CBZ archive file missing on disk: " + cbzPath);
        }

        return new FileSystemResource(cbzPath);
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

        Path targetToOpen = null;
        if (chapter.getCbzPath() != null && Files.exists(Paths.get(chapter.getCbzPath()))) {
            targetToOpen = Paths.get(chapter.getCbzPath()).toAbsolutePath();
        } else if (chapter.getStoragePath() != null && Files.exists(Paths.get(chapter.getStoragePath()))) {
            targetToOpen = Paths.get(chapter.getStoragePath()).toAbsolutePath();
        }

        if (targetToOpen == null) {
            throw new IllegalArgumentException("Chapter media is not stored locally.");
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            new ProcessBuilder("explorer.exe", targetToOpen.toString()).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", targetToOpen.toString()).start();
        } else {
            new ProcessBuilder("xdg-open", targetToOpen.toString()).start();
        }
    }
}
