package com.archive.scraper.engine;

import com.archive.scraper.model.ScraperTemplate;
import com.archive.scraper.model.dto.ScrapedChapter;
import com.archive.scraper.model.dto.ScrapedSeriesMetadata;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * CLASS: JsoupScraper (The Scout)
 * ============================================================================
 * WHAT IT DOES:
 * Connects directly over HTTP to media aggregator websites, parses their raw HTML
 * into an in-memory DOM (Document Object Model) tree, and uses CSS selectors defined
 * in ScraperTemplate to extract series metadata (title, author, synopsis, cover art)
 * and chapter listings.
 *
 * WHY IT IS USED:
 * For 80%+ of websites, HTML is delivered static or pre-rendered. Fetching via JSoup
 * uses ~20KB-200KB of network bandwidth and completes in 200-500ms, compared to Selenium
 * which requires 5-15 seconds and hundreds of megabytes of RAM.
 *
 * SYNTAX BREAKDOWN:
 * - @Component: Registers this class as a Spring-managed singleton bean so it can be
 *   injected into ScraperService via dependency injection.
 * - Jsoup.connect(url): Initiates an HTTP connection to the target server.
 * - Connection.userAgent(...): Spoofs the HTTP "User-Agent" header to identify as a standard browser.
 * - Connection.header("Referer", url): Adds HTTP Referer header to prevent 403 hotlinking rejections.
 * - Document.select(cssQuery): Queries the in-memory DOM using standard W3C CSS selector syntax.
 * - Element.absUrl("href"): Resolves relative paths (e.g. "/manga/chapter-1") to fully-qualified
 *   absolute URLs (e.g. "https://example.com/manga/chapter-1") based on the page's base URI.
 * - Pattern & Matcher: Java Regular Expression (regex) engine used to extract numeric chapter values.
 * ============================================================================
 */
@Component
public class JsoupScraper {

    private static final Logger log = LoggerFactory.getLogger(JsoupScraper.class);

    // Matches strings like "Chapter 104.5", "Ch. 12", "Episode 4" (case-insensitive)
    private static final Pattern CHAPTER_NUMBER_PATTERN =
            Pattern.compile("(?i)(?:ch(?:apter)?\\.?\\s*)(\\d+(?:\\.\\d+)?)");

    // Fallback: Extracts the first decimal or integer number found in the string
    private static final Pattern FALLBACK_NUMBER_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /**
     * Connects to a series URL, parses the HTML DOM, and returns extracted metadata.
     *
     * @param url      Target series web address.
     * @param template Recipe defining the CSS selectors for the domain.
     * @return ScrapedSeriesMetadata record containing title, author, description, and chapters.
     * @throws IOException If the network connection fails or the site returns HTTP error codes.
     */
    public ScrapedSeriesMetadata scrapeSeries(String url, ScraperTemplate template) throws IOException {
        log.info("Scouting series metadata from [{}] using template [{}]", url, template.getName());

        Document doc = connectAndFetch(url, template);

        // 1. Extract Series Title
        String title = extractText(doc, template.getTitleSelector(), "Unknown Title");

        // 2. Extract Author / Studio
        String author = null;
        if (template.getAuthorSelector() != null && !template.getAuthorSelector().isBlank()) {
            author = extractText(doc, template.getAuthorSelector(), null);
        }

        // 3. Extract Synopsis / Description
        String description = null;
        if (template.getDescriptionSelector() != null && !template.getDescriptionSelector().isBlank()) {
            description = extractText(doc, template.getDescriptionSelector(), null);
        }

        // 4. Extract Cover Thumbnail URL
        String coverUrl = null;
        if (template.getCoverImageSelector() != null && !template.getCoverImageSelector().isBlank()) {
            Element coverElem = doc.selectFirst(template.getCoverImageSelector());
            if (coverElem != null) {
                // Many sites use "data-src" for lazy loading, falling back to "src"
                coverUrl = coverElem.hasAttr("data-src") ? coverElem.absUrl("data-src") : coverElem.absUrl("src");
            }
        }

        // 5. Extract Adult / 18+ Flag
        boolean isAdult = false;
        if (template.getAdultSelector() != null && !template.getAdultSelector().isBlank()) {
            isAdult = doc.selectFirst(template.getAdultSelector()) != null;
        }

        // 6. Extract Chapter Listing
        List<ScrapedChapter> chapters = new ArrayList<>();
        Elements chapterLinks = doc.select(template.getChapterListSelector());

        for (Element link : chapterLinks) {
            String chapterUrl = link.absUrl("href");
            if (chapterUrl.isBlank()) {
                continue;
            }

            // Extract chapter title: check sub-selector or use raw link text
            String chapterRawText;
            if (template.getChapterTitleSelector() != null && !template.getChapterTitleSelector().isBlank()) {
                Element titleSubElem = link.selectFirst(template.getChapterTitleSelector());
                chapterRawText = (titleSubElem != null) ? titleSubElem.text() : link.text();
            } else {
                chapterRawText = link.text();
            }

            Double chapterNumber = parseChapterNumber(chapterRawText);
            chapters.add(new ScrapedChapter(chapterNumber, chapterRawText.trim(), chapterUrl));
        }

        log.info("Found [{}] chapters for series [{}]", chapters.size(), title);
        return new ScrapedSeriesMetadata(title, author, description, coverUrl, isAdult, chapters);
    }

    /**
     * Extracts page image URLs from a chapter reader page if images are rendered statically in HTML.
     *
     * @param chapterUrl          The web address of the chapter reader page.
     * @param template            The domain template containing the default image selector.
     * @param customImageSelector Optional local override selector on the Manga entity.
     * @return List of absolute image URLs in reading order.
     * @throws IOException If the connection fails.
     */
    public List<String> scrapeImageUrls(String chapterUrl, ScraperTemplate template, String customImageSelector) throws IOException {
        Document doc = connectAndFetch(chapterUrl, template);

        // Preference: Custom selector set on Manga takes precedence over global template
        String selector = (customImageSelector != null && !customImageSelector.isBlank())
                ? customImageSelector
                : template.getImageSelector();

        Elements imgElements = doc.select(selector);
        List<String> imageUrls = new ArrayList<>();

        for (Element img : imgElements) {
            String src = img.hasAttr("data-src") ? img.absUrl("data-src") : img.absUrl("src");
            if (src != null && !src.isBlank() && !imageUrls.contains(src)) {
                imageUrls.add(src);
            }
        }

        log.info("Extracted [{}] static image URLs from [{}]", imageUrls.size(), chapterUrl);
        return imageUrls;
    }

    /**
     * Internal helper to establish a polite, browser-mimicking HTTP connection via JSoup.
     */
    private Document connectAndFetch(String url, ScraperTemplate template) throws IOException {
        String userAgent = (template.getCustomUserAgent() != null && !template.getCustomUserAgent().isBlank())
                ? template.getCustomUserAgent()
                : DEFAULT_USER_AGENT;

        Connection conn = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(15_000)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", url)
                .followRedirects(true);

        return conn.get();
    }

    /**
     * Helper to safely extract and trim element text, returning a fallback if missing.
     */
    private String extractText(Document doc, String selector, String fallback) {
        if (selector == null || selector.isBlank()) return fallback;
        Element elem = doc.selectFirst(selector);
        return (elem != null && !elem.text().isBlank()) ? elem.text().trim() : fallback;
    }

    /**
     * Parses numeric chapter values from raw strings like "Chapter 104.5", "Ch. 5", "#22".
     */
    public static Double parseChapterNumber(String text) {
        if (text == null || text.isBlank()) return 0.0;

        Matcher m = CHAPTER_NUMBER_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }

        Matcher fallback = FALLBACK_NUMBER_PATTERN.matcher(text);
        if (fallback.find()) {
            try {
                return Double.parseDouble(fallback.group(1));
            } catch (NumberFormatException ignored) {}
        }

        return 0.0;
    }
}
