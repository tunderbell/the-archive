package com.archive.scraper;

import com.archive.domain.manga.Manga;
import com.archive.domain.manga.MangaService;
import com.archive.domain.manga.chapter.Chapter;
import com.archive.domain.manga.chapter.ChapterRepository;
import com.archive.scraper.engine.JsoupScraper;
import com.archive.scraper.engine.SeleniumHarvester;
import com.archive.scraper.model.ScraperTemplate;
import com.archive.scraper.model.ScraperTemplateRepository;
import com.archive.scraper.model.dto.ScrapedChapter;
import com.archive.scraper.model.dto.ScrapedSeriesMetadata;
import com.archive.workspace.service.WorkspaceService;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================================
 * CLASS: ScraperService
 * ============================================================================
 * WHAT IT DOES:
 * Serves as the central coordinator and orchestrator for Subsystem 1 (The Harvesting
 * and Web Scraping Engine). It receives ingestion requests, maps external URLs to their
 * corresponding domain ScraperTemplate, invokes JsoupScraper for fast metadata scouting,
 * registers newly discovered chapters with MangaService, and initiates parallel image
 * harvesting via SeleniumHarvester.
 *
 * WHY IT IS USED:
 * Separates high-level business orchestration (persisting series, creating chapter directories,
 * tracking download progression) from low-level web scraping protocols (HTTP parsing,
 * browser automation, and virtual thread concurrency).
 *
 * SYNTAX BREAKDOWN:
 * - @Service: Specialization of @Component, marking this class as a business service bean
 *   in Spring's component scanning.
 * - @Transactional: Ensures database mutations (saving Manga, updating Chapter statuses)
 *   are wrapped in database transactions that commit on success or roll back on unchecked exceptions.
 * - @Value("${archive.storage.vault-path:...}"): Injects the configured local vault storage directory.
 * ============================================================================
 */
@Service
@Transactional
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    private final ScraperTemplateRepository templateRepository;
    private final JsoupScraper jsoupScraper;
    private final SeleniumHarvester seleniumHarvester;
    private final MangaService mangaService;
    private final ChapterRepository chapterRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WorkspaceService workspaceService;

    @Value("${archive.storage.vault-path:./archive_vault}")
    private String vaultBasePath;

    public ScraperService(
            ScraperTemplateRepository templateRepository,
            JsoupScraper jsoupScraper,
            SeleniumHarvester seleniumHarvester,
            MangaService mangaService,
            ChapterRepository chapterRepository,
            SimpMessagingTemplate messagingTemplate,
            WorkspaceService workspaceService) {
        this.templateRepository = templateRepository;
        this.jsoupScraper = jsoupScraper;
        this.seleniumHarvester = seleniumHarvester;
        this.mangaService = mangaService;
        this.chapterRepository = chapterRepository;
        this.messagingTemplate = messagingTemplate;
        this.workspaceService = workspaceService;
    }

    /**
     * Inspects a series URL, identifies its registered domain recipe, extracts metadata
     * and chapter catalog, and saves the series and chapters into The Archive.
     *
     * @param seriesUrl Web address of the series catalog page.
     * @return The persisted Manga entity.
     * @throws Exception If network errors occur or no template is registered for the domain.
     */
    public Manga scoutAndRegisterSeries(String seriesUrl) throws Exception {
        String domain = extractDomain(seriesUrl);
        ScraperTemplate template = templateRepository.findByDomainName(domain)
                .orElseThrow(() -> new IllegalArgumentException("No scraper template found for domain: " + domain));

        ScrapedSeriesMetadata metadata = jsoupScraper.scrapeSeries(seriesUrl, template);

        // Map DTO to Manga entity
        Manga manga = new Manga();
        manga.setTitle(metadata.title());
        manga.setAuthor(metadata.author());
        manga.setDescription(metadata.description());
        manga.setCoverImageUrl(metadata.coverImageUrl());
        manga.setSourceUrl(seriesUrl);
        manga.setAdult(metadata.isAdult());

        Manga savedManga = mangaService.createManga(manga);

        // Convert DTO chapters to domain Chapter entities
        List<Chapter> chapters = new ArrayList<>();
        for (ScrapedChapter sc : metadata.chapters()) {
            Chapter chapter = new Chapter();
            chapter.setChapterNumber(sc.chapterNumber());
            chapter.setTitle(sc.title());
            chapter.setSourceUrl(sc.chapterUrl());
            chapter.setDownloaded(false);
            chapters.add(chapter);
        }

        // Delegate deduplication and saving to MangaService
        mangaService.addDiscoveredChapters(savedManga.getId(), chapters);
        log.info("Successfully registered series [{}] with [{}] discovered chapters", savedManga.getTitle(), chapters.size());
        return savedManga;
    }

    /**
     * Harvests an individual chapter's page images and downloads them into the local vault storage.
     *
     * @param chapterId UUID of the Chapter entity to harvest.
     * @return The updated Chapter entity marked as downloaded.
     * @throws Exception If navigation, network, or disk write operations fail.
     */
    public Chapter harvestChapter(UUID chapterId) throws Exception {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found with ID: " + chapterId));

        Manga manga = chapter.getManga();
        String domain = extractDomain(chapter.getSourceUrl());
        ScraperTemplate template = templateRepository.findByDomainName(domain)
                .orElseThrow(() -> new IllegalArgumentException("No scraper template found for domain: " + domain));

        broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 10, "DOWNLOADING", 8);

        // Determine if images can be scraped via fast Jsoup or need dynamic Selenium
        List<String> imageUrls;
        if (!template.isRequiresJs()) {
            imageUrls = jsoupScraper.scrapeImageUrls(chapter.getSourceUrl(), template, manga.getCustomImageSelector());
        } else {
            imageUrls = seleniumHarvester.extractDynamicImageUrls(chapter.getSourceUrl(), template, manga.getCustomImageSelector());
        }

        // Fallback: If Jsoup yielded 0 images, attempt dynamic Selenium
        if (imageUrls.isEmpty()) {
            log.warn("Jsoup found 0 images for chapter [{}], falling back to Selenium Harvester", chapter.getSourceUrl());
            imageUrls = seleniumHarvester.extractDynamicImageUrls(chapter.getSourceUrl(), template, manga.getCustomImageSelector());
        }

        broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 40, "DOWNLOADING", 8);

        // Construct target directory path: {vaultBasePath}/manga/{mangaTitle}/Chapter_{chapterNumber}
        String sanitizedTitle = sanitizeFilename(manga.getTitle());
        Path chapterDir = Paths.get(vaultBasePath, "manga", sanitizedTitle, "Chapter_" + chapter.getChapterNumber());

        broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 75, "DOWNLOADING", 8);

        int downloadedCount = seleniumHarvester.downloadPagesConcurrently(imageUrls, chapterDir);

        broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 95, "PACKAGING_CBZ", 1);

        Chapter savedChapter = mangaService.markChapterDownloaded(chapterId, chapterDir.toAbsolutePath().toString(), downloadedCount);

        broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 100, "COMPLETED", 0);
        workspaceService.recordActivity("HARVESTER", "CHAPTER_HARVESTED",
                "Harvested " + manga.getTitle() + " Chapter " + chapter.getChapterNumber() + " (" + downloadedCount + " pages)",
                "MANGA", manga.getId());

        return savedChapter;
    }

    /**
     * Executes arbitrary CSS selectors on a target URL to preview matches in the Wizard.
     */
    public Map<String, Object> testSelectors(
            String url,
            String titleSelector,
            String chapterListSelector,
            String imageSelector,
            boolean requiresJs) throws Exception {

        String domain = extractDomain(url);
        Document doc = jsoupScraper.fetchDocument(url);

        // Test Title
        String matchedTitle = "None found";
        if (titleSelector != null && !titleSelector.isBlank()) {
            Element tElem = doc.selectFirst(titleSelector);
            if (tElem != null) matchedTitle = tElem.text().trim();
        }

        // Test Chapter List
        List<Map<String, String>> sampleChapters = new ArrayList<>();
        int chapterCount = 0;
        if (chapterListSelector != null && !chapterListSelector.isBlank()) {
            Elements chLinks = doc.select(chapterListSelector);
            chapterCount = chLinks.size();
            for (int i = 0; i < Math.min(chLinks.size(), 5); i++) {
                Element link = chLinks.get(i);
                sampleChapters.add(Map.of(
                        "title", link.text().trim(),
                        "url", link.absUrl("href")
                ));
            }
        }

        // Test Images
        List<String> sampleImages = new ArrayList<>();
        int imageCount = 0;
        if (imageSelector != null && !imageSelector.isBlank()) {
            Elements imgs = doc.select(imageSelector);
            imageCount = imgs.size();
            for (Element img : imgs) {
                String src = img.hasAttr("data-src") ? img.absUrl("data-src") : img.absUrl("src");
                if (src != null && !src.isBlank() && !sampleImages.contains(src)) {
                    sampleImages.add(src);
                    if (sampleImages.size() >= 6) break;
                }
            }
        }

        return Map.of(
                "domain", domain,
                "url", url,
                "title", matchedTitle,
                "chapterCount", chapterCount,
                "sampleChapters", sampleChapters,
                "imageCount", imageCount,
                "sampleImages", sampleImages
        );
    }

    private void broadcastProgress(UUID chapterId, String title, Double chNum, int percent, String status, int threads) {
        if (messagingTemplate != null) {
            Map<String, Object> payload = Map.of(
                    "jobId", "JOB-" + chapterId.toString().substring(0, 8).toUpperCase(),
                    "chapterId", chapterId.toString(),
                    "chapterNumber", chNum,
                    "title", title + " Ch " + chNum,
                    "progress", percent,
                    "status", status,
                    "threads", threads
            );
            messagingTemplate.convertAndSend("/topic/scraper.progress", (Object) payload);
        }
    }

    // --- Template Management Operations ---

    /**
     * Registers or updates a site recipe template in the database.
     */
    public ScraperTemplate saveTemplate(ScraperTemplate template) {
        return templateRepository.save(template);
    }

    /**
     * Retrieves all registered site templates.
     */
    @Transactional(readOnly = true)
    public List<ScraperTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    /**
     * Looks up a template by domain name.
     */
    @Transactional(readOnly = true)
    public Optional<ScraperTemplate> getTemplateByDomain(String domain) {
        return templateRepository.findByDomainName(domain);
    }

    /**
     * Extracts the naked host domain from a web URL (e.g. "https://www.asurascans.com/page" -> "asurascans.com").
     */
    public static String extractDomain(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Strips illegal filesystem characters from titles when generating local folder names.
     */
    private String sanitizeFilename(String name) {
        return (name == null) ? "untitled" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /**
     * Proxies a target webpage HTML, transforms all relative assets to absolute URLs,
     * strips restrictive frame headers/meta tags, and injects the APEX Visual Inspector script.
     */
    public String generateLiveInspectHtml(String url) throws Exception {
        Document doc = jsoupScraper.fetchDocument(url);

        // Convert relative URLs to absolute so images, CSS, and fonts load correctly inside the proxy
        for (Element e : doc.select("a[href]")) {
            e.attr("href", e.attr("abs:href"));
        }
        for (Element e : doc.select("img[src]")) {
            e.attr("src", e.attr("abs:src"));
        }
        for (Element e : doc.select("img[data-src]")) {
            String abs = e.attr("abs:data-src");
            e.attr("data-src", abs);
            if (!e.hasAttr("src") || e.attr("src").isEmpty()) {
                e.attr("src", abs);
            }
        }
        for (Element e : doc.select("link[href]")) {
            e.attr("href", e.attr("abs:href"));
        }
        for (Element e : doc.select("script[src]")) {
            e.attr("src", e.attr("abs:src"));
        }

        // Remove any frame-busting scripts or restrictive meta tags
        doc.select("meta[http-equiv=Content-Security-Policy]").remove();
        doc.select("meta[http-equiv=X-Frame-Options]").remove();

        // Inject the APEX Live Inspector Script & Styling
        String inspectorScript = """
            <style id="apex-inspector-styles">
                .apex-highlight-hover {
                    outline: 2px solid #3898ec !important;
                    outline-offset: 2px !important;
                    box-shadow: 0 0 10px rgba(56, 152, 236, 0.6) !important;
                    cursor: crosshair !important;
                }
                #apex-selector-badge {
                    position: fixed;
                    background: #11161d;
                    color: #3898ec;
                    border: 1px solid #3898ec;
                    font-family: monospace;
                    font-size: 11px;
                    font-weight: bold;
                    padding: 3px 8px;
                    z-index: 2147483647;
                    pointer-events: none;
                    display: none;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.8);
                }
            </style>
            <div id="apex-selector-badge"></div>
            <script id="apex-inspector-script">
                (function() {
                    let hoveredEl = null;
                    const badge = document.getElementById('apex-selector-badge');

                    function computeCleanSelector(el) {
                        if (!el || el === document.body || el === document.documentElement) return 'body';
                        if (el.id && !el.id.match(/\\d{4,}/)) return '#' + el.id;

                        let tag = el.tagName.toLowerCase();
                        if (el.className && typeof el.className === 'string') {
                            const classes = el.className.trim().split(/\\s+/)
                                .filter(c => c && !c.includes(':') && !c.startsWith('apex-') && c.length < 35);
                            if (classes.length > 0) {
                                tag += '.' + classes.slice(0, 2).join('.');
                            }
                        }

                        if (el.parentElement && el.parentElement !== document.body) {
                            let parentTag = el.parentElement.tagName.toLowerCase();
                            if (el.parentElement.id && !el.parentElement.id.match(/\\d{4,}/)) {
                                return '#' + el.parentElement.id + ' ' + tag;
                            }
                            if (el.parentElement.className && typeof el.parentElement.className === 'string') {
                                const pClasses = el.parentElement.className.trim().split(/\\s+/)
                                    .filter(c => c && !c.includes(':') && !c.startsWith('apex-') && c.length < 25);
                                if (pClasses.length > 0) {
                                    parentTag += '.' + pClasses[0];
                                }
                            }
                            return parentTag + ' ' + tag;
                        }
                        return tag;
                    }

                    document.addEventListener('mouseover', function(e) {
                        if (hoveredEl) {
                            hoveredEl.classList.remove('apex-highlight-hover');
                        }
                        const target = e.target;
                        if (!target || target === badge || target.id === 'apex-selector-badge') return;

                        hoveredEl = target;
                        hoveredEl.classList.add('apex-highlight-hover');

                        const selector = computeCleanSelector(hoveredEl);
                        const matchCount = document.querySelectorAll(selector).length;
                        badge.textContent = selector + ' [' + matchCount + ' matches]';

                        const rect = hoveredEl.getBoundingClientRect();
                        badge.style.top = Math.max(5, rect.top - 26) + 'px';
                        badge.style.left = Math.max(5, rect.left) + 'px';
                        badge.style.display = 'block';
                    }, true);

                    document.addEventListener('mouseout', function(e) {
                        if (hoveredEl) {
                            hoveredEl.classList.remove('apex-highlight-hover');
                        }
                        badge.style.display = 'none';
                    }, true);

                    document.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (!hoveredEl) return;

                        const selector = computeCleanSelector(hoveredEl);
                        const matchCount = document.querySelectorAll(selector).length;
                        const tag = hoveredEl.tagName.toLowerCase();
                        const isImg = tag === 'img' || hoveredEl.querySelector('img') !== null;
                        const isLink = tag === 'a' || hoveredEl.closest('a') !== null;
                        const text = (hoveredEl.innerText || '').trim().slice(0, 80);

                        window.parent.postMessage({
                            type: 'APEX_INSPECTOR_ELEMENT_SELECTED',
                            selector: selector,
                            tagName: tag,
                            matchCount: matchCount,
                            isImage: isImg,
                            isLink: isLink,
                            sampleText: text
                        }, '*');
                    }, true);
                })();
            </script>
            """;

        if (doc.body() != null) {
            doc.body().append(inspectorScript);
        } else {
            doc.append(inspectorScript);
        }

        return doc.outerHtml();
    }
}
