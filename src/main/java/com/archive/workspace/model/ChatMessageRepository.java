package com.archive.workspace.model;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * ============================================================================
 * INTERFACE: ChatMessageRepository
 * ============================================================================
 * WHAT IT DOES:
 * Provides high-performance database querying and persistence operations for
 * ChatMessage records.
 *
 * WHY IT IS USED:
 * Enables the REST controller to query paginated chat history filtered by channel
 * and sorted in reverse chronological order so users see the most recent activity.
 *
 * SYNTAX BREAKDOWN:
 * - @Repository: Identifies this interface as a Spring Data access component.
 * - Pageable: A Spring Data interface representing pagination information (page index,
 *   limit size, sort orders), which translates into SQL "LIMIT ? OFFSET ?".
 * ============================================================================
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Retrieves messages within a specific channel, sorted from newest to oldest.
     *
     * @param channel  The channel name (e.g., "general", "releases").
     * @param pageable Pagination configuration limiting query results.
     * @return List of matching ChatMessage entities.
     */
    List<ChatMessage> findByChannelOrderByTimestampDesc(String channel, Pageable pageable);
}
