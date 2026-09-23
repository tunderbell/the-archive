package com.archive.scraper.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TEST: JsoupScraperTest
 * ============================================================================
 * WHAT IT DOES:
 * Tests the chapter regex number extraction and HTML DOM parsing logic.
 *
 * WHY IT IS USED:
 * Verifies that irregular and decimal chapter numbers (e.g. 104.5, 12, 0.5)
 * are accurately converted to Double values for proper ordering.
 * ============================================================================
 */
class JsoupScraperTest {

    @ParameterizedTest(name = "Text: \"{0}\" -> Expected Chapter Number: {1}")
    @CsvSource({
            "'Chapter 104.5 - Epilogue', 104.5",
            "'Chapter 100', 100.0",
            "'Ch. 12', 12.0",
            "'ch 4.2', 4.2",
            "'#85', 85.0",
            "'Episode 24', 24.0",
            "'Bonus Issue', 0.0"
    })
    @DisplayName("Should accurately parse integer and decimal chapter numbers")
    void shouldParseChapterNumbers(String rawText, Double expectedNumber) {
        Double parsed = JsoupScraper.parseChapterNumber(rawText);
        assertThat(parsed).isEqualTo(expectedNumber);
    }
}
