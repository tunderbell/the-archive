package com.archive.workspace.model;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * ============================================================================
 * INTERFACE: WorkspaceActivityRepository
 * ============================================================================
 * WHAT IT DOES:
 * Data access interface providing query methods for retrieving recent workspace activity logs.
 *
 * WHY IT IS USED:
 * Powers the dashboard's "Recent Events / Activity Stream" widget.
 * ============================================================================
 */
@Repository
public interface WorkspaceActivityRepository extends JpaRepository<WorkspaceActivity, UUID> {

    /**
     * Retrieves the latest activity items across the entire workspace, newest first.
     */
    List<WorkspaceActivity> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Retrieves activity items filtered by a specific media domain (e.g., "MANGA").
     */
    List<WorkspaceActivity> findByMediaDomainOrderByTimestampDesc(String mediaDomain, Pageable pageable);
}
