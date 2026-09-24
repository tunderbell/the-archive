package com.archive.core.service;

import com.archive.core.model.CustomCommand;
import com.archive.core.model.CustomCommandRepository;
import com.archive.core.util.Command;
import com.archive.core.util.CommandParser;
import com.archive.domain.manga.Manga;
import com.archive.domain.manga.MangaService;
import com.archive.domain.manga.chapter.Chapter;
import com.archive.domain.manga.chapter.ChapterRepository;
import com.archive.scraper.ScraperService;
import com.archive.workspace.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * ============================================================================
 * CLASS: CommandExecutionService
 * ============================================================================
 * WHAT IT DOES:
 * Dispatches, parses, expands, and executes built-in commands and user-defined
 * custom aliases (Tier 1) and multi-step pipelines (Tier 2).
 *
 * WHY IT IS USED:
 * Provides the execution backbone for the APEX Command Bar and the Xterm.js
 * browser terminal. Supports:
 * 1. Shorthand aliases (e.g., SCRP -> scrape, SYS -> status).
 * 2. User-defined aliases (e.g., alias sl="scrape --url ...").
 * 3. Chained multi-command pipelines using '&&' (Tier 2).
 * 4. Parameter substitution ($1, $2) in custom templates.
 * 5. Recursion guards preventing circular alias loops.
 * ============================================================================
 */
@Service
@Transactional
public class CommandExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutionService.class);
    private static final int MAX_RECURSION_DEPTH = 5;

    private final CommandParser parser;
    private final CustomCommandRepository customCommandRepository;
    private final ScraperService scraperService;
    private final MangaService mangaService;
    private final ChapterRepository chapterRepository;
    private final WorkspaceService workspaceService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${spring.profiles.active:local}")
    private String activeProfile = "local";

    public CommandExecutionService(
            CommandParser parser,
            CustomCommandRepository customCommandRepository,
            ScraperService scraperService,
            MangaService mangaService,
            ChapterRepository chapterRepository,
            WorkspaceService workspaceService,
            SimpMessagingTemplate messagingTemplate) {
        this.parser = parser;
        this.customCommandRepository = customCommandRepository;
        this.scraperService = scraperService;
        this.mangaService = mangaService;
        this.chapterRepository = chapterRepository;
        this.workspaceService = workspaceService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Top-level entry point to execute an arbitrary user command line string.
     * Evaluates multi-step pipelines (Tier 2) chained with "&&".
     */
    public String execute(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return "Type 'help' or 'HELP' for available APEX commands.";
        }

        // TIER 2: Multi-step pipeline execution (split by '&&')
        if (rawInput.contains("&&")) {
            return executePipeline(rawInput);
        }

        return executeSingle(rawInput.trim(), 0);
    }

    private String executePipeline(String pipelineInput) {
        String[] steps = pipelineInput.split("&&");
        StringBuilder output = new StringBuilder("=== EXECUTING PIPELINE (" + steps.length + " steps) ===\n");

        for (int i = 0; i < steps.length; i++) {
            String step = steps[i].trim();
            output.append(String.format("[%d/%d] > %s\n", i + 1, steps.length, step));
            String stepResult = executeSingle(step, 0);
            output.append(stepResult).append("\n");

            // Halt pipeline if a step fails
            if (stepResult.startsWith("ERROR")) {
                output.append("Pipeline halted due to error in step: ").append(step);
                break;
            }
        }
        return output.toString();
    }

    private String executeSingle(String rawInput, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return "ERROR: Circular alias expansion limit exceeded (> " + MAX_RECURSION_DEPTH + ")";
        }

        Command cmd = parser.parse(rawInput);
        String action = cmd.getAction().toLowerCase();

        // 1. Check for User-Defined Custom Commands (Tier 1 & Tier 2)
        Optional<CustomCommand> customCmdOpt = customCommandRepository.findByTriggerName(action);
        if (customCmdOpt.isPresent()) {
            CustomCommand customCmd = customCmdOpt.get();
            String expanded = expandTemplate(customCmd.getTemplateString(), rawInput);
            return executeSingle(expanded, depth + 1);
        }

        // 2. Built-in Core Handlers (with APEX Shorthand Aliases)
        try {
            return switch (action) {
                // Ingestion & Harvesting
                case "scrape", "scrp", "scrp.scout", "scout" -> handleScrape(cmd);
                case "harvest", "hrv", "scrp.run", "run" -> handleHarvest(cmd);
                
                // Catalog & Content
                case "list", "ls", "mng", "mng.list" -> handleList(cmd);
                
                // Custom Command Management (Tier 1)
                case "alias", "custom.add" -> handleAlias(cmd, rawInput);
                case "unalias" -> handleUnalias(cmd);
                case "aliases", "custom.list" -> handleListAliases();

                // Telemetry & Utility
                case "status", "sys", "sys.status" -> handleStatus();
                case "chat", "ws.chat" -> handleChat(cmd, rawInput);
                case "help", "man" -> handleHelp();

                default -> "Unknown APEX command: [" + action + "]. Type 'help' for reference.";
            };
        } catch (Exception e) {
            log.error("Error executing command [{}]: {}", action, e.getMessage());
            return "ERROR executing [" + action + "]: " + e.getMessage();
        }
    }

    private String handleScrape(Command cmd) throws Exception {
        String url = cmd.getFlag("url");
        if ((url == null || url.isBlank()) && !cmd.getArgs().isEmpty()) {
            url = cmd.getArgs().get(0);
        }
        if (url == null || url.isBlank()) {
            return "Usage: SCRP.SCOUT --url <series_url> OR SCRP.SCOUT <series_url>";
        }

        streamTerminalLine("Scouting series metadata from: " + url + "...");
        Manga manga = scraperService.scoutAndRegisterSeries(url);
        workspaceService.recordActivity("CLI", "SERIES_ARCHIVED", "Archived " + manga.getTitle(), "MANGA", manga.getId());
        return "✔ Registered [" + manga.getTitle() + "] with " + manga.getTotalChapters() + " chapters (UUID: " + manga.getId() + ")";
    }

    private String handleHarvest(Command cmd) throws Exception {
        String chapterIdStr = cmd.getFlag("chapter");
        String titleStr = cmd.getFlag("title");
        String numberStr = cmd.getFlag("num");

        if (chapterIdStr == null && !cmd.getArgs().isEmpty()) {
            String firstArg = cmd.getArgs().get(0);
            try {
                // If the first argument is a UUID
                UUID.fromString(firstArg);
                chapterIdStr = firstArg;
            } catch (IllegalArgumentException e) {
                // Not a UUID, treat as series title search
                titleStr = firstArg;
                if (cmd.getArgs().size() > 1) {
                    numberStr = cmd.getArgs().get(1);
                }
            }
        }

        if (chapterIdStr != null && !chapterIdStr.isBlank()) {
            UUID chapterId = UUID.fromString(chapterIdStr);
            streamTerminalLine("Initiating parallel harvest for chapter [" + chapterId + "]...");
            Chapter chapter = scraperService.harvestChapter(chapterId);
            return "✔ Successfully harvested Chapter " + chapter.getChapterNumber() + " (" + chapter.getPageCount() + " pages) -> " + chapter.getStoragePath();
        }

        if (titleStr != null && !titleStr.isBlank() && numberStr != null && !numberStr.isBlank()) {
            String targetTitle = titleStr.toLowerCase().trim();
            double targetNum = Double.parseDouble(numberStr);
            List<Manga> all = mangaService.getAllManga();
            Manga match = all.stream()
                    .filter(m -> m.getTitle().toLowerCase().contains(targetTitle))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                return "ERROR: No title matching '" + titleStr + "' found in vault.";
            }
            Optional<Chapter> chOpt = chapterRepository.findByMangaIdAndChapterNumber(match.getId(), targetNum);
            if (chOpt.isEmpty()) {
                return "ERROR: Chapter " + numberStr + " not found for series '" + match.getTitle() + "'.";
            }
            Chapter chapter = chOpt.get();
            streamTerminalLine("Initiating parallel harvest for " + match.getTitle() + " Ch. " + targetNum + " [" + chapter.getId() + "]...");
            Chapter harvested = scraperService.harvestChapter(chapter.getId());
            return "✔ Successfully harvested " + match.getTitle() + " Chapter " + harvested.getChapterNumber() + " (" + harvested.getPageCount() + " pages) -> " + harvested.getStoragePath();
        }

        return "Usage: SCRP.RUN --chapter <chapter_uuid> OR SCRP.RUN '<series_title>' <chapter_num>";
    }

    private String handleList(Command cmd) {
        List<Manga> all = mangaService.getAllManga();
        if (all.isEmpty()) return "Library is empty.";

        StringBuilder sb = new StringBuilder("=== VAULT CATALOG (" + all.size() + " titles) ===\n");
        for (Manga m : all) {
            sb.append(String.format("• [%s] %s | Progress: %d/%d chapters\n",
                    m.getId(), m.getTitle(), m.getDownloadedChapters(), m.getTotalChapters()));
        }
        return sb.toString();
    }

    private String handleAlias(Command cmd, String rawInput) {
        // Syntax: alias <name>="<template>" [--desc "description"]
        // or:     CUSTOM.ADD <name>="<template>" [--desc "description"]
        int eqIndex = rawInput.indexOf('=');
        if (eqIndex == -1) {
            return "Usage: CUSTOM.ADD <name>=\"<template>\" [--desc \"description\"]";
        }

        String cleaned = rawInput.replaceFirst("(?i)^(alias|custom\\.add)\\s+", "");
        int cleanedEqIndex = cleaned.indexOf('=');
        if (cleanedEqIndex == -1) {
            return "Usage: CUSTOM.ADD <name>=\"<template>\" [--desc \"description\"]";
        }

        String namePart = cleaned.substring(0, cleanedEqIndex).trim();
        String rest = cleaned.substring(cleanedEqIndex + 1).trim();

        // Extract template inside quotes
        String template;
        if (rest.startsWith("\"") || rest.startsWith("'")) {
            char quote = rest.charAt(0);
            int closing = rest.indexOf(quote, 1);
            if (closing == -1) return "ERROR: Unclosed quote in alias template";
            template = rest.substring(1, closing);
        } else {
            template = rest.split("\\s+")[0];
        }

        String desc = cmd.getFlag("desc");
        boolean isPipeline = template.contains("&&");

        CustomCommand customCmd = new CustomCommand(namePart, template, desc, isPipeline);
        customCommandRepository.save(customCmd);
        return "✔ Saved alias: [" + namePart + "] -> \"" + template + "\"";
    }

    private String handleUnalias(Command cmd) {
        String name = cmd.getFlag("name");
        if (name == null || name.isBlank()) return "Usage: unalias --name <alias_name>";
        customCommandRepository.deleteByTriggerName(name.toLowerCase().trim());
        return "✔ Removed alias: [" + name + "]";
    }

    private String handleListAliases() {
        List<CustomCommand> all = customCommandRepository.findAll();
        if (all.isEmpty()) return "No custom aliases registered. Create one with: alias <name>=\"<cmd>\"";

        StringBuilder sb = new StringBuilder("=== USER ALIASES & MACROS (" + all.size() + ") ===\n");
        for (CustomCommand c : all) {
            sb.append(String.format("• %-12s -> %s %s\n",
                    c.getTriggerName(), c.getTemplateString(),
                    c.getDescription() != null ? "(" + c.getDescription() + ")" : ""));
        }
        return sb.toString();
    }

    private String handleStatus() {
        List<Manga> mangaList = mangaService.getAllManga();
        int mangaCount = (mangaList != null) ? mangaList.size() : 0;
        String profileName = (activeProfile != null) ? activeProfile.toUpperCase() : "LOCAL";
        return String.format("""
                === THE ARCHIVE APEX STATUS ===
                Active Profile  : %s
                Catalog Titles  : %d Manga
                Harvester Engine: Ready (Java 21 Virtual Threads)
                STOMP Broker    : Online (/ws)
                """, profileName, mangaCount);
    }

    private String handleChat(Command cmd, String rawInput) {
        String text = rawInput.replaceFirst("(?i)^(chat|ws\\.chat)\\s+", "").trim();
        if (text.isBlank()) return "Usage: WS.CHAT <message text>";
        workspaceService.broadcastSystemAlert(text, "general");
        return "✔ Message broadcast to #general";
    }

    private String handleHelp() {
        return """
                === APEX COMMAND REFERENCE ===
                  MNG.LIST / list                             List stored vault titles
                  SYS.STATUS / status                         System telemetry & status
                  SCRP.SCOUT / scrape    <url>                Scout & catalog a series
                  SCRP.RUN / harvest     <chapter_uuid>       Harvest chapter images
                  WS.CHAT / chat         <message>            Broadcast to workspace chat
                  CUSTOM.ADD / alias     <name>="<cmd>"       Create custom alias/pipeline
                  CUSTOM.LIST / aliases                       List registered aliases
                  unalias                --name <name>        Remove custom alias
                  cmd1 && cmd2                                Chained execution pipeline
                """;
    }

    private String expandTemplate(String template, String rawInput) {
        String[] tokens = rawInput.split("\\s+");
        String expanded = template;

        // Replace positional parameters ($1, $2, ...)
        for (int i = 1; i < tokens.length; i++) {
            expanded = expanded.replace("$" + i, tokens[i]);
        }
        return expanded;
    }

    private void streamTerminalLine(String line) {
        messagingTemplate.convertAndSend("/topic/terminal.output", line);
    }
}
