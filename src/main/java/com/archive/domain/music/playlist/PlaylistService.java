package com.archive.domain.music.playlist;

import com.archive.core.model.Visibility;
import com.archive.domain.music.track.Track;
import com.archive.domain.music.track.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;

    public PlaylistService(PlaylistRepository playlistRepository, TrackRepository trackRepository) {
        this.playlistRepository = playlistRepository;
        this.trackRepository = trackRepository;
    }

    public Playlist createPlaylist(String name, String description, Visibility visibility) {
        Playlist playlist = new Playlist();
        playlist.setName(name);
        playlist.setDescription(description);
        playlist.setVisibility(visibility != null ? visibility : Visibility.PRIVATE);
        return playlistRepository.save(playlist);
    }

    @Transactional(readOnly = true)
    public Optional<Playlist> getPlaylistById(UUID id) {
        return playlistRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Playlist> getAllPlaylists() {
        return playlistRepository.findAll();
    }

    public Playlist addTrackToPlaylist(UUID playlistId, UUID trackId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found with ID: " + playlistId));
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new IllegalArgumentException("Track not found with ID: " + trackId));

        playlist.addTrack(track);
        return playlistRepository.save(playlist);
    }

    public Playlist removeTrackFromPlaylist(UUID playlistId, UUID trackId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found with ID: " + playlistId));

        playlist.getItems().removeIf(item -> item.getTrack().getId().equals(trackId));
        // Re-index positions
        for (int i = 0; i < playlist.getItems().size(); i++) {
            playlist.getItems().get(i).setPosition(i);
        }
        return playlistRepository.save(playlist);
    }

    /**
     * Reorders a track within the playlist (for drag-and-drop UI interaction).
     */
    public Playlist reorderTrack(UUID playlistId, int fromIndex, int toIndex) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found with ID: " + playlistId));

        List<PlaylistTrack> items = playlist.getItems();
        if (fromIndex >= 0 && fromIndex < items.size() && toIndex >= 0 && toIndex < items.size()) {
            PlaylistTrack moved = items.remove(fromIndex);
            items.add(toIndex, moved);
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setPosition(i);
            }
        }
        return playlistRepository.save(playlist);
    }

    public void deletePlaylist(UUID id) {
        playlistRepository.deleteById(id);
    }
}
