package com.archive.scraper.engine;

import com.archive.scraper.model.ScraperTemplate;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ============================================================================
 * CLASS: SeleniumHarvester (The Harvester)
 * ============================================================================
 * WHAT IT DOES:
 * Automates headless Chromium browser instances for dynamic, JavaScript-rendered
 * chapter pages (e.g. single-page applications or infinite-scrollers) and performs
 * parallel media downloading using Java 21 Virtual Threads.
 *
 * WHY IT IS USED:
 * 1. JavaScript Rendering: Many modern manga readers load image DOM nodes asynchronously
 *    via AJAX or render images into HTML5 canvas tags after scrolling. Static scrapers
 *    (JSoup) see only an empty container. Selenium launches a headless browser that
 *    executes the site's JavaScript, scrolls the viewport, and extracts populated image URLs.
 * 2. Java 21 Virtual Threads: Traditional OS threads consume ~1MB of stack memory each.
 *    Spawning dozens of platform threads to download images blocks kernel threads on I/O.
 *    Virtual Threads (Project Loom) are lightweight user-space threads multiplexed onto
 *    a small pool of carrier threads. They make blocking I/O (streaming image bytes from
 *    a remote web server to local disk) virtually free in terms of CPU and RAM overhead.
 *
 * SYNTAX BREAKDOWN:
 * - ChromeDriver: The Selenium WebDriver implementation for controlling Chromium/Chrome.
 * - WebDriverWait & ExpectedConditions: Explicit wait mechanism. Instead of dangerous
 *   Thread.sleep(), it polls the browser DOM until an expected condition occurs (e.g.
 *   element presence) or a timeout threshold is exceeded.
 * - JavascriptExecutor: An interface enabling Java code to execute raw JavaScript directly
 *   inside the browser's JavaScript engine (e.g. window.scrollTo).
 * - Executors.newVirtualThreadPerTaskExecutor(): Factory method introduced in Java 21
 *   that returns an ExecutorService where every submitted Runnable/Callable executes on a
 *   distinct virtual thread.
 * - try (ExecutorService executor = ...): Java 7+ try-with-resources statement. In Java 19+,
 *   ExecutorService implements AutoCloseable, automatically awaiting termination of all
 *   spawned virtual threads before the block exits.
 * ============================================================================
 */
@Component
public class SeleniumHarvester {

    private static final Logger log = LoggerFactory.getLogger(SeleniumHarvester.class);
    private final ChromeOptions chromeOptions;

    public SeleniumHarvester(ChromeOptions chromeOptions) {
        this.chromeOptions = chromeOptions;
    }

    /**
     * Navigates to a dynamic chapter reader page, executes scrolling to trigger lazy loading,
     * and extracts all image URLs matching the selector.
     *
     * @param chapterUrl          The web address of the chapter.
     * @param template            The domain template containing the default image selector.
     * @param customImageSelector Optional local override selector on the Manga entity.
     * @return List of extracted image URLs in reading order.
     */
    public List<String> extractDynamicImageUrls(String chapterUrl, ScraperTemplate template, String customImageSelector) {
        String selector = (customImageSelector != null && !customImageSelector.isBlank())
                ? customImageSelector
                : template.getImageSelector();

        log.info("Launching headless browser to extract dynamic images from [{}] using selector [{}]", chapterUrl, selector);

        WebDriver driver = new ChromeDriver(chromeOptions);
        try {
            driver.get(chapterUrl);

            // Explicit wait: Wait up to 15 seconds for at least one image element to appear
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));

            // Scroll down gradually to trigger lazy-loaded images that load only when near the viewport
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollTo(0, document.body.scrollHeight / 2);");
            Thread.sleep(800);
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(800);

            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            List<String> imageUrls = new ArrayList<>();

            for (WebElement elem : elements) {
                // Check data-src first (lazy loading attribute), fallback to src
                String src = elem.getAttribute("data-src");
                if (src == null || src.isBlank()) {
                    src = elem.getAttribute("src");
                }
                if (src != null && !src.isBlank() && !imageUrls.contains(src)) {
                    imageUrls.add(src);
                }
            }

            log.info("Extracted [{}] dynamic image URLs from [{}]", imageUrls.size(), chapterUrl);
            return imageUrls;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Scraping execution interrupted", ie);
        } finally {
            // CRITICAL: Always quit the driver to terminate the external chromedriver.exe and
            // chrome.exe child processes, preventing zombie process memory leaks.
            driver.quit();
        }
    }

    /**
     * Downloads an entire list of image URLs concurrently to a local directory
     * using Java 21 Virtual Threads.
     *
     * @param imageUrls List of remote web image URLs.
     * @param targetDir Local filesystem directory to write files into.
     * @return Number of successfully downloaded pages.
     */
    public int downloadPagesConcurrently(List<String> imageUrls, Path targetDir) throws Exception {
        Files.createDirectories(targetDir);

        // Java 21: Virtual-thread-per-task executor
        // Every download runs on its own virtual thread without consuming OS platform threads
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < imageUrls.size(); i++) {
                final int pageIndex = i + 1;
                final String imageUrl = imageUrls.get(i);

                // Format filename as padded 3-digit number (e.g., 001.jpg, 002.jpg)
                String extension = getFileExtension(imageUrl);
                Path destination = targetDir.resolve(String.format("%03d%s", pageIndex, extension));

                // Submit each download task to a virtual thread
                futures.add(executor.submit(() -> downloadSingleImage(imageUrl, destination)));
            }

            // Wait for all virtual thread download tasks to complete
            int successCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successCount++;
                }
            }

            log.info("Successfully harvested [{}/{}] pages into [{}]", successCount, imageUrls.size(), targetDir);
            return successCount;
        }
    }

    /**
     * Streams raw bytes from a remote URL directly into a local file.
     *
     * @param urlString  Remote image web address.
     * @param targetPath Local destination path.
     * @return true if download succeeded, false otherwise.
     */
    private boolean downloadSingleImage(String urlString, Path targetPath) {
        try (InputStream in = URI.create(urlString).toURL().openStream()) {
            // StandardCopyOption.REPLACE_EXISTING ensures partial/failed downloads are overwritten cleanly
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.error("Failed to download image from [{}]: {}", urlString, e.getMessage());
            return false;
        }
    }

    /**
     * Determines appropriate file extension from URL, defaulting to .jpg.
     */
    private String getFileExtension(String url) {
        String cleanUrl = url.split("\\?")[0].toLowerCase();
        if (cleanUrl.endsWith(".png")) return ".png";
        if (cleanUrl.endsWith(".webp")) return ".webp";
        if (cleanUrl.endsWith(".gif")) return ".gif";
        return ".jpg";
    }
}
