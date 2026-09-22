package com.archive.domain.anime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;


@Repository
public interface AnimeRepository extends JpaRepository<Anime, UUID>{
    List<Anime> findByTitleContainingIgnoreCase(String title);
    List<Anime> findByStatus(AnimeStatus status);
    List<Anime> findByStudioIgnoreCase(String studio);
}
