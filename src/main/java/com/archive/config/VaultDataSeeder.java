package com.archive.config;

import com.archive.core.model.Visibility;
import com.archive.domain.anime.*;
import com.archive.domain.manga.*;
import com.archive.domain.music.*;
import com.archive.domain.videogames.*;
import com.archive.scraper.ScraperService;
import com.archive.scraper.model.ScraperTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * ============================================================================
 * COMPONENT: VaultDataSeeder
 * ============================================================================
 * Seeds initial demonstrative library records (Manga, Anime, Music, Video Games)
 * and default Scraper site recipes into the local SQLite vault (archive_vault.db).
 * Essentially test data
 * ============================================================================
 */
@Component
public class VaultDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VaultDataSeeder.class);

    private final MangaService mangaService;
    private final AnimeService animeService;
    private final AlbumService albumService;
    private final GameService gameService;
    private final ScraperService scraperService;

    public VaultDataSeeder(MangaService mangaService,
                           AnimeService animeService,
                           AlbumService albumService,
                           GameService gameService,
                           ScraperService scraperService) {
        this.mangaService = mangaService;
        this.animeService = animeService;
        this.albumService = albumService;
        this.gameService = gameService;
        this.scraperService = scraperService;
    }

    @Override
    public void run(String... args) {
        seedMangaIfEmpty();
        seedAnimeIfEmpty();
        seedMusicIfEmpty();
        seedGamesIfEmpty();
        seedTemplatesIfEmpty();
    }

    private void seedTemplatesIfEmpty() {
        if (scraperService.getAllTemplates().isEmpty()) {
            log.info("[VaultDataSeeder] Initializing default Scraper site recipes...");

            ScraperTemplate t1 = new ScraperTemplate();
            t1.setName("Asura Comic");
            t1.setDomainName("asuracomic.net");
            t1.setTitleSelector("span.text-xl, h1");
            t1.setChapterListSelector("div.pl-4 a, #chapterlist a");
            t1.setImageSelector("div#readerarea img, div.w-full img");
            t1.setCoverImageSelector("img[alt='poster'], div.thumb img");
            t1.setRequiresJs(false);
            t1.setRateLimitMs(1000);
            scraperService.saveTemplate(t1);

            ScraperTemplate t2 = new ScraperTemplate();
            t2.setName("Asura Scans");
            t2.setDomainName("asurascans.com");
            t2.setTitleSelector("h1.entry-title, .series-title");
            t2.setChapterListSelector("#chapterlist li a");
            t2.setImageSelector("#readerarea img, .page-break img");
            t2.setCoverImageSelector(".thumb img");
            t2.setRequiresJs(false);
            t2.setRateLimitMs(1000);
            scraperService.saveTemplate(t2);
        }
    }

    private void seedMangaIfEmpty() {
        if (mangaService.getAllManga().isEmpty()) {
            log.info("[VaultDataSeeder] Initializing sample Manga catalog in SQLite vault...");

            Manga m1 = new Manga();
            m1.setTitle("Solo Leveling");
            m1.setAuthor("Chugong");
            m1.setArtist("DUBU (REDICE Studio)");
            m1.setType(MangaType.MANWHA);
            m1.setStatus(MangaStatus.COMPLETED);
            m1.setVisibility(Visibility.WORKSPACE);
            m1.setGenres(Set.of("Action", "Fantasy", "System"));
            mangaService.createManga(m1);

            Manga m2 = new Manga();
            m2.setTitle("Berserk");
            m2.setAuthor("Kentaro Miura");
            m2.setArtist("Kentaro Miura");
            m2.setType(MangaType.MANGA);
            m2.setStatus(MangaStatus.ONGOING);
            m2.setVisibility(Visibility.PRIVATE);
            m2.setGenres(Set.of("Dark Fantasy", "Psychological"));
            mangaService.createManga(m2);

            Manga m3 = new Manga();
            m3.setTitle("Vinland Saga");
            m3.setAuthor("Makoto Yukimura");
            m3.setArtist("Makoto Yukimura");
            m3.setType(MangaType.MANGA);
            m3.setStatus(MangaStatus.ONGOING);
            m3.setVisibility(Visibility.PRIVATE);
            m3.setGenres(Set.of("Historical", "Action", "Drama"));
            mangaService.createManga(m3);
        }
    }

    private void seedAnimeIfEmpty() {
        if (animeService.getAllAnime().isEmpty()) {
            log.info("[VaultDataSeeder] Initializing sample Anime series in SQLite vault...");

            Anime a1 = new Anime();
            a1.setTitle("Cowboy Bebop");
            a1.setStudio("Sunrise");
            a1.setDirector("Shinichiro Watanabe");
            a1.setReleaseYear(1998);
            a1.setFormat(AnimeFormat.TV);
            a1.setStatus(AnimeStatus.FINISHED);
            a1.setVisibility(Visibility.PRIVATE);
            a1.setGenres(Set.of("Sci-Fi", "Neo-Noir", "Space Western"));
            animeService.createAnime(a1);

            Anime a2 = new Anime();
            a2.setTitle("Neon Genesis Evangelion");
            a2.setStudio("Gainax / Tatsunoko");
            a2.setDirector("Hideaki Anno");
            a2.setReleaseYear(1995);
            a2.setFormat(AnimeFormat.TV);
            a2.setStatus(AnimeStatus.FINISHED);
            a2.setVisibility(Visibility.WORKSPACE);
            a2.setGenres(Set.of("Mecha", "Psychological", "Drama"));
            animeService.createAnime(a2);
        }
    }

    private void seedMusicIfEmpty() {
        if (albumService.getAllAlbums().isEmpty()) {
            log.info("[VaultDataSeeder] Initializing sample Music albums in SQLite vault...");

            Album al1 = new Album();
            al1.setTitle("Discovery");
            al1.setArtist("Daft Punk");
            al1.setReleaseYear(2001);
            al1.setRecordLabel("Virgin Records");
            al1.setAlbumType(AlbumType.ALBUM);
            al1.setVisibility(Visibility.PRIVATE);
            al1.setGenres(Set.of("French House", "Synthpop", "Electronic"));
            albumService.createAlbum(al1);

            Album al2 = new Album();
            al2.setTitle("Modal Soul");
            al2.setArtist("Nujabes");
            al2.setReleaseYear(2005);
            al2.setRecordLabel("Hydeout Productions");
            al2.setAlbumType(AlbumType.ALBUM);
            al2.setVisibility(Visibility.PRIVATE);
            al2.setGenres(Set.of("Lo-Fi Hip Hop", "Jazz Hop", "Instrumental"));
            albumService.createAlbum(al2);
        }
    }

    private void seedGamesIfEmpty() {
        if (gameService.getAllGames().isEmpty()) {
            log.info("[VaultDataSeeder] Initializing sample Video Games in SQLite vault...");

            VideoGames g1 = new VideoGames();
            g1.setTitle("Elden Ring");
            g1.setDeveloper("FromSoftware");
            g1.setPublisher("Bandai Namco Entertainment");
            g1.setReleaseYear(2022);
            g1.setPlayStatus(GameStatus.PLAYING);
            g1.setVisibility(Visibility.WORKSPACE);
            g1.setPlatforms(Set.of("PC", "PlayStation 5", "Xbox Series X"));
            gameService.createGame(g1);

            VideoGames g2 = new VideoGames();
            g2.setTitle("Cyberpunk 2077");
            g2.setDeveloper("CD Projekt Red");
            g2.setPublisher("CD Projekt");
            g2.setReleaseYear(2020);
            g2.setPlayStatus(GameStatus.COMPLETED);
            g2.setVisibility(Visibility.PRIVATE);
            g2.setPlatforms(Set.of("PC", "PlayStation 5"));
            gameService.createGame(g2);
        }
    }
}
