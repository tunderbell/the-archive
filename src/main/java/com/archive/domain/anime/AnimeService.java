package com.archive.domain.anime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class AnimeService {

    private final AnimeRepository animeRepository;
    private final EpisodeRepository episodeRepository;

    public AnimeService(AnimeRepository animeRepository, EpisodeRepository episodeRepository) {
        this.animeRepository = animeRepository;
        this.episodeRepository = episodeRepository;
    }

    // --- Series Operations ---

    public Anime createAnime(Anime anime) {
        return animeRepository.save(anime);
    }

    @Transactional(readOnly = true)
    public Optional<Anime> getAnimeById(UUID id) {
        return animeRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Anime> getAllAnime() {
        return animeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Anime> searchAnime(String query) {
        return animeRepository.findByTitleContainingIgnoreCase(query);
    }

    public void deleteAnime(UUID id) {
        animeRepository.deleteById(id);
    }

    // --- Episode Ingestion & Deduplication (For Scout) ---

    /**
     * Ingests episodes found by a scraper or directory scanner.
     * Prevents duplicate episode numbers on repeated runs.
     */
    public List<Episode> addDiscoveredEpisodes(UUID animeId, List<Episode> discoveredEpisodes) {
        Anime anime = animeRepository.findById(animeId)
                .orElseThrow(() -> new IllegalArgumentException("Anime not found with ID: " + animeId));

        List<Episode> existingEpisodes = episodeRepository.findByAnimeIdOrderByEpisodeNumberAsc(animeId);
        Set<Double> existingNumbers = new HashSet<>();
        for (Episode ep : existingEpisodes) {
            existingNumbers.add(ep.getEpisodeNumber());
        }

        List<Episode> newlySaved = new ArrayList<>();
        for (Episode episode : discoveredEpisodes) {
            if (!existingNumbers.contains(episode.getEpisodeNumber())) {
                anime.addEpisode(episode);
                newlySaved.add(episode);
                existingNumbers.add(episode.getEpisodeNumber());
            }
        }

        anime.setEpisodesTotal(existingNumbers.size());
        anime.setLastChecked(OffsetDateTime.now());
        animeRepository.save(anime);

        return newlySaved;
    }

    // --- Episode Download Progression (For Harvester) ---

    /**
     * Marks an episode as downloaded and updates the parent Anime's downloaded count.
     */
    public Episode markEpisodeDownloaded(UUID episodeId, String storagePath, int durationSeconds, String resolution) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new IllegalArgumentException("Episode not found with ID: " + episodeId));

        episode.setDownloaded(true);
        episode.setStoragePath(storagePath);
        episode.setDurationSeconds(durationSeconds);
        episode.setResolution(resolution);
        Episode savedEpisode = episodeRepository.save(episode);

        Anime anime = episode.getAnime();
        if (anime != null) {
            long count = anime.getEpisodes().stream().filter(Episode::isDownloaded).count();
            anime.setEpisodesDownloaded((int) count);
            animeRepository.save(anime);
        }

        return savedEpisode;
    }

    @Transactional(readOnly = true)
    public List<Episode> getEpisodesForAnime(UUID animeId) {
        return episodeRepository.findByAnimeIdOrderByEpisodeNumberAsc(animeId);
    }

    // --- Scraper Configuration Overrides ---

    public Anime updateSelectors(UUID animeId, String episodeSelector, String videoSelector) {
        Anime anime = animeRepository.findById(animeId)
                .orElseThrow(() -> new IllegalArgumentException("Anime not found with ID: " + animeId));

        anime.setCustomEpisodeSelector(episodeSelector);
        anime.setCustomVideoSelector(videoSelector);
        return animeRepository.save(anime);
    }
}
