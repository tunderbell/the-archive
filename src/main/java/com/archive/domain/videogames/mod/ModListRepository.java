package com.archive.domain.videogames.mod;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModListRepository extends JpaRepository<ModList, UUID> {

    List<ModList> findByGameIdOrderByNameAsc(UUID gameId);

    Optional<ModList> findByGameIdAndActiveTrue(UUID gameId);
}
