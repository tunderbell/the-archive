package com.archive.workspace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * ENTITY: WorkspaceActivity
 * ============================================================================
 * WHAT IT DOES:
 * Represents an audit trail entry documenting significant user or automated system
 * actions within a shared workspace (e.g., newly ingested series, completed chapter
 * downloads, metadata updates, or member activities).
 *
 * WHY IT IS USED:
 * Provides a unified "Activity Feed" panel for the dashboard. Family members or team
 * collaborators can glance at the activity log to see what media was recently added,
 * updated, or harvested without manually querying individual media libraries.
 *
 * SYNTAX BREAKDOWN:
 * - @Entity & @Table: Maps this class to the "workspace_activity" table.
 * - @CreationTimestamp: Automatically stamps the record with the current UTC offset
 *   timestamp when inserted into the database.
 * ============================================================================
 */
@Entity
@Table(name = "workspace_activity")
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    /**
     * The name or identifier of the user or subsystem that performed the action
     * (e.g., "Alice", "Scraper Engine", "Command Parser").
     */
    @Column(nullable = false)
    private String actor;

    /**
     * Categorical action tag (e.g., "SERIES_REGISTERED", "CHAPTER_HARVESTED", "METADATA_UPDATED").
     */
    @Column(nullable = false)
    private String action;

    /**
     * Human-readable narrative description of the event.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    /**
     * Media domain context associated with this activity (e.g., "MANGA", "ANIME", "MUSIC").
     */
    @Column(name = "media_domain")
    private String mediaDomain;

    /**
     * Optional UUID referencing the specific media entity affected by this action.
     */
    @Column(name = "media_uuid")
    private UUID mediaId;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime timestamp;

    public WorkspaceActivity(String actor, String action, String details, String mediaDomain, UUID mediaId) {
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.mediaDomain = mediaDomain;
        this.mediaId = mediaId;
    }
}
