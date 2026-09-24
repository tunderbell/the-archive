package com.archive.workspace;

import com.archive.workspace.model.WorkspaceActivity;
import com.archive.workspace.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * CLASS: WorkspaceController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes RESTful HTTP endpoints for managing and inspecting shared workspace state,
 * including activity feeds, member presence, and operational telemetry.
 *
 * WHY IT IS USED:
 * Provides the backend data endpoints for the React dashboard's "Workspace Overview"
 * and "Recent Activity" widgets.
 *
 * SYNTAX BREAKDOWN:
 * - @RestController: Marks this class as an HTTP controller with serialized JSON outputs.
 * - @RequestMapping("/api/workspace"): Sets the root URL path for all endpoints in this class.
 * - @GetMapping & @PostMapping: Maps HTTP GET and POST requests to handler methods.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * Retrieves the most recent activities across all media domains.
     * Example: GET /api/workspace/activities?limit=25
     */
    @GetMapping("/activities")
    public List<WorkspaceActivity> getRecentActivities(@RequestParam(defaultValue = "25") int limit) {
        return workspaceService.getRecentActivities(limit);
    }

    /**
     * Retrieves recent activities filtered by domain (e.g. "MANGA", "ANIME").
     * Example: GET /api/workspace/activities/MANGA?limit=15
     */
    @GetMapping("/activities/{domain}")
    public List<WorkspaceActivity> getActivitiesByDomain(
            @PathVariable String domain,
            @RequestParam(defaultValue = "15") int limit) {
        return workspaceService.getActivitiesByDomain(domain.toUpperCase(), limit);
    }

    /**
     * Records a new activity entry into the workspace audit trail.
     * Example: POST /api/workspace/activities
     */
    @PostMapping("/activities")
    public ResponseEntity<WorkspaceActivity> createActivity(@RequestBody WorkspaceActivity activity) {
        WorkspaceActivity saved = workspaceService.recordActivity(
                activity.getActor(),
                activity.getAction(),
                activity.getDetails(),
                activity.getMediaDomain(),
                activity.getMediaId()
        );
        return ResponseEntity.ok(saved);
    }

    /**
     * Provides basic workspace status information (e.g., active database profile).
     * Example: GET /api/workspace/status
     */
    @GetMapping("/status")
    public Map<String, Object> getWorkspaceStatus() {
        return Map.of(
                "application", "The Archive",
                "activeProfile", activeProfile,
                "status", "ONLINE"
        );
    }
}
