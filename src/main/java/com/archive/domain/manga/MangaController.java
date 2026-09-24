package com.archive.domain.manga;

import com.archive.domain.manga.chapter.Chapter;
import com.archive.domain.manga.chapter.dto.ChapterPagesDto;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================================
 * CLASS: MangaController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes RESTful endpoints for querying and managing manga catalog entries and chapters.
 *
 * WHY IT IS USED:
 * Provides standard HTTP CRUD endpoints for frontend media browsers and API testing tools (Postman).
 * ============================================================================
 */
@RestController
@RequestMapping("/api/manga")
public class MangaController {

    private final MangaService mangaService;

    public MangaController(MangaService mangaService) {
        this.mangaService = mangaService;
    }

    /**
     * Lists all manga in the library.
     * Example: GET /api/manga
     */
    @GetMapping
    public List<Manga> getAllManga() {
        return mangaService.getAllManga();
    }

    /**
     * Retrieves a single manga by its unique UUID.
     * Example: GET /api/manga/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Manga> getMangaById(@PathVariable UUID id) {
        return mangaService.getMangaById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves all chapters for a manga ordered by chapterNumber ascending.
     * Example: GET /api/manga/{id}/chapters
     */
    @GetMapping("/{id}/chapters")
    public List<Chapter> getChaptersForManga(@PathVariable UUID id) {
        return mangaService.getChaptersForManga(id);
    }

    /**
     * Manually registers a new manga entity.
     * Example: POST /api/manga
     */
    @PostMapping
    public ResponseEntity<Manga> createManga(@RequestBody Manga manga) {
        return ResponseEntity.ok(mangaService.createManga(manga));
    }

    /**
     * Deletes a manga by ID.
     * Example: DELETE /api/manga/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteManga(@PathVariable UUID id) {
        mangaService.deleteManga(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves chapter pages and metadata for the in-app Reader Buffer.
     * Example: GET /api/manga/chapters/{chapterId}/pages
     */
    @GetMapping("/chapters/{chapterId}/pages")
    public ResponseEntity<ChapterPagesDto> getChapterPages(@PathVariable UUID chapterId) {
        return ResponseEntity.ok(mangaService.getChapterPages(chapterId));
    }

    /**
     * Streams an individual page image binary to the browser.
     * Example: GET /api/manga/chapters/{chapterId}/pages/{filename}
     */
    @GetMapping("/chapters/{chapterId}/pages/{filename:.+}")
    public ResponseEntity<Resource> getChapterPageImage(
            @PathVariable UUID chapterId,
            @PathVariable String filename) {
        Resource resource = mangaService.getChapterPageResource(chapterId, filename);
        String contentType = determineContentType(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    /**
     * Launches the default operating system file viewer / CBZ reader targeting the chapter.
     * Example: POST /api/manga/chapters/{chapterId}/open-external
     */
    @PostMapping("/chapters/{chapterId}/open-external")
    public ResponseEntity<Map<String, Object>> openExternalViewer(@PathVariable UUID chapterId) throws IOException {
        mangaService.openInExternalViewer(chapterId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Opened in system viewer"));
    }

    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
