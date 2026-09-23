package com.archive.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * ENTITY: CustomCommand
 * ============================================================================
 * WHAT IT DOES:
 * Represents a persistent user-defined command alias or multi-step pipeline macro.
 *
 * WHY IT IS USED:
 * Allows users to create in-app shortcuts (Tier 1) and chained multi-command
 * sequences (Tier 2) that survive application restarts.
 *
 * SYNTAX BREAKDOWN:
 * - @Entity & @Table(name = "custom_command"): Maps this class to the database table.
 * - triggerName: The shorthand word typed by the user (e.g., "sl", "sync-asura").
 * - templateString: The underlying command or pipeline (e.g., "scrape --url $1 && harvest --latest").
 * - isPipeline: Flags whether the template contains multiple chained commands (via '&&').
 * ============================================================================
 */
@Entity
@Table(name = "custom_command")
@Getter
@Setter
@NoArgsConstructor
public class CustomCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "trigger_name", nullable = false, unique = true)
    private String triggerName;

    private String description;

    @Column(name = "template_string", nullable = false, columnDefinition = "TEXT")
    private String templateString;

    @Column(name = "is_pipeline", nullable = false)
    private boolean isPipeline = false;

    @CreationTimestamp
    @Column(name = "date_created", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateCreated;

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastUpdated;

    public CustomCommand(String triggerName, String templateString, String description, boolean isPipeline) {
        this.triggerName = triggerName.trim().toLowerCase();
        this.templateString = templateString.trim();
        this.description = description;
        this.isPipeline = isPipeline;
    }
}
