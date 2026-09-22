package com.archive.domain.music;

import com.archive.domain.music.track.Track;
import com.archive.domain.music.track.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;

    public AlbumService(AlbumRepository albumRepository, TrackRepository trackRepository) {
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
    }

    public Album createAlbum(Album album) {
        return albumRepository.save(album);
    }

    @Transactional(readOnly = true)
    public Optional<Album> getAlbumById(UUID id) {
        return albumRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Album> getAllAlbums() {
        return albumRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Album> searchAlbums(String query) {
        return albumRepository.findByTitleContainingIgnoreCase(query);
    }

    public Track addTrackToAlbum(UUID albumId, Track track) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album not found with ID: " + albumId));
        album.addTrack(track);
        return trackRepository.save(track);
    }

    public void deleteAlbum(UUID id) {
        albumRepository.deleteById(id);
    }
}
