package com.archive.domain.music.playlist;

import com.archive.core.model.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    List<Playlist> findByNameContainingIgnoreCase(String name);

    List<Playlist> findByVisibility(Visibility visibility);
}
