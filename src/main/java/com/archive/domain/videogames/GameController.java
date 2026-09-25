package com.archive.domain.videogames;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ============================================================================
 * CONTROLLER: GameController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes RESTful HTTP endpoints for managing video game vault records,
 * platforms, engines, and play statuses (PLAYING, COMPLETED, BACKLOG, etc.).
 *
 * WHY IT IS USED:
 * Provides endpoints consumed by the APEX console's MEDIA.VAULT buffer
 * under the [GAMES] sub-tab.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * Lists all video games registered in the local vault.
     * Example: GET /api/games
     */
    @GetMapping
    public List<VideoGames> getAllGames() {
        return gameService.getAllGames();
    }

    /**
     * Retrieves a game record by its unique UUID.
     * Example: GET /api/games/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<VideoGames> getGameById(@PathVariable UUID id) {
        return gameService.getGameById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Searches games by title substring.
     * Example: GET /api/games/search?query=Elden
     */
    @GetMapping("/search")
    public List<VideoGames> searchGames(@RequestParam String query) {
        return gameService.searchGames(query);
    }

    /**
     * Registers a new game in the vault.
     * Example: POST /api/games
     */
    @PostMapping
    public ResponseEntity<VideoGames> createGame(@RequestBody VideoGames game) {
        return ResponseEntity.ok(gameService.createGame(game));
    }

    /**
     * Updates the play status of an existing game.
     * Example: PUT /api/games/{id}/status?status=PLAYING
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<VideoGames> updatePlayStatus(@PathVariable UUID id, @RequestParam GameStatus status) {
        return ResponseEntity.ok(gameService.updatePlayStatus(id, status));
    }

    /**
     * Deletes a game record by its unique UUID.
     * Example: DELETE /api/games/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGame(@PathVariable UUID id) {
        gameService.deleteGame(id);
        return ResponseEntity.noContent().build();
    }
}
