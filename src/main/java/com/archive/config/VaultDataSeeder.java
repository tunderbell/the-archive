package com.archive.config;

import com.archive.core.model.Visibility;
import com.archive.domain.anime.*;
import com.archive.domain.manga.*;
import com.archive.domain.manga.chapter.Chapter;
import com.archive.domain.manga.chapter.ChapterRepository;
import com.archive.domain.music.*;
import com.archive.domain.videogames.*;
import com.archive.scraper.ScraperService;
import com.archive.scraper.model.ScraperTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
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
    private final ChapterRepository chapterRepository;
    private final DataSource dataSource;

    public VaultDataSeeder(MangaService mangaService,
                           AnimeService animeService,
                           AlbumService albumService,
                           GameService gameService,
                           ScraperService scraperService,
                           ChapterRepository chapterRepository,
                           DataSource dataSource) {
        this.mangaService = mangaService;
        this.animeService = animeService;
        this.albumService = albumService;
        this.gameService = gameService;
        this.scraperService = scraperService;
        this.chapterRepository = chapterRepository;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        ensureSchemaColumns();
        seedMangaIfEmpty();
        seedAnimeIfEmpty();
        seedMusicIfEmpty();
        seedGamesIfEmpty();
        seedTemplatesIfEmpty();
        seedChaptersIfEmpty();
    }

    /**
     * Self-healing SQLite schema verification:
     * Ensures all necessary columns exist on startup even if Hibernate's SQLite dialect
     * doesn't perform automated ALTER TABLE statements.
     */
    private void ensureSchemaColumns() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            // Check chapter table columns
            var rsChapter = stmt.executeQuery("PRAGMA table_info(chapter)");
            Set<String> chapterCols = new HashSet<>();
            while (rsChapter.next()) {
                chapterCols.add(rsChapter.getString("name").toLowerCase());
            }
            if (!chapterCols.contains("is_read")) {
                stmt.execute("ALTER TABLE chapter ADD COLUMN is_read BOOLEAN DEFAULT 0");
                log.info("[VaultDataSeeder] Added missing column 'is_read' to chapter table.");
            }
            if (!chapterCols.contains("last_read_page")) {
                stmt.execute("ALTER TABLE chapter ADD COLUMN last_read_page INTEGER DEFAULT 0");
                log.info("[VaultDataSeeder] Added missing column 'last_read_page' to chapter table.");
            }
            if (!chapterCols.contains("cbz_path")) {
                stmt.execute("ALTER TABLE chapter ADD COLUMN cbz_path VARCHAR(255)");
                log.info("[VaultDataSeeder] Added missing column 'cbz_path' to chapter table.");
            }

            // Check manga table columns
            var rsManga = stmt.executeQuery("PRAGMA table_info(manga)");
            Set<String> mangaCols = new HashSet<>();
            while (rsManga.next()) {
                mangaCols.add(rsManga.getString("name").toLowerCase());
            }
            if (!mangaCols.contains("last_read_chapter")) {
                stmt.execute("ALTER TABLE manga ADD COLUMN last_read_chapter DOUBLE DEFAULT 0");
                log.info("[VaultDataSeeder] Added missing column 'last_read_chapter' to manga table.");
            }
            if (!mangaCols.contains("last_read_page")) {
                stmt.execute("ALTER TABLE manga ADD COLUMN last_read_page INTEGER DEFAULT 0");
                log.info("[VaultDataSeeder] Added missing column 'last_read_page' to manga table.");
            }
            if (!mangaCols.contains("reading_status")) {
                stmt.execute("ALTER TABLE manga ADD COLUMN reading_status VARCHAR(50) DEFAULT 'UNREAD'");
                log.info("[VaultDataSeeder] Added missing column 'reading_status' to manga table.");
            }
        } catch (Exception e) {
            log.warn("[VaultDataSeeder] Schema column check note: {}", e.getMessage());
        }
    }

    private void seedTemplatesIfEmpty() {
        boolean hasAsuraComic = scraperService.getAllTemplates().stream()
                .anyMatch(t -> "asuracomic.net".equalsIgnoreCase(t.getDomainName()));
        if (!hasAsuraComic) {
            log.info("[VaultDataSeeder] Initializing Asura Comic site recipe...");
            ScraperTemplate t1 = new ScraperTemplate();
            t1.setName("Asura Comic");
            t1.setDomainName("asuracomic.net");
            t1.setTitleSelector("span.text-xl, h1, .entry-title");
            t1.setChapterListSelector("div.pl-4 a, #chapterlist a, a[href*='/chapter/'], a[href*='/chapter-']");
            t1.setImageSelector("div[data-page] img, img[data-page-index], div.w-full img, #readerarea img, div#readerarea img");
            t1.setCoverImageSelector("img[alt='poster'], div.thumb img");
            t1.setRequiresJs(false);
            t1.setRateLimitMs(1000);
            scraperService.saveTemplate(t1);
        }

        boolean hasAsuraScans = scraperService.getAllTemplates().stream()
                .anyMatch(t -> "asurascans.com".equalsIgnoreCase(t.getDomainName()));
        if (!hasAsuraScans) {
            log.info("[VaultDataSeeder] Initializing Asura Scans site recipe...");
            ScraperTemplate t2 = new ScraperTemplate();
            t2.setName("Asura Scans");
            t2.setDomainName("asurascans.com");
            t2.setTitleSelector("h1.text-xl, h1, .entry-title, .series-title");
            t2.setChapterListSelector("div.pl-4 a, #chapterlist a, a[href*='/chapter/'], a[href*='/chapter-']");
            t2.setImageSelector("div[data-page] img, img[data-page-index], div.w-full img, #readerarea img, div#readerarea img");
            t2.setCoverImageSelector("img[alt='poster'], div.thumb img");
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

    private void seedChaptersIfEmpty() {
        if (chapterRepository.count() == 0) {
            log.info("[VaultDataSeeder] Initializing sample chapters and generating local page assets...");

            Manga manga = mangaService.getAllManga().stream()
                    .filter(m -> m.getTitle().equalsIgnoreCase("Solo Leveling"))
                    .findFirst()
                    .orElseGet(() -> {
                        Manga m = new Manga();
                        m.setTitle("Solo Leveling");
                        m.setAuthor("Chugong");
                        m.setArtist("DUBU (REDICE Studio)");
                        m.setType(MangaType.MANWHA);
                        m.setStatus(MangaStatus.COMPLETED);
                        m.setVisibility(Visibility.WORKSPACE);
                        m.setGenres(Set.of("Action", "Fantasy", "System"));
                        return mangaService.createManga(m);
                    });

            Path chapterDir = Paths.get("./archive_vault/manga/Solo_Leveling/Chapter_1.0");
            try {
                Files.createDirectories(chapterDir);
                for (int i = 1; i <= 4; i++) {
                    Path imgPath = chapterDir.resolve(String.format("%03d.png", i));
                    if (!Files.exists(imgPath)) {
                        createSamplePageImage(imgPath, "SOLO LEVELING", 1.0, i, 4);
                    }
                }

                Chapter ch1 = new Chapter();
                ch1.setChapterNumber(1.0);
                ch1.setTitle("I'm Used to It");
                ch1.setDownloaded(true);
                ch1.setPageCount(4);
                ch1.setStoragePath(chapterDir.toAbsolutePath().toString());
                ch1.setSourceUrl("https://asuracomic.net/series/solo-leveling-chapter-1");
                ch1.setManga(manga);
                manga.addChapter(ch1);
                chapterRepository.save(ch1);

                String[] titles = new String[]{"If I Had Just One More Chance", "The Daily Quest", "The Weakest Hunter"};
                for (int i = 2; i <= 4; i++) {
                    Chapter ch = new Chapter();
                    ch.setChapterNumber((double) i);
                    ch.setTitle(titles[i - 2]);
                    ch.setDownloaded(false);
                    ch.setPageCount(0);
                    ch.setSourceUrl("https://asuracomic.net/series/solo-leveling-chapter-" + i);
                    ch.setManga(manga);
                    manga.addChapter(ch);
                    chapterRepository.save(ch);
                }

                manga.setTotalChapters(4);
                manga.setDownloadedChapters(1);
                mangaService.createManga(manga);
                log.info("[VaultDataSeeder] Successfully seeded 4 sample chapters for [{}] with generated page assets.", manga.getTitle());
            } catch (Exception e) {
                log.error("[VaultDataSeeder] Failed to seed sample chapter assets: {}", e.getMessage(), e);
            }
        }
    }

    private void createSamplePageImage(Path targetPath, String seriesTitle, double chapterNum, int pageNum, int totalPages) {
        int width = 800;
        int height = 1200;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dark background matching APEX console
        g2d.setColor(new Color(10, 13, 17));
        g2d.fillRect(0, 0, width, height);

        // Grid lines (subtle cyberpunk pattern)
        g2d.setColor(new Color(21, 27, 34));
        for (int y = 0; y < height; y += 40) {
            g2d.drawLine(0, y, width, y);
        }
        for (int x = 0; x < width; x += 40) {
            g2d.drawLine(x, 0, x, height);
        }

        // Inner tactical border
        g2d.setColor(new Color(56, 152, 236)); // Cyan
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(40, 40, width - 80, height - 80);

        // Secondary amber corner brackets
        g2d.setColor(new Color(240, 140, 0)); // Amber
        int cornerLen = 30;
        g2d.drawLine(35, 35, 35 + cornerLen, 35);
        g2d.drawLine(35, 35, 35, 35 + cornerLen);
        g2d.drawLine(width - 35, 35, width - 35 - cornerLen, 35);
        g2d.drawLine(width - 35, 35, width - 35, 35 + cornerLen);
        g2d.drawLine(35, height - 35, 35 + cornerLen, height - 35);
        g2d.drawLine(35, height - 35, 35, height - 35 - cornerLen);
        g2d.drawLine(width - 35, height - 35, width - 35 - cornerLen, height - 35);
        g2d.drawLine(width - 35, height - 35, width - 35, height - 35 - cornerLen);

        // Header Text
        g2d.setColor(new Color(56, 152, 236));
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        g2d.drawString("THE ARCHIVE // APEX MEDIA VAULT", 60, 80);

        g2d.setColor(new Color(138, 149, 165));
        g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g2d.drawString("SECTOR: MANGA READER BUFFER // LOCAL CACHE", 60, 105);

        // Center Box: Chapter Title & Art
        g2d.setColor(new Color(17, 22, 29));
        g2d.fillRect(80, 200, width - 160, 600);
        g2d.setColor(new Color(33, 40, 50));
        g2d.drawRect(80, 200, width - 160, 600);

        g2d.setColor(new Color(240, 140, 0));
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        FontMetrics fmTitle = g2d.getFontMetrics();
        int titleX = (width - fmTitle.stringWidth(seriesTitle)) / 2;
        g2d.drawString(seriesTitle, titleX, 350);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        String chText = String.format("CHAPTER %.0f", chapterNum);
        FontMetrics fmCh = g2d.getFontMetrics();
        g2d.drawString(chText, (width - fmCh.stringWidth(chText)) / 2, 420);

        g2d.setColor(new Color(63, 185, 80));
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        String pageText = String.format("[ PAGE %02d OF %02d ]", pageNum, totalPages);
        FontMetrics fmPg = g2d.getFontMetrics();
        g2d.drawString(pageText, (width - fmPg.stringWidth(pageText)) / 2, 500);

        g2d.setColor(new Color(107, 122, 141));
        g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        String noteText = "DEMONSTRATION PAGE // HIGH PERFORMANCE STREAMING";
        FontMetrics fmNote = g2d.getFontMetrics();
        g2d.drawString(noteText, (width - fmNote.stringWidth(noteText)) / 2, 600);

        // Footer info
        g2d.setColor(new Color(107, 122, 141));
        g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g2d.drawString("STATUS: DOWNLOADED & VERIFIED", 60, height - 70);
        g2d.drawString(String.format("FILE: %s", targetPath.getFileName()), width - 240, height - 70);

        g2d.dispose();

        try {
            ImageIO.write(image, "PNG", targetPath.toFile());
        } catch (IOException e) {
            log.error("Failed to write image {}: {}", targetPath, e.getMessage());
        }
    }
}
