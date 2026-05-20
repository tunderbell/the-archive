package com.archive.domain.manga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repo for manga entities.
 * Inherits standard CRUD operations from JpaRepository.
 */
@Repository
public interface MangaRepository extends JpaRepository<Manga, UUID> {
    // Spring Boot will automatically generate the implementation for this interface.
}