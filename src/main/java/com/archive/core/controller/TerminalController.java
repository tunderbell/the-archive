package com.archive.core.controller;

import com.archive.core.service.CommandExecutionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ============================================================================
 * CLASS: TerminalController
 * ============================================================================
 * WHAT IT DOES:
 * Exposes WebSocket and REST endpoints for the APEX Command Bar and Xterm.js terminal.
 *
 * WHY IT IS USED:
 * Provides two communication channels for executing commands:
 * 1. WebSocket (@MessageMapping) for live, streaming terminal sessions.
 * 2. REST (@PostMapping) for one-off command submissions from the command bar.
 * ============================================================================
 */
@RestController
public class TerminalController {

    private final CommandExecutionService executionService;

    public TerminalController(CommandExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * WebSocket endpoint for interactive browser terminal (Xterm.js).
     * Destination in:  /app/terminal.command
     * Destination out: /topic/terminal.output
     */
    @MessageMapping("/terminal.command")
    @SendTo("/topic/terminal.output")
    public String handleWebSocketCommand(@Payload String commandLine) {
        return executionService.execute(commandLine);
    }

    /**
     * REST endpoint for single-shot command execution via the APEX command bar.
     * Example: POST /api/terminal/execute with JSON: {"command": "status"}
     */
    @PostMapping("/api/terminal/execute")
    public Map<String, String> handleRestCommand(@RequestBody Map<String, String> request) {
        String input = request.getOrDefault("command", "help");
        String output = executionService.execute(input);
        return Map.of("output", output);
    }
}
