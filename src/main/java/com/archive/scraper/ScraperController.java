package com.archive.scraper;

import com.archive.domain.manga.Manga;
import com.archive.domain.manga.chapter.Chapter;
import com.archive.scraper.model.ScraperTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================================
 * CLASS: ScraperController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes RESTful endpoints for managing scraper site templates, triggering series
 * scouting, and running batch chapter image harvesting.
 *
 * WHY IT IS USED:
 * Allows developers and external tools (such as Postman) to manage scrapers and
 * trigger ingestion directly over HTTP.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/scraper")
public class ScraperController {

    private final ScraperService scraperService;

    public ScraperController(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    /**
     * Saves or updates a scraper template recipe.
     * Example: POST /api/scraper/template
     */
    @PostMapping("/template")
    public ResponseEntity<ScraperTemplate> saveTemplate(@RequestBody ScraperTemplate template) {
        return ResponseEntity.ok(scraperService.saveTemplate(template));
    }

    /**
     * Lists all registered site templates.
     * Example: GET /api/scraper/template
     */
    @GetMapping("/template")
    public List<ScraperTemplate> getAllTemplates() {
        return scraperService.getAllTemplates();
    }

    /**
     * Retrieves a site template for a specific domain.
     * Example: GET /api/scraper/template/asurascans.com
     */
    @GetMapping("/template/{domain}")
    public ResponseEntity<ScraperTemplate> getTemplateByDomain(@PathVariable String domain) {
        return scraperService.getTemplateByDomain(domain)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Scouts a series catalog page, extracts metadata and chapters, and persists them.
     * Example: POST /api/scraper/scout with body: {"url": "https://asurascans.com/solo-leveling"}
     */
    @PostMapping("/scout")
    public ResponseEntity<Manga> scoutSeries(@RequestBody Map<String, String> payload) throws Exception {
        String url = payload.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(scraperService.scoutAndRegisterSeries(url));
    }

    /**
     * Harvests chapter images and downloads them into the local vault storage.
     * Example: POST /api/scraper/harvest/{chapterId}
     */
    @PostMapping("/harvest/{chapterId}")
    public ResponseEntity<Chapter> harvestChapter(@PathVariable UUID chapterId) throws Exception {
        return ResponseEntity.ok(scraperService.harvestChapter(chapterId));
    }

    /**
     * Tests arbitrary CSS selectors against a live URL for the CSS Selector Wizard.
     * Example: POST /api/scraper/test-selector
     */
    @PostMapping("/test-selector")
    public ResponseEntity<Map<String, Object>> testSelectors(@RequestBody Map<String, Object> payload) throws Exception {
        String url = String.valueOf(payload.get("url"));
        String titleSelector = (String) payload.get("titleSelector");
        String chapterListSelector = (String) payload.get("chapterListSelector");
        String imageSelector = (String) payload.get("imageSelector");
        boolean requiresJs = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("requiresJs", "false")));

        return ResponseEntity.ok(scraperService.testSelectors(url, titleSelector, chapterListSelector, imageSelector, requiresJs));
    }
}
