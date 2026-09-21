package com.archive.domain.videogames.mod;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GameModRepository extends JpaRepository<GameMod, UUID> {

    List<GameMod> findByGameIdOrderByDefaultLoadOrderAsc(UUID gameId);

    List<GameMod> findByGameIdAndEnabledTrue(UUID gameId);
}
