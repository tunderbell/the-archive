package com.archive.domain.music.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrackRepository extends JpaRepository<Track, UUID> {

    List<Track> findByAlbumIdOrderByTrackNumberAsc(UUID albumId);

    // Query for all standalone tracks that don't belong to any album
    List<Track> findByAlbumIsNull();

    List<Track> findByArtistIgnoreCase(String artist);

    List<Track> findByTitleContainingIgnoreCase(String title);
}
