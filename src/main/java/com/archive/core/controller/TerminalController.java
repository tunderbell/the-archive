package com.archive.core.controller;

import com.archive.core.service.CommandExecutionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ============================================================================
 * CLASS: TerminalController
 * ============================================================================
 * Exposes WebSocket and REST endpoints for the APEX Command Bar and Terminal.
 * Returns execution metrics (timeMs, success status) and supports both raw
 * and JSON-wrapped command strings.
 * ============================================================================
 */
@RestController
public class TerminalController {

    private final CommandExecutionService executionService;
    private final SimpMessagingTemplate messagingTemplate;

    public TerminalController(CommandExecutionService executionService, SimpMessagingTemplate messagingTemplate) {
        this.executionService = executionService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * WebSocket endpoint for interactive browser terminal (Xterm.js).
     * Destination in:  /app/terminal.command
     * Destination out: /topic/terminal.output
     */
    @MessageMapping("/terminal.command")
    @SendTo("/topic/terminal.output")
    public String handleWebSocketCommand(@Payload String rawPayload) {
        String commandLine = extractCommand(rawPayload);
        return executionService.execute(commandLine);
    }

    /**
     * REST endpoint for single-shot command execution via the APEX command bar.
     * Example: POST /api/terminal/execute with JSON: {"command": "MNG.LIST", "source": "BAR"}
     */
    @PostMapping("/api/terminal/execute")
    public Map<String, Object> handleRestCommand(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();
        String input = String.valueOf(request.getOrDefault("command", "help"));
        String source = String.valueOf(request.getOrDefault("source", "BAR"));
        String output = executionService.execute(input);
        long elapsedMs = System.currentTimeMillis() - startTime;

        boolean success = !output.startsWith("ERROR") &&
                          !output.startsWith("[ERROR]") &&
                          !output.startsWith("Unknown APEX command");

        // Cockpit Echo: If triggered from the APEX Command Bar, broadcast to open terminal tiles
        if ("BAR".equalsIgnoreCase(source)) {
            Map<String, Object> echoPayload = Map.of(
                "source", "BAR",
                "command", input,
                "output", output
            );
            messagingTemplate.convertAndSend("/topic/terminal.output", (Object) echoPayload);
        }

        return Map.of(
            "command", input,
            "output", output,
            "executionTimeMs", elapsedMs,
            "success", success
        );
    }

    private String extractCommand(String rawPayload) {
        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            return "help";
        }
        String trimmed = rawPayload.trim();
        if (trimmed.startsWith("{") && trimmed.contains("\"command\"")) {
            int keyIdx = trimmed.indexOf("\"command\"");
            int colonIdx = trimmed.indexOf(':', keyIdx);
            if (colonIdx != -1) {
                int firstQuote = trimmed.indexOf('"', colonIdx + 1);
                if (firstQuote != -1) {
                    int secondQuote = trimmed.indexOf('"', firstQuote + 1);
                    if (secondQuote != -1) {
                        return trimmed.substring(firstQuote + 1, secondQuote).trim();
                    }
                }
            }
        }
        return trimmed;
    }
}
