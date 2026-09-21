package com.archive.domain.videogames.dlc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DlcRepository extends JpaRepository<Dlc, UUID> {

    List<Dlc> findByGameIdOrderByNameAsc(UUID gameId);

    List<Dlc> findByGameIdAndInstalledTrue(UUID gameId);
}
