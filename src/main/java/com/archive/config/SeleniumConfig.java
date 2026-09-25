package com.archive.config;

import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================================
 * CLASS: SeleniumConfig
 * ============================================================================
 * WHAT IT DOES:
 * Configures the parameters, arguments, and stealth options for the headless
 * Chrome/Chromium browser instance utilized by Selenium WebDriver in The Archive.
 *
 * WHY IT IS USED:
 * Standard automated web drivers send identifiable browser signatures (such as
 * `navigator.webdriver = true` in JavaScript). Modern content distribution networks
 * (CDNs) like Cloudflare detect these flags and issue CAPTCHA challenges or HTTP 403 Forbidden.
 * This configuration applies stealth flags and headless execution options so automated
 * requests behave like standard user desktop sessions.
 *
 * SYNTAX BREAKDOWN:
 * - @Configuration: Informs the Spring IOC (Inversion of Control) container that this class
 *   declares one or more @Bean methods and may be processed by the Spring container to
 *   generate bean definitions and service requests at runtime.
 * - @Value("${archive.scraper.user-agent:...}"): SpEL (Spring Expression Language) annotation
 *   that injects a property value from application.properties / application.yml, falling back
 *   to a realistic desktop Chrome User-Agent if not specified.
 * - @Bean: Tells Spring that the return value of chromeOptions() should be registered
 *   as a bean in the application context and injected where needed (e.g. in SeleniumHarvester).
 * ============================================================================
 */
@Configuration
public class SeleniumConfig {

    @Value("${archive.scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36}")
    private String defaultUserAgent;

    /**
     * Builds and exposes a configured ChromeOptions bean with anti-detection flags.
     *
     * @return ChromeOptions configured for headless stealth execution.
     */
    @Bean
    public ChromeOptions chromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // Detect container-provided or custom Chromium binary path
        String envChromeBin = System.getenv("CHROME_BIN");
        if (envChromeBin != null && !envChromeBin.isBlank()) {
            options.setBinary(envChromeBin);
        }

        // Run in headless mode (no visual UI window appears on the desktop)
        // "--headless=new" invokes Chromium's modern headless architecture (Chrome 109+),
        // which runs the full Chromium engine rather than the legacy lightweight shell.
        options.addArguments("--headless=new");

        // Essential execution flags for stability across environments:
        // --disable-gpu: Avoids hardware acceleration crashes in headless environments
        // --no-sandbox: Bypasses OS security model constraints (safe for local-first desktop apps)
        // --disable-dev-shm-usage: Overcomes memory limitations in restricted environments
        // --window-size=1920,1080: Ensures responsive websites render desktop layouts rather than mobile
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        // Anti-bot detection flag: Disables Chromium's internal automation flag
        // (prevents navigator.webdriver from being set to true in JavaScript)
        options.addArguments("--disable-blink-features=AutomationControlled");

        // Sets a realistic Desktop Chrome User-Agent header
        options.addArguments("--user-agent=" + defaultUserAgent);

        return options;
    }
}
