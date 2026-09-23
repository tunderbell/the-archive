package com.archive.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TEST: CommandParserTest
 * ============================================================================
 * WHAT IT DOES:
 * Unit tests verifying that raw user CLI strings are accurately tokenized into
 * actions and key-value flag maps.
 *
 * WHY IT IS USED:
 * Ensures the APEX command bar never misinterprets multi-word titles in quotes
 * or drops boolean flags.
 * ============================================================================
 */
class CommandParserTest {

    private CommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new CommandParser();
    }

    @Test
    @DisplayName("Should parse action and quoted multi-word flag values")
    void shouldParseQuotedFlags() {
        String input = "add --title \"Solo Leveling\" --author 'Chugong' --status ONGOING";
        Command cmd = parser.parse(input);

        assertThat(cmd.getAction()).isEqualTo("add");
        assertThat(cmd.getFlag("title")).isEqualTo("Solo Leveling");
        assertThat(cmd.getFlag("author")).isEqualTo("Chugong");
        assertThat(cmd.getFlag("status")).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("Should parse boolean switch flags without values as 'true'")
    void shouldParseBooleanFlags() {
        String input = "scrape --url https://example.com/manga --adult";
        Command cmd = parser.parse(input);

        assertThat(cmd.getAction()).isEqualTo("scrape");
        assertThat(cmd.getFlag("url")).isEqualTo("https://example.com/manga");
        assertThat(cmd.hasFlag("adult")).isTrue();
        assertThat(cmd.getFlag("adult")).isEqualTo("true");
    }

    @Test
    @DisplayName("Should return help action when input is empty or null")
    void shouldReturnHelpOnEmpty() {
        assertThat(parser.parse(null).getAction()).isEqualTo("help");
        assertThat(parser.parse("   ").getAction()).isEqualTo("help");
    }
}
