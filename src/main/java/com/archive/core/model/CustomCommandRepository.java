package com.archive.core.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================================
 * INTERFACE: CustomCommandRepository
 * ============================================================================
 * WHAT IT DOES:
 * Data access operations for user-defined custom commands.
 *
 * WHY IT IS USED:
 * Allows fast retrieval of custom aliases by trigger name when the user inputs a command.
 * ============================================================================
 */
@Repository
public interface CustomCommandRepository extends JpaRepository<CustomCommand, UUID> {

    Optional<CustomCommand> findByTriggerName(String triggerName);

    boolean existsByTriggerName(String triggerName);

    void deleteByTriggerName(String triggerName);
}
