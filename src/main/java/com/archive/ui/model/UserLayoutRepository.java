package com.archive.ui.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================================
 * INTERFACE: UserLayoutRepository
 * ============================================================================
 * WHAT IT DOES:
 * Repository for storing and retrieving Dockview layout configurations.
 * ============================================================================
 */
@Repository
public interface UserLayoutRepository extends JpaRepository<UserLayout, UUID> {

    Optional<UserLayout> findByLayoutName(String layoutName);

    boolean existsByLayoutName(String layoutName);
}
