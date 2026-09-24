package com.archive.domain.music;

import com.archive.domain.music.track.Track;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ============================================================================
 * CONTROLLER: MusicController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes RESTful HTTP endpoints for managing musical albums, track sequences,
 * and discography metadata in the local SQLite vault.
 *
 * WHY IT IS USED:
 * Provides endpoints consumed by the APEX console's MEDIA.VAULT buffer
 * under the [MUSIC] sub-tab.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final AlbumService albumService;

    public MusicController(AlbumService albumService) {
        this.albumService = albumService;
    }

    /**
     * Lists all albums in the music collection.
     * Example: GET /api/music
     */
    @GetMapping
    public List<Album> getAllAlbums() {
        return albumService.getAllAlbums();
    }

    /**
     * Retrieves an album by its unique UUID.
     * Example: GET /api/music/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Album> getAlbumById(@PathVariable UUID id) {
        return albumService.getAlbumById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Searches albums by artist or title keyword.
     * Example: GET /api/music/search?query=Daft+Punk
     */
    @GetMapping("/search")
    public List<Album> searchAlbums(@RequestParam String query) {
        return albumService.searchAlbums(query);
    }

    /**
     * Manually registers a new album in the local vault.
     * Example: POST /api/music
     */
    @PostMapping
    public ResponseEntity<Album> createAlbum(@RequestBody Album album) {
        return ResponseEntity.ok(albumService.createAlbum(album));
    }

    /**
     * Appends a track to an album.
     * Example: POST /api/music/{id}/tracks
     */
    @PostMapping("/{id}/tracks")
    public ResponseEntity<Track> addTrack(@PathVariable UUID id, @RequestBody Track track) {
        return ResponseEntity.ok(albumService.addTrackToAlbum(id, track));
    }

    /**
     * Deletes an album by ID.
     * Example: DELETE /api/music/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable UUID id) {
        albumService.deleteAlbum(id);
        return ResponseEntity.noContent().build();
    }
}
