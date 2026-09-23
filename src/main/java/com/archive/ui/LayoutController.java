package com.archive.ui;

import com.archive.ui.model.UserLayout;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * CLASS: LayoutController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes RESTful endpoints for saving, loading, and listing Dockview workspace
 * presets and APEX command bar preferences.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/ui/layout")
public class LayoutController {

    private final UserLayoutService layoutService;

    public LayoutController(UserLayoutService layoutService) {
        this.layoutService = layoutService;
    }

    /**
     * Retrieves a layout configuration by preset name.
     * Example: GET /api/ui/layout/OPS
     */
    @GetMapping("/{name}")
    public ResponseEntity<UserLayout> getLayout(@PathVariable String name) {
        return layoutService.getLayout(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all saved workspace layout presets.
     * Example: GET /api/ui/layout
     */
    @GetMapping
    public List<UserLayout> getAllLayouts() {
        return layoutService.getAllLayouts();
    }

    /**
     * Saves or updates a workspace layout preset.
     * Example: POST /api/ui/layout with body:
     * {"name": "OPS", "json": "{...}", "commandBarPosition": "BOTTOM"}
     */
    @PostMapping
    public ResponseEntity<UserLayout> saveLayout(@RequestBody Map<String, String> payload) {
        String name = payload.getOrDefault("name", "default");
        String json = payload.getOrDefault("json", "{}");
        String barPosition = payload.getOrDefault("commandBarPosition", "TOP");

        UserLayout saved = layoutService.saveLayout(name, json, barPosition);
        return ResponseEntity.ok(saved);
    }
}
