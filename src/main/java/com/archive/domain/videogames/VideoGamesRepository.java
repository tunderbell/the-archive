package com.archive.domain.videogames;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface VideoGamesRepository extends JpaRepository<VideoGames, UUID> {
    List<VideoGames> findByTitleContainingIgnoreCase(String title);
    List<VideoGames> findByPlayStatus(GameStatus playStatus);
}
