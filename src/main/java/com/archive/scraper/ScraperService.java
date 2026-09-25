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

        if ("Unknown Title".equalsIgnoreCase(metadata.title()) && (metadata.chapters() == null || metadata.chapters().isEmpty())) {
            throw new IllegalStateException("Scout failed: Could not match title or chapters using current template selectors. Please refine selectors in the CSS Wizard first.");
        }

        // Deduplicate series: check if a manga with the same sourceUrl or title already exists
        Optional<Manga> existingOpt = mangaService.getAllManga().stream()
                .filter(m -> (m.getSourceUrl() != null && m.getSourceUrl().equalsIgnoreCase(seriesUrl))
                          || (metadata.title() != null && m.getTitle() != null && m.getTitle().equalsIgnoreCase(metadata.title())))
                .findFirst();

        Manga savedManga;
        if (existingOpt.isPresent()) {
            savedManga = existingOpt.get();
            if (metadata.author() != null && !metadata.author().isBlank()) savedManga.setAuthor(metadata.author());
            if (metadata.description() != null && !metadata.description().isBlank()) savedManga.setDescription(metadata.description());
            if (metadata.coverImageUrl() != null && !metadata.coverImageUrl().isBlank()) savedManga.setCoverImageUrl(metadata.coverImageUrl());
            savedManga.setSourceUrl(seriesUrl);
            savedManga = mangaService.createManga(savedManga);
            log.info("Found existing series [{}], updated metadata and checking new chapters", savedManga.getTitle());
        } else {
            Manga manga = new Manga();
            manga.setTitle(metadata.title());
            manga.setAuthor(metadata.author());
            manga.setDescription(metadata.description());
            manga.setCoverImageUrl(metadata.coverImageUrl());
            manga.setSourceUrl(seriesUrl);
            manga.setAdult(metadata.isAdult());
            savedManga = mangaService.createManga(manga);
        }

        // Convert DTO chapters to domain Chapter entities
        List<Chapter> chapters = new ArrayList<>();
        if (metadata.chapters() != null) {
            for (ScrapedChapter sc : metadata.chapters()) {
                Chapter chapter = new Chapter();
                chapter.setChapterNumber(sc.chapterNumber());
                chapter.setTitle(sc.title());
                chapter.setSourceUrl(sc.chapterUrl());
                chapter.setDownloaded(false);
                chapters.add(chapter);
            }
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
                .orElseGet(() -> templateRepository.findAll().stream()
                        .filter(t -> domain.contains(t.getDomainName()) || t.getDomainName().contains(domain))
                        .findFirst()
                        .orElseGet(() -> createDefaultTemplate(domain)));

        broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 10, "DOWNLOADING", 8);

        try {
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

            if (imageUrls.isEmpty()) {
                throw new IllegalStateException("No page images could be extracted for chapter [" + chapter.getSourceUrl() + "]. Please verify the image selector in your domain recipe.");
            }

            broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 40, "DOWNLOADING", 8);

            // Construct target directory path: {vaultBasePath}/manga/{mangaTitle}/Chapter_{chapterNumber}
            String sanitizedTitle = sanitizeFilename(manga.getTitle());
            Path chapterDir = Paths.get(vaultBasePath, "manga", sanitizedTitle, "Chapter_" + chapter.getChapterNumber());

            broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 75, "DOWNLOADING", 8);

            int downloadedCount = seleniumHarvester.downloadPagesConcurrently(
                    imageUrls,
                    chapterDir,
                    chapter.getSourceUrl(),
                    manga.getTitle(),
                    chapter.getChapterNumber());

            if (downloadedCount == 0 && !imageUrls.isEmpty()) {
                throw new IllegalStateException("Failed to stream image bytes from host CDN. Image URLs may be expired or access forbidden.");
            }

            broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 95, "PACKAGING_CBZ", 1);

            Chapter savedChapter = mangaService.markChapterDownloaded(chapterId, chapterDir.toAbsolutePath().toString(), downloadedCount);

            broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 100, "COMPLETED", 0);
            workspaceService.recordActivity("HARVESTER", "CHAPTER_HARVESTED",
                    "Harvested " + manga.getTitle() + " Chapter " + chapter.getChapterNumber() + " (" + downloadedCount + " pages)",
                    "MANGA", manga.getId());

            return savedChapter;
        } catch (Exception e) {
            broadcastProgress(chapterId, manga.getTitle(), chapter.getChapterNumber(), 0, "FAILED", 0);
            log.error("Harvesting failed for chapter [{}]: {}", chapterId, e.getMessage());
            throw e;
        }
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

        // Test Images (filtering out transparent spacers)
        List<String> sampleImages = new ArrayList<>();
        int imageCount = 0;
        if (imageSelector != null && !imageSelector.isBlank()) {
            Elements imgs = doc.select(imageSelector);
            for (Element img : imgs) {
                String src = JsoupScraper.resolveImageSrc(img);
                if (src != null) {
                    imageCount++;
                    if (!sampleImages.contains(src) && sampleImages.size() < 6) {
                        sampleImages.add(src);
                    }
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
     * Registers or updates a site recipe template in the database (Upsert).
     */
    public ScraperTemplate saveTemplate(ScraperTemplate template) {
        if (template.getDomainName() != null) {
            String domain = template.getDomainName().trim().toLowerCase();
            template.setDomainName(domain);
            Optional<ScraperTemplate> existingOpt = templateRepository.findByDomainName(domain);
            if (existingOpt.isPresent()) {
                ScraperTemplate existing = existingOpt.get();
                if (template.getName() != null && !template.getName().isBlank()) {
                    existing.setName(template.getName());
                }
                existing.setTitleSelector(template.getTitleSelector());
                existing.setChapterListSelector(template.getChapterListSelector());
                existing.setImageSelector(template.getImageSelector());
                if (template.getAuthorSelector() != null) existing.setAuthorSelector(template.getAuthorSelector());
                if (template.getDescriptionSelector() != null) existing.setDescriptionSelector(template.getDescriptionSelector());
                if (template.getCoverImageSelector() != null) existing.setCoverImageSelector(template.getCoverImageSelector());
                if (template.getChapterTitleSelector() != null) existing.setChapterTitleSelector(template.getChapterTitleSelector());
                existing.setRequiresJs(template.isRequiresJs());
                if (template.getRateLimitMs() > 0) existing.setRateLimitMs(template.getRateLimitMs());
                return templateRepository.save(existing);
            }
        }
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
        // Strip external scripts to prevent third-party ads, frame-busters, tracking, and SPA hydration crashes
        doc.select("script").remove();

        // Remove any frame-busting scripts or restrictive meta tags
        doc.select("meta[http-equiv=Content-Security-Policy]").remove();
        doc.select("meta[http-equiv=X-Frame-Options]").remove();

        // Inject the APEX Live Inspector Script & Styling
        String inspectorScript = """
            <style id="apex-inspector-styles">
                * {
                    cursor: default !important;
                }
                .apex-highlight-hover {
                    outline: 2px solid #3898ec !important;
                    outline-offset: 2px !important;
                    box-shadow: 0 0 10px rgba(56, 152, 236, 0.7) !important;
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

                    function isValidCssIdent(s) {
                        return typeof s === 'string' && /^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(s) && !s.startsWith('apex-') && s.length < 30;
                    }

                    function safeQuery(sel) {
                        if (!sel) return [];
                        try {
                            return document.querySelectorAll(sel);
                        } catch (e) {
                            return [];
                        }
                    }

                    function computeCandidateSelectors(el) {
                        if (!el || el === document.body || el === document.documentElement) return ['body'];
                        const candidates = [];
                        const tag = el.tagName.toLowerCase();

                        // 1. Semantic uniqueness: If <h1> is unique on the page, it's the gold standard for title
                        if (tag === 'h1' && safeQuery('h1').length === 1) {
                            candidates.push('h1');
                        }

                        // 2. ID uniqueness (if clean and not autogenerated numbers)
                        if (el.id && isValidCssIdent(el.id) && !el.id.match(/\\d{4,}/) && safeQuery('#' + el.id).length === 1) {
                            candidates.push('#' + el.id);
                        }

                        // 3. Clean CSS class combinations (strictly alphanumeric + hyphens)
                        let validClasses = [];
                        if (el.className && typeof el.className === 'string') {
                            validClasses = el.className.trim().split(/\\s+/).filter(isValidCssIdent);
                        }

                        for (const c of validClasses.slice(0, 3)) {
                            const sel = tag + '.' + c;
                            if (safeQuery(sel).length > 0 && !candidates.includes(sel)) {
                                candidates.push(sel);
                            }
                        }

                        if (validClasses.length >= 2) {
                            const sel2 = tag + '.' + validClasses[0] + '.' + validClasses[1];
                            if (safeQuery(sel2).length > 0 && !candidates.includes(sel2)) {
                                candidates.push(sel2);
                            }
                        }

                        if (!candidates.includes(tag)) {
                            candidates.push(tag);
                        }

                        // 4. Hierarchical parent context for lists (chapters / images)
                        if (el.parentElement && el.parentElement !== document.body && el.parentElement !== document.documentElement) {
                            const p = el.parentElement;
                            let pSel = '';
                            if (p.id && isValidCssIdent(p.id) && !p.id.match(/\\d{4,}/)) {
                                pSel = '#' + p.id;
                            } else if (p.className && typeof p.className === 'string') {
                                const pClasses = p.className.trim().split(/\\s+/).filter(isValidCssIdent);
                                if (pClasses.length > 0) pSel = p.tagName.toLowerCase() + '.' + pClasses[0];
                            }
                            if (pSel) {
                                const combined = pSel + ' ' + tag;
                                if (safeQuery(combined).length > 0 && !candidates.includes(combined)) {
                                    candidates.push(combined);
                                }
                            }
                        }

                        return candidates.length > 0 ? candidates : [tag];
                    }

                    document.addEventListener('mouseover', function(e) {
                        const target = e.target;
                        if (!target || target === badge || target.id === 'apex-selector-badge') return;

                        if (hoveredEl && hoveredEl !== target) {
                            hoveredEl.classList.remove('apex-highlight-hover');
                        }

                        hoveredEl = target;
                        hoveredEl.classList.add('apex-highlight-hover');

                        const candidates = computeCandidateSelectors(hoveredEl);
                        const primarySelector = candidates[0] || hoveredEl.tagName.toLowerCase();
                        const matchCount = safeQuery(primarySelector).length;
                        badge.textContent = primarySelector + ' [' + matchCount + ' matches]';

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
                        const target = hoveredEl || e.target;
                        if (!target) return;

                        const candidates = computeCandidateSelectors(target);
                        const primarySelector = candidates[0] || target.tagName.toLowerCase();
                        const matchCount = safeQuery(primarySelector).length;
                        const tag = target.tagName.toLowerCase();
                        const isImg = tag === 'img' || target.querySelector('img') !== null;
                        const isLink = tag === 'a' || target.closest('a') !== null;
                        const text = (target.innerText || '').trim().slice(0, 80);

                        window.parent.postMessage({
                            type: 'APEX_INSPECTOR_ELEMENT_SELECTED',
                            selector: primarySelector,
                            candidates: candidates,
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

    /**
     * Generates a safe fallback ScraperTemplate for unknown or dynamic domains
     * equipped with standard manga reader CSS selectors.
     */
    private ScraperTemplate createDefaultTemplate(String domain) {
        log.info("No saved recipe for domain [{}], creating dynamic fallback template with standard selectors", domain);
        ScraperTemplate t = new ScraperTemplate();
        t.setName(domain);
        t.setDomainName(domain);
        t.setTitleSelector("h1.text-xl, h1, .entry-title, .series-title");
        t.setChapterListSelector("div.pl-4 a, #chapterlist a, a[href*='/chapter/'], a[href*='/chapter-']");
        t.setImageSelector("div[data-page] img, img[data-page-index], div.w-full img, #readerarea img, div#readerarea img");
        t.setCoverImageSelector("img[alt='poster'], div.thumb img, img.wp-post-image");
        t.setRequiresJs(false);
        t.setRateLimitMs(1000);
        return t;
    }
}
