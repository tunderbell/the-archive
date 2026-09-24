package com.archive.domain.anime;

import com.archive.domain.anime.episode.Episode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ============================================================================
 * CONTROLLER: AnimeController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes RESTful HTTP endpoints for querying, creating, and managing Anime
 * catalog entities and their associated episodes in the local SQLite vault.
 *
 * WHY IT IS USED:
 * Provides standard endpoints consumed by the APEX desktop console (MEDIA.VAULT)
 * and the automated Postman test suite.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/anime")
public class AnimeController {

    private final AnimeService animeService;

    public AnimeController(AnimeService animeService) {
        this.animeService = animeService;
    }

    /**
     * Lists all anime series registered in the local vault.
     * Example: GET /api/anime
     */
    @GetMapping
    public List<Anime> getAllAnime() {
        return animeService.getAllAnime();
    }

    /**
     * Retrieves an anime series by its unique UUID.
     * Example: GET /api/anime/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Anime> getAnimeById(@PathVariable UUID id) {
        return animeService.getAnimeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Searches for anime series by title substring.
     * Example: GET /api/anime/search?query=Bebop
     */
    @GetMapping("/search")
    public List<Anime> searchAnime(@RequestParam String query) {
        return animeService.searchAnime(query);
    }

    /**
     * Registers a new anime series in the local vault.
     * Example: POST /api/anime
     */
    @PostMapping
    public ResponseEntity<Anime> createAnime(@RequestBody Anime anime) {
        return ResponseEntity.ok(animeService.createAnime(anime));
    }

    /**
     * Ingests discovered episodes into an existing anime series.
     * Example: POST /api/anime/{id}/episodes
     */
    @PostMapping("/{id}/episodes")
    public ResponseEntity<List<Episode>> addEpisodes(@PathVariable UUID id, @RequestBody List<Episode> episodes) {
        return ResponseEntity.ok(animeService.addDiscoveredEpisodes(id, episodes));
    }

    /**
     * Deletes an anime series from the vault by its ID.
     * Example: DELETE /api/anime/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAnime(@PathVariable UUID id) {
        animeService.deleteAnime(id);
        return ResponseEntity.noContent().build();
    }
}
