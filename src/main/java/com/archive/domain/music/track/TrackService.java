package com.archive.domain.music.track;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class TrackService {

    private final TrackRepository trackRepository;

    public TrackService(TrackRepository trackRepository) {
        this.trackRepository = trackRepository;
    }

    /**
     * Creates a standalone single track with no album attached.
     */
    public Track createStandaloneTrack(Track track) {
        track.setAlbum(null);
        return trackRepository.save(track);
    }

    @Transactional(readOnly = true)
    public Optional<Track> getTrackById(UUID id) {
        return trackRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Track> getStandaloneTracks() {
        return trackRepository.findByAlbumIsNull();
    }

    @Transactional(readOnly = true)
    public List<Track> getTracksForAlbum(UUID albumId) {
        return trackRepository.findByAlbumIdOrderByTrackNumberAsc(albumId);
    }

    @Transactional(readOnly = true)
    public List<Track> searchTracks(String query) {
        return trackRepository.findByTitleContainingIgnoreCase(query);
    }

    public Track markTrackDownloaded(UUID trackId, String storagePath, Long durationSeconds, Integer bitrate) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new IllegalArgumentException("Track not found with ID: " + trackId));
        track.setDownloaded(true);
        track.setStoragePath(storagePath);
        track.setDurationSeconds(durationSeconds);
        track.setBitrate(bitrate);
        return trackRepository.save(track);
    }

    public void deleteTrack(UUID id) {
        trackRepository.deleteById(id);
    }
}
