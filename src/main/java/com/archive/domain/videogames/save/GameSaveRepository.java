package com.archive.domain.videogames.save;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GameSaveRepository extends JpaRepository<GameSave, UUID> {

    List<GameSave> findByGameIdOrderBySaveTimestampDesc(UUID gameId);

    List<GameSave> findByGameIdAndIsBackupTrue(UUID gameId);
}
