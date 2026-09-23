package com.archive.domain.manga;

import com.archive.domain.manga.chapter.Chapter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
}
