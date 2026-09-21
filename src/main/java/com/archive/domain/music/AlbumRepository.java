package com.archive.domain.music;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlbumRepository extends JpaRepository<Album, UUID> {

    List<Album> findByArtistIgnoreCase(String artist);

    List<Album> findByTitleContainingIgnoreCase(String title);
}
