package com.archive.domain.manga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByMangaIdOrderByChapterNumberAsc(UUID mangaId);

    Optional<Chapter> findByMangaIdAndChapterNumber(UUID mangaId, Double chapterNumber);
}
