package com.archive.core.service;

import com.archive.core.model.CustomCommand;
import com.archive.core.model.CustomCommandRepository;
import com.archive.core.util.CommandParser;
import com.archive.domain.manga.MangaService;
import com.archive.domain.manga.chapter.ChapterRepository;
import com.archive.scraper.ScraperService;
import com.archive.workspace.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * TEST: CommandExecutionServiceTest
 * ============================================================================
 * WHAT IT DOES:
 * Tests the execution router, Tier 1 alias resolution, Tier 2 pipeline chaining,
 * parameter expansion, and recursion limit protection.
 * ============================================================================
 */
@ExtendWith(MockitoExtension.class)
class CommandExecutionServiceTest {

    @Mock private CustomCommandRepository customCommandRepository;
    @Mock private ScraperService scraperService;
    @Mock private MangaService mangaService;
    @Mock private ChapterRepository chapterRepository;
    @Mock private WorkspaceService workspaceService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private CommandExecutionService executionService;

    @BeforeEach
    void setUp() {
        CommandParser parser = new CommandParser();
        executionService = new CommandExecutionService(
                parser,
                customCommandRepository,
                scraperService,
                mangaService,
                chapterRepository,
                workspaceService,
                messagingTemplate
        );
    }

    @Test
    @DisplayName("Should expand Tier 1 alias and execute target action")
    void shouldExpandCustomAlias() {
        CustomCommand alias = new CustomCommand("sys-check", "status", "Check status", false);
        when(customCommandRepository.findByTriggerName("sys-check")).thenReturn(Optional.of(alias));

        String result = executionService.execute("sys-check");

        assertThat(result).contains("THE ARCHIVE APEX STATUS");
    }

    @Test
    @DisplayName("Should execute Tier 2 multi-step pipeline chained with &&")
    void shouldExecutePipeline() {
        String result = executionService.execute("status && help");

        assertThat(result).contains("=== EXECUTING PIPELINE (2 steps) ===");
        assertThat(result).contains("[1/2] > status");
        assertThat(result).contains("[2/2] > help");
    }

    @Test
    @DisplayName("Should abort execution when circular alias expansion is detected")
    void shouldDetectCircularRecursion() {
        CustomCommand loopAlias = new CustomCommand("loop", "loop", "Infinite loop", false);
        when(customCommandRepository.findByTriggerName("loop")).thenReturn(Optional.of(loopAlias));

        String result = executionService.execute("loop");

        assertThat(result).contains("ERROR: Circular alias expansion limit exceeded (> 5)");
    }
}
